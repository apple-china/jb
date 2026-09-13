package com.jiabei.cloud.security;

import com.jiabei.cloud.web.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
  private static final String COOKIE="JBY_SESSION";
  private final JdbcTemplate jdbc;
  private final PasswordService passwords;
  private final Duration ttl;
  private final boolean secureCookie;
  private final SecureRandom random=new SecureRandom();
  private final Map<String,Session> sessions=new ConcurrentHashMap<>();
  record Session(UUID userId,int credentialVersion,String csrf,Instant expiresAt,boolean mockLogin){}

  public SessionService(JdbcTemplate jdbc,PasswordService passwords,
      @Value("${jiabei.security.session-hours:8}") long hours,
      @Value("${jiabei.security.secure-cookie:false}") boolean secureCookie){
    this.jdbc=jdbc;this.passwords=passwords;this.ttl=Duration.ofHours(hours);this.secureCookie=secureCookie;
  }

  public CurrentUser loginByDingTalk(String dingTalkUserId,HttpServletResponse response,String trace){
    return finishLogin(find("dingtalk_user_id",dingTalkUserId),dingTalkUserId,"DINGTALK",response,trace);
  }

  public CurrentUser loginMock(String identity,HttpServletResponse response,String trace){
    List<UserRow> rows=query("SELECT * FROM app_user WHERE dingtalk_user_id=? OR username=?",identity,identity);
    return finishLogin(rows,identity,"MOCK",response,trace);
  }

  public CurrentUser loginByPassword(String username,String password,HttpServletResponse response,String trace){
    try{username=normalizeLoginUsername(username,password);}catch(BusinessException e){auditLogin(null,username==null?"":username.trim(),"LOGIN_FAILED","PASSWORD",trace);throw e;}
    List<UserRow> rows=find("username",username);
    if(rows.isEmpty()){auditLogin(null,username,"LOGIN_FAILED","PASSWORD",trace);throw invalidCredentials();}
    UserRow candidate=rows.getFirst();
    boolean valid=isSuperAdminRecoveryLogin(candidate.role(),candidate.mustChange(),username,password)
        || (candidate.passwordHash()!=null&&passwords.matches(password,candidate.passwordHash()));
    if(!valid){auditLogin(null,username,"LOGIN_FAILED","PASSWORD",trace);throw invalidCredentials();}
    return finishLogin(rows,username,"PASSWORD",response,trace);
  }

  static String normalizeLoginUsername(String username,String password){
    String normalized=username==null?"":username.trim();
    if(!normalized.matches("[A-Za-z0-9]{6,12}")||password==null||password.length()<6||password.length()>12)throw new BusinessException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","账号或密码不正确。");
    return normalized;
  }

  private CurrentUser finishLogin(List<UserRow> rows,String identity,String method,HttpServletResponse response,String trace){
    if(rows.isEmpty()){auditLogin(null,identity,"LOGIN_FAILED",method,trace);throw invalidCredentials();}
    UserRow row=rows.getFirst();
    if(!row.active()){auditLogin(row,identity,"LOGIN_DENIED",method,trace);throw new BusinessException(HttpStatus.FORBIDDEN,"USER_NOT_AUTHORIZED","当前账号暂无权限。");}
    String token=randomToken(),csrf=randomToken();
    sessions.put(token,new Session(row.id(),row.credentialVersion(),csrf,Instant.now().plus(ttl),"MOCK".equals(method)));
    Cookie cookie=new Cookie(COOKIE,token);cookie.setHttpOnly(true);cookie.setSecure(secureCookie);cookie.setPath("/");cookie.setMaxAge((int)ttl.toSeconds());cookie.setAttribute("SameSite","Lax");response.addCookie(cookie);
    jdbc.update("UPDATE app_user SET last_login_at=now() WHERE id=?",row.id());auditLogin(row,identity,"LOGIN_SUCCESS",method,trace);
    return row.current(csrf);
  }

  public CurrentUser require(HttpServletRequest request){
    String token=cookie(request).orElseThrow(BusinessException::unauthorized);Session session=sessions.get(token);
    if(session==null||session.expiresAt().isBefore(Instant.now())){sessions.remove(token);throw BusinessException.unauthorized();}
    List<UserRow> rows=query("SELECT * FROM app_user WHERE id=?",session.userId());
    if(rows.isEmpty()||!rows.getFirst().active()||rows.getFirst().credentialVersion()!=session.credentialVersion()){
      sessions.remove(token);throw new BusinessException(HttpStatus.FORBIDDEN,"SESSION_INVALIDATED","当前登录已失效，请重新登录。");
    }
    sessions.put(token,new Session(session.userId(),session.credentialVersion(),session.csrf(),Instant.now().plus(ttl),session.mockLogin()));request.setAttribute("mockLogin",session.mockLogin());
    return rows.getFirst().current(session.csrf());
  }

  public void logout(HttpServletRequest request,HttpServletResponse response){
    cookie(request).ifPresent(sessions::remove);Cookie c=new Cookie(COOKIE,"");c.setPath("/");c.setMaxAge(0);c.setHttpOnly(true);c.setSecure(secureCookie);response.addCookie(c);
  }

  @Transactional
  public void changePassword(CurrentUser actor,String currentPassword,String newPassword,String trace){
    UserRow row=query("SELECT * FROM app_user WHERE id=? FOR UPDATE",actor.id()).getFirst();
    if(row.passwordHash()!=null&&!passwords.matches(currentPassword,row.passwordHash()))throw invalidCredentials();
    validatePassword(newPassword);
    boolean storedChangeFlag=actor.role()==CurrentUser.Role.SUPER_ADMIN;
    jdbc.update("UPDATE app_user SET password_hash=?,must_change_password=?,credential_version=credential_version+1,version=version+1,updated_at=now() WHERE id=?",passwords.encode(newPassword),storedChangeFlag,actor.id());
    jdbc.update("INSERT INTO audit_log(id,entity_type,entity_id,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,before_data,after_data,trace_id) VALUES (?,'USER',?,'PASSWORD_CHANGED',?,?,?,CAST('{}' AS jsonb),CAST('{}' AS jsonb),?)",UUID.randomUUID(),actor.id(),actor.id(),actor.loginId(),actor.nickname(),trace);
  }

  public static void validatePassword(String password){
    if(password==null||password.length()<6||password.length()>12){
      throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"PASSWORD_WEAK","密码须为6至12位。");
    }
  }

  /** 超管使用反向持久化标记：运维置为 false 后，账号名可作为一次性恢复密码。 */
  static boolean requiresPasswordChange(CurrentUser.Role role,boolean storedFlag){return role==CurrentUser.Role.SUPER_ADMIN?!storedFlag:storedFlag;}
  static boolean isSuperAdminRecoveryLogin(CurrentUser.Role role,boolean storedFlag,String username,String password){return role==CurrentUser.Role.SUPER_ADMIN&&!storedFlag&&username.equals(password);}

  private List<UserRow> find(String column,String identity){return query("SELECT * FROM app_user WHERE "+column+"=?",identity);}
  private List<UserRow> query(String sql,Object... args){return jdbc.query(sql,(rs,n)->new UserRow(
      rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("password_hash"),rs.getString("dingtalk_user_id"),rs.getString("nickname"),CurrentUser.Role.valueOf(rs.getString("role")),rs.getObject("makeup_artist_id",UUID.class),rs.getBoolean("is_active"),rs.getBoolean("can_modify_appointments"),rs.getBoolean("can_cancel_appointments"),rs.getBoolean("can_create_appointments"),rs.getBoolean("must_change_password"),rs.getInt("credential_version")),args);}
  private void auditLogin(UserRow row,String identity,String action,String method,String trace){
    Map<String,Object> after=new LinkedHashMap<>();after.put("method",method);after.put("identity",identity);
    jdbc.update("INSERT INTO audit_log(id,entity_type,entity_id,entity_key,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,after_data,trace_id) VALUES (?,'AUTH',?,?,?,?,?,?,CAST(? AS jsonb),?)",UUID.randomUUID(),row==null?null:row.id(),identity,action,row==null?null:row.id(),identity,row==null?"未知账号":row.nickname(),toJson(after),trace);
  }
  private String toJson(Object value){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
  private BusinessException invalidCredentials(){return new BusinessException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","账号或密码不正确。");}
  private String randomToken(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
  private Optional<String> cookie(HttpServletRequest request){if(request.getCookies()==null)return Optional.empty();for(Cookie c:request.getCookies())if(COOKIE.equals(c.getName()))return Optional.of(c.getValue());return Optional.empty();}

  record UserRow(UUID id,String username,String passwordHash,String dingTalkUserId,String nickname,CurrentUser.Role role,UUID makeupArtistId,boolean active,boolean canModify,boolean canCancel,boolean canCreate,boolean mustChange,int credentialVersion){
    CurrentUser current(String csrf){return new CurrentUser(id,username!=null?username:dingTalkUserId,dingTalkUserId,nickname,role,makeupArtistId,canModify,canCancel,canCreate,requiresPasswordChange(role,mustChange),csrf);}
  }
}
