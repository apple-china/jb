package com.jiabei.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.integration.DingTalkDirectoryGateway;
import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.security.PasswordService;
import com.jiabei.cloud.web.BusinessException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private static final SecureRandom RANDOM=new SecureRandom();
  private static final DateTimeFormatter ACCOUNT_DATE=DateTimeFormatter.ofPattern("yyMMdd");
  private final JdbcTemplate jdbc;
  private final PasswordService passwords;
  private final ObjectMapper json;
  private final DingTalkDirectoryGateway directory;

  public AccountService(JdbcTemplate jdbc,PasswordService passwords,ObjectMapper json,DingTalkDirectoryGateway directory){
    this.jdbc=jdbc;this.passwords=passwords;this.json=json;this.directory=directory;
  }

  public List<Map<String,Object>> list(CurrentUser actor){
    if(!actor.isAdministrator()&&actor.role()!=CurrentUser.Role.OBSERVER)throw BusinessException.forbidden();
    return jdbc.query("""
        SELECT id,username,dingtalk_user_id,dingtalk_username,nickname,role,makeup_artist_id,
               is_active,is_attending,can_modify_appointments,can_cancel_appointments,
               can_create_appointments,must_change_password,version,updated_at,last_login_at
        FROM app_user
        ORDER BY is_active DESC,
          CASE role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'OPERATOR' THEN 2 WHEN 'OBSERVER' THEN 3
                    WHEN 'MAKEUP' THEN 4 WHEN 'STREAMER' THEN 5 ELSE 6 END,
          updated_at DESC,id
        """,(rs,n)->{
      Map<String,Object> m=new LinkedHashMap<>();
      m.put("id",rs.getObject(1,UUID.class));m.put("username",rs.getString(2));
      m.put("dingTalkUserId",rs.getString(3));m.put("dingTalkUsername",rs.getString(4));
      m.put("nickname",rs.getString(5));m.put("role",rs.getString(6));
      m.put("makeupArtistId",rs.getObject(7,UUID.class));m.put("active",rs.getBoolean(8));
      m.put("attending",rs.getBoolean(9));m.put("canModifyAppointments",rs.getBoolean(10));
      m.put("canCancelAppointments",rs.getBoolean(11));m.put("canCreateAppointments",rs.getBoolean(12));m.put("mustChangePassword",rs.getBoolean(13));
      m.put("version",rs.getInt(14));m.put("updatedAt",rs.getObject(15));m.put("lastLoginAt",rs.getObject(16));return m;
    });
  }

  @Transactional public Map<String,Object> create(CurrentUser actor,Create p,String trace){
    CurrentUser.Role role=parseRole(p.role());if(role==CurrentUser.Role.SUPER_ADMIN)throw BusinessException.forbidden();
    requireMayManage(actor,role);String ding=require(p.dingTalkUserId(),"钉钉不能为空。");
    String dingName=require(p.dingTalkUsername(),"钉钉用户无效。");String nickname=require(p.nickname(),"昵称不能为空。");
    ensureNicknameAvailable(nickname,null);
    boolean directoryMatch=directory.employees(ding).stream().anyMatch(employee->employee.userId().equals(ding)&&employee.username().equals(dingName));
    if(!directoryMatch)throw invalid("钉钉用户必须从员工列表中选择。");
    UUID id=UUID.randomUUID(),makeupArtistId=null;
    try{
      if(role==CurrentUser.Role.MAKEUP){
        makeupArtistId=UUID.randomUUID();
        jdbc.update("INSERT INTO makeup_artist(id,name,work_days,work_start,work_end,is_attending,is_active) VALUES (?,?, '1,2,3,4,5,6,7','08:00','20:00',?,?)",makeupArtistId,nickname,p.attending(),p.active()==null||p.active());
      }
      boolean active=p.active()==null||p.active();
      boolean mayConfigureOperator=actor.role()==CurrentUser.Role.SUPER_ADMIN&&role==CurrentUser.Role.OPERATOR;
      boolean modify=mayConfigureOperator&&Boolean.TRUE.equals(p.canModifyAppointments());
      boolean cancel=mayConfigureOperator&&Boolean.TRUE.equals(p.canCancelAppointments());
      boolean proxy=(role==CurrentUser.Role.MAKEUP||role==CurrentUser.Role.OPERATOR)&&(p.canCreateAppointments()==null||p.canCreateAppointments());
      jdbc.update("""
          INSERT INTO app_user(id,dingtalk_user_id,dingtalk_username,nickname,role,makeup_artist_id,
                               is_active,is_attending,can_modify_appointments,can_cancel_appointments,can_create_appointments)
          VALUES (?,?,?,?,?,?,?,?,?,?,?)
          """,id,ding,dingName,nickname,role.name(),makeupArtistId,active,p.attending(),modify,cancel,proxy);
    }catch(DataIntegrityViolationException e){throw new BusinessException(HttpStatus.CONFLICT,"ACCOUNT_CONFLICT","该钉钉员工或化妆师昵称已存在。");}
    audit(id,"CREATE",actor,Map.of("nickname",nickname,"dingTalkUsername",dingName,"role",role.name()),trace);
    return byId(actor,id);
  }

  @Transactional public Map<String,Object> update(CurrentUser actor,UUID id,Patch p,String trace){
    Map<String,Object> before=lock(id);CurrentUser.Role oldRole=parseRole((String)before.get("role"));
    if(oldRole==CurrentUser.Role.SUPER_ADMIN||actor.id().equals(id))throw BusinessException.forbidden();
    CurrentUser.Role role=p.role()==null?oldRole:parseRole(p.role());
    requireMayManage(actor,oldRole);requireMayManage(actor,role);
    if((int)before.get("version")!=p.version())throw versionConflict();
    String nickname=p.nickname()==null?(String)before.get("nickname"):require(p.nickname(),"昵称不能为空。");
    ensureNicknameAvailable(nickname,id);
    boolean active=p.active()==null?(boolean)before.get("active"):p.active();
    boolean attending=p.attending()==null?(boolean)before.get("attending"):p.attending();
    UUID makeupArtistId=(UUID)before.get("makeupArtistId");
    boolean proxyRole=role==CurrentUser.Role.MAKEUP||role==CurrentUser.Role.OPERATOR;
    boolean oldProxyRole=oldRole==CurrentUser.Role.MAKEUP||oldRole==CurrentUser.Role.OPERATOR;
    boolean create=proxyRole?(p.canCreateAppointments()!=null?p.canCreateAppointments():(oldProxyRole?(boolean)before.get("canCreateAppointments"):true)):false;
    try{
      if(role==CurrentUser.Role.MAKEUP&&makeupArtistId==null){
        makeupArtistId=UUID.randomUUID();
        jdbc.update("INSERT INTO makeup_artist(id,name,work_days,work_start,work_end,is_attending,is_active) VALUES (?,?, '1,2,3,4,5,6,7','08:00','20:00',true,?)",makeupArtistId,nickname,active);
      }else if(role==CurrentUser.Role.MAKEUP&&makeupArtistId!=null){
        jdbc.update("UPDATE makeup_artist SET name=?,is_active=?,updated_at=now(),version=version+1 WHERE id=?",nickname,active,makeupArtistId);
      }else if(oldRole==CurrentUser.Role.MAKEUP&&makeupArtistId!=null){
        jdbc.update("UPDATE makeup_artist SET is_active=false,updated_at=now(),version=version+1 WHERE id=?",makeupArtistId);
      }
      boolean mayGrant=actor.role()==CurrentUser.Role.SUPER_ADMIN&&role==CurrentUser.Role.OPERATOR;
      boolean modify=mayGrant&&p.canModifyAppointments()!=null?p.canModifyAppointments():(role==CurrentUser.Role.OPERATOR&&(boolean)before.get("canModifyAppointments"));
      boolean cancel=mayGrant&&p.canCancelAppointments()!=null?p.canCancelAppointments():(role==CurrentUser.Role.OPERATOR&&(boolean)before.get("canCancelAppointments"));
      jdbc.update("""
          UPDATE app_user SET nickname=?,role=?,makeup_artist_id=?,is_active=?,is_attending=?,
            can_modify_appointments=?,can_cancel_appointments=?,can_create_appointments=?,
            credential_version=credential_version+CASE WHEN is_active<>? OR role<>? THEN 1 ELSE 0 END,
            version=version+1,updated_at=now() WHERE id=?
          """,nickname,role.name(),makeupArtistId,active,attending,modify,cancel,create,active,role.name(),id);
    }catch(DataIntegrityViolationException e){throw new BusinessException(HttpStatus.CONFLICT,"ACCOUNT_CONFLICT","角色、昵称或化妆师关联冲突。");}
    audit(id,"UPDATE",actor,Map.of("nickname",nickname,"role",role.name(),"active",active,"attending",attending,"canCreateAppointments",create),trace);
    return byId(actor,id);
  }

  @Transactional public Map<String,String> assignPassword(CurrentUser actor,UUID id,String trace){
    Map<String,Object> target=lock(id);CurrentUser.Role role=parseRole((String)target.get("role"));
    if(role==CurrentUser.Role.SUPER_ADMIN||actor.id().equals(id))throw BusinessException.forbidden();requireMayManage(actor,role);
    String username=null;
    for(int attempt=0;attempt<20;attempt++){
      String candidate="JB"+LocalDate.now(ZoneId.of("Asia/Shanghai")).format(ACCOUNT_DATE)+String.format("%04d",RANDOM.nextInt(10000));
      Integer count=jdbc.queryForObject("SELECT count(*) FROM app_user WHERE username=?",Integer.class,candidate);
      if(count!=null&&count==0){username=candidate;break;}
    }
    if(username==null)throw new BusinessException(HttpStatus.CONFLICT,"ACCOUNT_NAME_GENERATION_FAILED","账号生成冲突，请重试。");
    jdbc.update("UPDATE app_user SET username=?,password_hash=?,must_change_password=true,credential_version=credential_version+1,version=version+1,updated_at=now() WHERE id=?",username,passwords.encode("123456"),id);
    audit(id,"PASSWORD_ASSIGNED",actor,Map.of("username",username),trace);
    return Map.of("username",username,"password","123456");
  }

  @Transactional public void revokePassword(CurrentUser actor,UUID id,String trace){
    Map<String,Object> target=lock(id);CurrentUser.Role role=parseRole((String)target.get("role"));
    if(role==CurrentUser.Role.SUPER_ADMIN||actor.id().equals(id))throw BusinessException.forbidden();requireMayManage(actor,role);
    jdbc.update("UPDATE app_user SET username=NULL,password_hash=NULL,must_change_password=false,credential_version=credential_version+1,version=version+1,updated_at=now() WHERE id=?",id);
    audit(id,"PASSWORD_REVOKED",actor,Map.of(),trace);
  }

  private Map<String,Object> byId(CurrentUser actor,UUID id){return list(actor).stream().filter(item->id.equals(item.get("id"))).findFirst().orElseThrow();}
  private Map<String,Object> lock(UUID id){
    List<Map<String,Object>> rows=jdbc.query("""
        SELECT id,username,dingtalk_user_id,dingtalk_username,nickname,role,makeup_artist_id,
               is_active,is_attending,can_modify_appointments,can_cancel_appointments,can_create_appointments,version
        FROM app_user WHERE id=? FOR UPDATE
        """,(rs,n)->{
      Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getObject(1,UUID.class));
      m.put("username",rs.getString(2));m.put("dingTalkUserId",rs.getString(3));
      m.put("dingTalkUsername",rs.getString(4));m.put("nickname",rs.getString(5));
      m.put("role",rs.getString(6));m.put("makeupArtistId",rs.getObject(7,UUID.class));
      m.put("active",rs.getBoolean(8));m.put("attending",rs.getBoolean(9));
      m.put("canModifyAppointments",rs.getBoolean(10));m.put("canCancelAppointments",rs.getBoolean(11));
      m.put("canCreateAppointments",rs.getBoolean(12));m.put("version",rs.getInt(13));return m;
    },id);
    if(rows.isEmpty())throw new BusinessException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","账号不存在。");return rows.getFirst();
  }
  private void requireMayManage(CurrentUser actor,CurrentUser.Role target){if(actor.role()==CurrentUser.Role.SUPER_ADMIN)return;if(actor.role()!=CurrentUser.Role.OPERATOR||target==CurrentUser.Role.OPERATOR||target==CurrentUser.Role.SUPER_ADMIN)throw BusinessException.forbidden();}
  /**
   * 昵称校验使用事务级咨询锁串行化相同规范化昵称，避免两个并发请求同时通过“未存在”检查。
   * 比较忽略首尾空格和大小写，适用于所有账号角色。
   */
  private void ensureNicknameAvailable(String nickname,UUID excludedId){
    String normalized=nickname.trim().toLowerCase(java.util.Locale.ROOT);
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"account-nickname|"+normalized);
    Integer count=jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(btrim(nickname))=? AND (?::uuid IS NULL OR id<>?::uuid)",Integer.class,normalized,excludedId==null?null:excludedId.toString(),excludedId==null?null:excludedId.toString());
    if(count!=null&&count>0)throw new BusinessException(HttpStatus.CONFLICT,"NICKNAME_EXISTS","昵称已存在，请使用其他昵称。");
  }
  private CurrentUser.Role parseRole(String role){try{return CurrentUser.Role.valueOf(role);}catch(Exception e){throw invalid("角色无效。");}}
  private void audit(UUID id,String action,CurrentUser actor,Object after,String trace){try{jdbc.update("INSERT INTO audit_log(id,entity_type,entity_id,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,after_data,trace_id) VALUES (?,'USER',?,?,?,?,?,CAST(? AS jsonb),?)",UUID.randomUUID(),id,action,actor.id(),actor.loginId(),actor.nickname(),json.writeValueAsString(after),trace);}catch(Exception e){throw new IllegalStateException(e);}}
  private String require(String value,String message){if(value==null||value.isBlank())throw invalid(message);return value.trim();}
  private BusinessException invalid(String message){return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"ACCOUNT_DATA_INVALID",message);}
  private BusinessException versionConflict(){return new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","数据已变化，请刷新后重试。");}
  public record Create(String dingTalkUserId,String dingTalkUsername,String nickname,String role,Boolean active,boolean attending,Boolean canModifyAppointments,Boolean canCancelAppointments,Boolean canCreateAppointments){}
  public record Patch(String nickname,String role,Boolean active,Boolean attending,Boolean canModifyAppointments,Boolean canCancelAppointments,Boolean canCreateAppointments,int version){}
}
