package com.jiabei.cloud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.domain.BookingPolicy;
import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.web.BusinessException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 预约领域服务。
 *
 * <p>这里集中处理日期与排班校验、并发占位、幂等请求、审计记录和卡片刷新。
 * 控制器只负责协议转换，业务约束必须保留在本服务中，避免通过直接调用 API 绕过。</p>
 */
public class BookingService {
  private final JdbcTemplate jdbc;private final BookingPolicy policy;private final BookingProperties props;private final Clock clock;private final ObjectMapper json;private final AttendanceService attendance;private final CardRefreshService cards;
  @Autowired public BookingService(JdbcTemplate jdbc,BookingPolicy policy,BookingProperties props,Clock clock,ObjectMapper json,AttendanceService attendance,CardRefreshService cards){this.jdbc=jdbc;this.policy=policy;this.props=props;this.clock=clock;this.json=json;this.attendance=attendance;this.cards=cards;}
  BookingService(JdbcTemplate jdbc,BookingPolicy policy,BookingProperties props,Clock clock,ObjectMapper json,AttendanceService attendance){this(jdbc,policy,props,clock,json,attendance,new CardRefreshService(jdbc,props,clock));}

  /** 聚合预约页首屏数据；未指定日期时优先推荐今天，没有可用时段才推荐明天。 */
  public Map<String,Object> context(CurrentUser actor,LocalDate requested){
    LocalDate today=LocalDate.now(clock);LocalDate recommended=hasAnyAvailability(today,actor)?today:today.plusDays(1);LocalDate date=requested==null?recommended:requested;policy.validateDate(date,today);
    Map<String,Object> result=new LinkedHashMap<>();result.put("selectedDate",date.toString());result.put("recommendedDate",recommended.toString());result.put("writeEnabled",systemEnabled());
    result.put("myAppointment",actor.isStreamer()?findActive(actor.id(),date):null);result.put("cancelledAppointments",actor.isStreamer()?findCancelled(actor.id(),date):List.of());
    result.put("dailySchedule",schedule(date,actor));
    List<Map<String,Object>> makeupArtists=jdbc.query("SELECT id,name,avatar_url FROM makeup_artist WHERE is_active AND is_attending ORDER BY name",(rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getObject(1,UUID.class));m.put("name",rs.getString(2));m.put("avatarUrl",Objects.toString(rs.getString(3),""));return m;});
    if(actor.role()==CurrentUser.Role.MAKEUP)makeupArtists.removeIf(t->!actor.makeupArtistId().equals(t.get("id")));else makeupArtists.removeIf(t->!hasMakeupArtistAvailability((UUID)t.get("id"),date,actor));
    result.put("makeupArtists",makeupArtists);result.put("teams",jdbc.query("SELECT id,name,logo_url FROM team WHERE is_active ORDER BY name",(rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getObject(1,UUID.class));m.put("name",rs.getString(2));m.put("logoUrl",Objects.toString(rs.getString(3),""));return m;}));
    result.put("defaults",actor.isStreamer()?defaults(actor.id(),date):Map.of());result.put("rules",Map.of("stepMinutes",props.timeStepMinutes(),"durationMinutes",props.serviceDurationMinutes(),"leadMinutes",actor.isStreamer()?props.streamerLeadMinutes():props.adminLeadMinutes(),"cancelLimit",2,"modifyLimit",3));result.put("operationCounts",actor.isStreamer()?counts(actor.id(),date):Map.of());return result;
  }

  /**
   * 生成化妆师可用时段。
   *
   * <p>排班关闭表示忽略工作日和上下班边界，因此按 10 分钟步长生成全天 144 个开始时刻；
   * 资源停用或休息仍由领域策略判定为不可预约。管理员和化妆师可以看到冲突时段，
   * 但只有具备覆盖权限的角色可以提交冲突预约。</p>
   */
  public Map<String,Object> availability(CurrentUser actor,LocalDate date,UUID makeupArtistId){
    return availability(actor,date,makeupArtistId,null);
  }

  public Map<String,Object> streamerAvailability(CurrentUser actor,LocalDate date,UUID makeupArtistId,UUID appointmentId){
    requireStreamer(actor);
    if(appointmentId==null)return availability(actor,date,makeupArtistId,null);
    Appointment appointment=appointment(appointmentId);
    if(!appointment.streamerId().equals(actor.id()))throw BusinessException.forbidden();
    if(!appointment.date().equals(date))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"BOOKING_DATE_IMMUTABLE","预约日期不可修改。");
    return availability(actor,date,makeupArtistId,appointmentId);
  }

  private Map<String,Object> availability(CurrentUser actor,LocalDate date,UUID makeupArtistId,UUID excludeAppointmentId){
    policy.validateDate(date,LocalDate.now(clock));MakeupArtist makeupArtist=makeupArtist(makeupArtistId);List<Map<String,Object>> slots=new ArrayList<>();boolean conflictAllowed=actor.isAdministrator()||actor.role()==CurrentUser.Role.MAKEUP;
    LocalTime first=makeupArtist.scheduleEnabled()?makeupArtist.workStart():LocalTime.MIDNIGHT;
    int slotCount=makeupArtist.scheduleEnabled()?(int)(java.time.Duration.between(first,makeupArtist.workEnd()).toMinutes()-props.serviceDurationMinutes())/props.timeStepMinutes()+1:24*60/props.timeStepMinutes();
    for(int index=0;index<slotCount;index++){LocalTime t=first.plusMinutes((long)index*props.timeStepMinutes());
      ZonedDateTime start=policy.start(date,t);String reason=null;boolean conflict=false;
      try{policy.validateMakeupArtist(date,t,makeupArtist.workDays(),makeupArtist.workStart(),makeupArtist.workEnd(),makeupArtist.scheduleEnabled(),makeupArtist.active(),makeupArtist.attending());if(actor.isStreamer())policy.validateStreamerLead(ZonedDateTime.now(clock),start);else policy.validateAdminLead(ZonedDateTime.now(clock),start);}catch(BusinessException e){reason=e.getMessage();}
      if(reason==null){conflict=overlap(makeupArtistId,start,start.plusMinutes(props.serviceDurationMinutes()),excludeAppointmentId);if(conflict&&!conflictAllowed)reason="该时段已占用";}
      Map<String,Object> slot=new LinkedHashMap<>();slot.put("time",t.toString());slot.put("available",reason==null);slot.put("conflict",conflict);slot.put("reason",reason);slots.add(slot);
    }
    return Map.of("slots",slots,"conflictAllowed",conflictAllowed);
  }

  public Map<String,Object> adminAvailability(CurrentUser actor,LocalDate date,UUID makeupArtistId,UUID appointmentId){
    requireProxyActor(actor);
    if(appointmentId!=null){Map<String,Object> appointment=jdbc.queryForMap("SELECT booking_date,makeup_artist_id FROM appointment WHERE id=?",appointmentId);if(actor.role()==CurrentUser.Role.MAKEUP&&!actor.makeupArtistId().equals(appointment.get("makeup_artist_id")))throw BusinessException.forbidden();if(!date.equals(appointment.get("booking_date")))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"BOOKING_DATE_IMMUTABLE","预约日期不可修改。");}
    return availability(actor,date,makeupArtistId,appointmentId);
  }

  public Map<String,Object> adminBookingOptions(CurrentUser actor,LocalDate date,UUID appointmentId){
    requireProxyActor(actor);policy.validateDate(date,LocalDate.now(clock));
    List<Map<String,Object>> streamers=jdbc.query("""
      SELECT coalesce(u.dingtalk_user_id,u.username),u.nickname FROM app_user u
      WHERE u.role='STREAMER' AND u.is_active AND u.is_attending
        AND NOT EXISTS (SELECT 1 FROM appointment a WHERE a.streamer_user_id=u.id AND a.booking_date=? AND a.status='ACTIVE')
      ORDER BY u.nickname
      """,(rs,n)->Map.of("userId",rs.getString(1),"nickname",rs.getString(2)),date);
    List<Map<String,Object>> artists=jdbc.query("SELECT id,name,avatar_url,work_days,work_start,work_end,schedule_enabled FROM makeup_artist WHERE is_active AND is_attending ORDER BY name",(rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getObject(1,UUID.class));m.put("name",rs.getString(2));m.put("avatarUrl",Objects.toString(rs.getString(3),""));m.put("workDays",rs.getString(4));m.put("workStart",rs.getObject(5,LocalTime.class));m.put("workEnd",rs.getObject(6,LocalTime.class));m.put("scheduleEnabled",rs.getBoolean(7));return m;});
    artists.removeIf(item->actor.role()==CurrentUser.Role.MAKEUP&&!actor.makeupArtistId().equals(item.get("id"))||((List<?>)availability(actor,date,(UUID)item.get("id"),appointmentId).get("slots")).stream().noneMatch(slot->Boolean.TRUE.equals(((Map<?,?>)slot).get("available"))));
    List<Map<String,Object>> teams=jdbc.query("SELECT id,name,logo_url FROM team WHERE is_active ORDER BY name",(rs,n)->Map.of("id",rs.getObject(1,UUID.class),"name",rs.getString(2),"logoUrl",Objects.toString(rs.getString(3),"")));
    return Map.of("streamers",streamers,"makeupArtists",artists,"teams",teams);
  }

  private void requireProxyActor(CurrentUser actor){
    if(actor.role()==CurrentUser.Role.OBSERVER||actor.isStreamer())throw BusinessException.forbidden();
    if((actor.role()==CurrentUser.Role.MAKEUP||actor.role()==CurrentUser.Role.OPERATOR)&&!actor.mayCreateAppointments()&&!actor.mayModifyAppointments())throw BusinessException.forbidden();
  }

  @Transactional
  /**
   * 创建预约的完整事务边界：先校验角色与资源，再登记幂等请求并获取业务锁，
   * 最后写预约、审计和卡片刷新任务。顺序不可随意调整，否则并发请求可能重复占位。
   */
  public Map<String,Object> create(CurrentUser actor,CreateCommand c,String key,String trace){
    if(actor.role()==CurrentUser.Role.OBSERVER)throw BusinessException.forbidden();
    if((actor.role()==CurrentUser.Role.MAKEUP||actor.role()==CurrentUser.Role.OPERATOR)&&!actor.mayCreateAppointments())throw new BusinessException(HttpStatus.FORBIDDEN,"PROXY_BOOKING_FORBIDDEN","当前账号未开启代预约权限。");
    boolean streamerMode=actor.isStreamer(),overrideAllowed=actor.isAdministrator()||actor.role()==CurrentUser.Role.MAKEUP;
    if(!actor.isAdministrator()&&!systemEnabled())throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"SYSTEM_DISABLED","预约暂未开放。");
    LocalDate today=LocalDate.now(clock);policy.validateDate(c.bookingDate(),today);policy.validateStep(c.startTime());ZonedDateTime start=policy.start(c.bookingDate(),c.startTime());if(streamerMode)policy.validateStreamerLead(ZonedDateTime.now(clock),start);else policy.validateAdminLead(ZonedDateTime.now(clock),start);
    UUID streamerId=streamerMode?actor.id():streamerId(c.streamerUserId());User streamer=user(streamerId);if(!streamer.attending())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"STREAMER_NOT_ATTENDING","主播当前未出勤，不能提交预约。");
    UUID makeupArtistId=actor.role()==CurrentUser.Role.MAKEUP?actor.makeupArtistId():c.makeupArtistId();if(makeupArtistId==null)throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"MAKEUP_ARTIST_REQUIRED","请选择化妆师。");MakeupArtist makeupArtist=makeupArtist(makeupArtistId);Team team=team(c.teamId());validateResources(c.bookingDate(),c.startTime(),makeupArtist,team);
    String operation=streamerMode?"CREATE_APPOINTMENT":actor.role().name()+"_CREATE_APPOINTMENT";Map<String,Object> request=Map.of("bookingDate",c.bookingDate(),"startTime",c.startTime(),"makeupArtistId",makeupArtistId,"teamId",c.teamId(),"streamerId",streamerId);Map<String,Object> replay=idempotent(actor.id(),operation,key,request);if(replay!=null)return replay;
    lock("streamer|"+streamerId+"|"+c.bookingDate());lock("makeupArtist|"+makeupArtistId+"|"+c.bookingDate());
    boolean conflict=overlap(makeupArtistId,start,start.plusMinutes(props.serviceDurationMinutes()),null);if(conflict&&!overrideAllowed)throw new BusinessException(HttpStatus.CONFLICT,"MAKEUP_ARTIST_SLOT_CONFLICT","该化妆师时段已被占用，请重新选择。");
    UUID id=UUID.randomUUID();try{jdbc.update("INSERT INTO appointment(id,booking_date,start_at,end_at,streamer_user_id,makeup_artist_id,team_id,streamer_name_snapshot,makeup_artist_name_snapshot,team_name_snapshot,status,source,conflict_override,created_by_user_id) VALUES (?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)",id,c.bookingDate(),start.toOffsetDateTime(),start.plusMinutes(props.serviceDurationMinutes()).toOffsetDateTime(),streamerId,makeupArtistId,c.teamId(),streamer.name(),makeupArtist.name(),team.name(),actor.role().name(),conflict,actor.id());}catch(DataIntegrityViolationException e){throw constraint(e);}
    jdbc.update("UPDATE app_user SET last_makeup_artist_id=?,last_start_time=?,last_team_id=?,updated_at=now(),version=version+1 WHERE id=?",makeupArtistId,c.startTime(),c.teamId(),streamerId);attendance.recalculate(id,trace);audit(id,"CREATE",actor,c.reason(),null,Map.of("bookingDate",c.bookingDate(),"startTime",c.startTime(),"makeupArtistName",makeupArtist.name(),"teamName",team.name(),"conflictOverride",conflict),trace);cards.ensureAndRefreshWindow(c.bookingDate(),false);Map<String,Object> result=Map.of("id",id,"status","ACTIVE","version",0,"conflictOverride",conflict);completeIdempotent(actor.id(),operation,key,result);return result;
  }

  @Transactional
  public Map<String,Object> streamerModify(CurrentUser actor,UUID id,ModifyCommand c,String key,String trace){
    requireStreamer(actor);Map<String,Object> replay=idempotent(actor.id(),"MODIFY_APPOINTMENT",key,Map.of("appointmentId",id,"makeupArtistId",c.makeupArtistId(),"teamId",c.teamId(),"startTime",c.startTime(),"version",c.version()));if(replay!=null)return replay;
    Appointment a=appointmentForUpdate(id);requireOwnedActive(actor,a,c.version());ZonedDateTime now=ZonedDateTime.now(clock);policy.validateStreamerLead(now,a.start());policy.validateStep(c.startTime());ZonedDateTime start=policy.start(a.date(),c.startTime());policy.validateStreamerLead(now,start);MakeupArtist makeupArtist=makeupArtist(c.makeupArtistId());Team team=team(c.teamId());validateResources(a.date(),c.startTime(),makeupArtist,team);
    if(a.makeupArtistId().equals(c.makeupArtistId())&&a.teamId().equals(c.teamId())&&a.start().toLocalTime().equals(c.startTime())){Map<String,Object> result=Map.of("id",id,"status","ACTIVE","version",a.version(),"unchanged",true);completeIdempotent(actor.id(),"MODIFY_APPOINTMENT",key,result);return result;}
    lockCounter(a.streamerId(),a.date());int modified=currentCount(a.streamerId(),a.date(),"modify_count");if(modified>=3)throw new BusinessException(HttpStatus.CONFLICT,"MODIFY_LIMIT_REACHED","该预约日期已达到3次自行修改上限，请联系管理员。");lock("makeupArtist|"+c.makeupArtistId()+"|"+a.date());if(overlap(c.makeupArtistId(),start,start.plusMinutes(props.serviceDurationMinutes()),id))throw new BusinessException(HttpStatus.CONFLICT,"MAKEUP_ARTIST_SLOT_CONFLICT","该化妆师时段已被占用，请重新选择。");
    boolean timeChanged=!a.start().toLocalTime().equals(c.startTime());Map<String,Object> before=changeView(a);updateAppointment(id,c,makeupArtist,team,start,false);incrementCount(a.streamerId(),a.date(),"modify_count");if(timeChanged)attendance.recalculate(id,trace);audit(id,"MODIFY",actor,null,before,Map.of("makeupArtistId",c.makeupArtistId(),"teamId",c.teamId(),"startTime",c.startTime()),trace);cards.refreshExistingWindow();Map<String,Object> result=Map.of("id",id,"status","ACTIVE","version",a.version()+1,"modifyCount",modified+1);completeIdempotent(actor.id(),"MODIFY_APPOINTMENT",key,result);return result;
  }

  @Transactional
  public Map<String,Object> streamerCancel(CurrentUser actor,UUID id,int version,String key,String trace){
    requireStreamer(actor);Map<String,Object> replay=idempotent(actor.id(),"CANCEL_APPOINTMENT",key,Map.of("appointmentId",id,"version",version));if(replay!=null)return replay;Appointment a=appointmentForUpdate(id);requireOwnedActive(actor,a,version);policy.validateStreamerLead(ZonedDateTime.now(clock),a.start());lockCounter(a.streamerId(),a.date());int cancelled=currentCount(a.streamerId(),a.date(),"cancel_count");if(cancelled>=2)throw new BusinessException(HttpStatus.CONFLICT,"CANCEL_LIMIT_REACHED","该预约日期已达到2次自行取消上限，请联系管理员。");cancel(a,actor,"主播取消",trace);incrementCount(a.streamerId(),a.date(),"cancel_count");Map<String,Object> result=Map.of("id",id,"status","CANCELLED","version",version+1,"cancelCount",cancelled+1);completeIdempotent(actor.id(),"CANCEL_APPOINTMENT",key,result);return result;
  }

  public List<Map<String,Object>> adminList(CurrentUser actor,LocalDate date,String streamer,String makeupArtist,String status){
    policy.validateDate(date,LocalDate.now(clock));StringBuilder sql=new StringBuilder("SELECT a.*,coalesce(u.dingtalk_user_id,u.username) identity,(SELECT count(*) FROM appointment p WHERE p.booking_date=a.booking_date AND (p.created_at<a.created_at OR (p.created_at=a.created_at AND p.id<=a.id))) daily_sequence FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.booking_date=?");List<Object> args=new ArrayList<>();args.add(date);if(streamer!=null&&!streamer.isBlank()){sql.append(" AND (u.dingtalk_user_id=? OR u.username=?)");args.add(streamer);args.add(streamer);}if(makeupArtist!=null&&!makeupArtist.isBlank()){sql.append(" AND a.makeup_artist_id=?");args.add(UUID.fromString(makeupArtist));}if(status!=null&&!status.isBlank()){sql.append(" AND a.status=?");args.add(status);}sql.append(" ORDER BY a.start_at,a.created_at");return jdbc.query(sql.toString(),this::appointmentMap,args.toArray());
  }
  /**
   * 返回管理端详情所需的扁平数据，同时保留原始 audits 兼容旧客户端。
   * 代预约人优先使用创建审计中的姓名快照，避免账号改名后历史记录漂移。
   */
  public Map<String,Object> detail(CurrentUser actor,UUID id){
    Appointment a=appointment(id);if(actor.role()==CurrentUser.Role.MAKEUP&&!a.makeupArtistId().equals(actor.makeupArtistId()))throw BusinessException.forbidden();
    List<Map<String,Object>> rows=jdbc.query("SELECT a.*,coalesce(u.dingtalk_user_id,u.username,u.id::text) identity,(SELECT count(*) FROM appointment p WHERE p.booking_date=a.booking_date AND (p.created_at<a.created_at OR (p.created_at=a.created_at AND p.id<=a.id))) daily_sequence FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.id=?",this::appointmentMap,id);
    Map<String,Object> m=new LinkedHashMap<>(rows.stream().findFirst().orElseThrow());
    Map<String,Object> metadata=jdbc.queryForMap("SELECT a.source,a.created_at,a.updated_at,a.cancelled_at,a.cancel_reason,coalesce(create_audit.actor_name_snapshot,creator.nickname) created_by_name,canceller.nickname cancelled_by_name FROM appointment a LEFT JOIN app_user creator ON creator.id=a.created_by_user_id LEFT JOIN app_user canceller ON canceller.id=a.cancelled_by_user_id LEFT JOIN LATERAL (SELECT actor_name_snapshot FROM audit_log WHERE entity_type='APPOINTMENT' AND entity_id=a.id AND action='CREATE' ORDER BY created_at LIMIT 1) create_audit ON true WHERE a.id=?",id);
    m.put("source",metadata.get("source"));m.put("createdAt",metadata.get("created_at"));m.put("updatedAt",metadata.get("updated_at"));m.put("createdByName",metadata.get("created_by_name"));m.put("cancelledAt",metadata.get("cancelled_at"));m.put("cancelReason",metadata.get("cancel_reason"));m.put("cancelledByName",metadata.get("cancelled_by_name"));
    List<Map<String,Object>> audits=jdbc.query("SELECT action,actor_name_snapshot,reason,before_data,after_data,created_at FROM audit_log WHERE entity_type='APPOINTMENT' AND entity_id=? ORDER BY created_at DESC",(rs,n)->{Map<String,Object>x=new LinkedHashMap<>();x.put("action",rs.getString(1));x.put("actorName",rs.getString(2));x.put("reason",rs.getString(3));x.put("before",rs.getString(4));x.put("after",rs.getString(5));x.put("createdAt",rs.getObject(6,OffsetDateTime.class));return x;},id);
    m.put("audits",audits);m.put("modifications",audits.stream().filter(x->"MODIFY".equals(x.get("action"))).map(this::modificationView).toList());return m;
  }

  @Transactional
  public Map<String,Object> adminModify(CurrentUser actor,UUID id,ModifyCommand c,String trace){
    if(!actor.mayModifyAppointments())throw BusinessException.forbidden();Appointment a=appointmentForUpdate(id);requireActiveVersion(a,c.version());policy.validateDate(a.date(),LocalDate.now(clock));policy.validateStep(c.startTime());boolean timeChanged=!a.start().toLocalTime().equals(c.startTime());ZonedDateTime start=policy.start(a.date(),c.startTime());if(timeChanged){policy.validateAdminLead(ZonedDateTime.now(clock),a.start());policy.validateAdminLead(ZonedDateTime.now(clock),start);}MakeupArtist makeupArtist=makeupArtist(c.makeupArtistId());Team team=team(c.teamId());validateResources(a.date(),c.startTime(),makeupArtist,team);if(!timeChanged&&a.makeupArtistId().equals(c.makeupArtistId())&&a.teamId().equals(c.teamId()))return Map.of("id",id,"status","ACTIVE","version",a.version(),"unchanged",true);lock("makeupArtist|"+c.makeupArtistId()+"|"+a.date());boolean conflict=overlap(c.makeupArtistId(),start,start.plusMinutes(props.serviceDurationMinutes()),id);Map<String,Object> before=changeView(a);updateAppointment(id,c,makeupArtist,team,start,conflict);if(timeChanged)attendance.recalculate(id,trace);audit(id,"MODIFY",actor,c.reason(),before,Map.of("makeupArtistId",c.makeupArtistId(),"teamId",c.teamId(),"startTime",c.startTime(),"conflictOverride",conflict),trace);cards.refreshExistingWindow();return Map.of("id",id,"status","ACTIVE","version",a.version()+1,"conflictOverride",conflict);
  }

  @Transactional
  public Map<String,Object> adminCancel(CurrentUser actor,UUID id,StatusCommand c,String trace){
    if(!actor.mayCancelAppointments())throw BusinessException.forbidden();Appointment a=appointmentForUpdate(id);requireActiveVersion(a,c.version());policy.validateDate(a.date(),LocalDate.now(clock));cancel(a,actor,c.reason(),trace);return Map.of("id",id,"status","CANCELLED","version",c.version()+1);
  }

  @Transactional
  public Map<String,Object> manualScheduleCard(CurrentUser actor,LocalDate date){if(!actor.isAdministrator())throw BusinessException.forbidden();policy.validateDate(date,LocalDate.now(clock));cards.ensureChronologicalScheduleCard(date,true);return Map.of("businessDate",date,"queued",true);}

  /**
   * 查询今天和明天是否曾被钉钉网关实际接收。
   * first_delivered_at 为空时，即使任务已经排队，也不能在前端显示“重新发送”。
   */
  public Map<String,Object> scheduleCardStatus(CurrentUser actor){
    if(!actor.isAdministrator())throw BusinessException.forbidden();LocalDate today=LocalDate.now(clock);List<Map<String,Object>> dates=new ArrayList<>();
    for(int offset=0;offset<2;offset++){LocalDate date=today.plusDays(offset);List<OffsetDateTime> delivered=jdbc.query("SELECT first_delivered_at FROM daily_card WHERE business_date=? AND group_open_conversation_id=?",(rs,n)->rs.getObject(1,OffsetDateTime.class),date,props.groupId());OffsetDateTime first=delivered.isEmpty()?null:delivered.getFirst();Map<String,Object> item=new LinkedHashMap<>();item.put("key",offset==0?"today":"tomorrow");item.put("bookingDate",date);item.put("hasSuccessfulDelivery",first!=null);item.put("firstDeliveredAt",first);dates.add(item);}return Map.of("dates",dates);
  }

  private Map<String,Object> modificationView(Map<String,Object> audit){
    Map<String,Object> result=new LinkedHashMap<>();result.put("actorName",audit.get("actorName"));result.put("createdAt",audit.get("createdAt"));result.put("reason",audit.get("reason"));List<Map<String,Object>> changes=new ArrayList<>();
    try{JsonNode before=json.readTree(Objects.toString(audit.get("before"),"{}"));JsonNode after=json.readTree(Objects.toString(audit.get("after"),"{}"));addModification(changes,"预约时间",before,after,"startTime",null);addModification(changes,"化妆师",before,after,"makeupArtistId","makeup_artist");addModification(changes,"团队",before,after,"teamId","team");}catch(JsonProcessingException ignored){}
    result.put("changes",changes);return result;
  }
  private void addModification(List<Map<String,Object>> changes,String field,JsonNode before,JsonNode after,String key,String table){String oldValue=jsonValue(before,key),newValue=jsonValue(after,key);if(Objects.equals(oldValue,newValue))return;if(table!=null){oldValue=resourceName(table,oldValue);newValue=resourceName(table,newValue);}Map<String,Object> change=new LinkedHashMap<>();change.put("field",field);change.put("before",oldValue);change.put("after",newValue);changes.add(change);}
  private String jsonValue(JsonNode node,String key){JsonNode value=node.get(key);return value==null||value.isNull()?null:value.asText();}
  private String resourceName(String table,String id){if(id==null||id.isBlank())return id;try{List<String> names=jdbc.query("SELECT name FROM "+table+" WHERE id=?",(rs,n)->rs.getString(1),UUID.fromString(id));return names.isEmpty()?id:names.getFirst();}catch(IllegalArgumentException e){return id;}}

  private void cancel(Appointment a,CurrentUser actor,String reason,String trace){attendance.recalculate(a.id(),trace);jdbc.update("UPDATE appointment SET status='CANCELLED',attendance_frozen=true,cancelled_at=now(),cancelled_by_user_id=?,cancel_reason=?,version=version+1,updated_at=now() WHERE id=?",actor.id(),blankToNull(reason),a.id());audit(a.id(),"CANCEL",actor,reason,Map.of("status","ACTIVE"),Map.of("status","CANCELLED","attendanceFrozen",true),trace);cards.refreshExistingWindow();}
  private void updateAppointment(UUID id,ModifyCommand c,MakeupArtist makeupArtist,Team team,ZonedDateTime start,boolean conflict){int n=jdbc.update("UPDATE appointment SET makeup_artist_id=?,team_id=?,start_at=?,end_at=?,makeup_artist_name_snapshot=?,team_name_snapshot=?,conflict_override=?,card_past_refreshed_at=CASE WHEN start_at<>? THEN NULL ELSE card_past_refreshed_at END,version=version+1,updated_at=now() WHERE id=? AND version=?",c.makeupArtistId(),c.teamId(),start.toOffsetDateTime(),start.plusMinutes(props.serviceDurationMinutes()).toOffsetDateTime(),makeupArtist.name(),team.name(),conflict,start.toOffsetDateTime(),id,c.version());if(n==0)throw versionConflict();}
  private void validateResources(LocalDate date,LocalTime start,MakeupArtist makeupArtist,Team team){policy.validateMakeupArtist(date,start,makeupArtist.workDays(),makeupArtist.workStart(),makeupArtist.workEnd(),makeupArtist.scheduleEnabled(),makeupArtist.active(),makeupArtist.attending());if(!team.active())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"TEAM_UNAVAILABLE","团队已停用，请选择其他团队。");}
  private List<Map<String,Object>> schedule(LocalDate date,CurrentUser actor){return jdbc.query("SELECT to_char(start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI'),makeup_artist_name_snapshot,team_name_snapshot,streamer_name_snapshot,streamer_user_id,attendance_status,conflict_override FROM appointment WHERE booking_date=? AND status='ACTIVE' ORDER BY start_at",(rs,n)->Map.of("startTime",rs.getString(1),"makeupArtistName",rs.getString(2),"teamName",rs.getString(3),"streamerName",rs.getString(4),"isMine",actor.id().equals(rs.getObject(5,UUID.class)),"attendanceStatus",rs.getString(6),"conflictOverride",rs.getBoolean(7),"booked",true),date);}
  private Map<String,Object> findActive(UUID userId,LocalDate date){List<Map<String,Object>> rows=jdbc.query("SELECT a.*,coalesce(u.dingtalk_user_id,u.username) identity,(SELECT count(*) FROM appointment p WHERE p.booking_date=a.booking_date AND (p.created_at<a.created_at OR (p.created_at=a.created_at AND p.id<=a.id))) daily_sequence FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.streamer_user_id=? AND a.booking_date=? AND a.status='ACTIVE'",this::appointmentMap,userId,date);return rows.isEmpty()?null:rows.getFirst();}
  private List<Map<String,Object>> findCancelled(UUID userId,LocalDate date){return jdbc.query("SELECT a.*,coalesce(u.dingtalk_user_id,u.username) identity,(SELECT count(*) FROM appointment p WHERE p.booking_date=a.booking_date AND (p.created_at<a.created_at OR (p.created_at=a.created_at AND p.id<=a.id))) daily_sequence FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.streamer_user_id=? AND a.booking_date=? AND a.status='CANCELLED' ORDER BY a.created_at DESC",this::appointmentMap,userId,date);}
  private Map<String,Object> appointmentMap(java.sql.ResultSet rs,int n)throws java.sql.SQLException{Map<String,Object> m=new LinkedHashMap<>();LocalDate d=rs.getObject("booking_date",LocalDate.class);String identity=rs.getString("identity");m.put("id",rs.getObject("id",UUID.class));m.put("bookingNumber",String.format("%02d%02d%05d-%s",d.getMonthValue(),d.getDayOfMonth(),10000+rs.getInt("daily_sequence"),identity));m.put("bookingDate",d);m.put("startTime",rs.getObject("start_at",OffsetDateTime.class).atZoneSameInstant(props.zoneId()).toLocalTime().toString());m.put("streamerName",rs.getString("streamer_name_snapshot"));m.put("streamerDingTalkUserId",identity);m.put("makeupArtistId",rs.getObject("makeup_artist_id",UUID.class));m.put("makeupArtistName",rs.getString("makeup_artist_name_snapshot"));m.put("teamId",rs.getObject("team_id",UUID.class));m.put("teamName",rs.getString("team_name_snapshot"));m.put("status",rs.getString("status"));m.put("attendanceStatus",rs.getString("attendance_status"));m.put("attendanceFrozen",rs.getBoolean("attendance_frozen"));m.put("attendanceEvidenceAt",rs.getObject("attendance_evidence_at"));m.put("conflictOverride",rs.getBoolean("conflict_override"));m.put("version",rs.getInt("version"));m.put("createdAt",rs.getObject("created_at",OffsetDateTime.class));m.put("changed",rs.getInt("version")>0);return m;}
  private Map<String,Object> counts(UUID streamer,LocalDate date){List<Map<String,Object>> rows=jdbc.query("SELECT cancel_count,modify_count FROM appointment_operation_counter WHERE streamer_user_id=? AND booking_date=?",(rs,n)->Map.of("cancelCount",rs.getInt(1),"modifyCount",rs.getInt(2)),streamer,date);return rows.isEmpty()?Map.of("cancelCount",0,"modifyCount",0):rows.getFirst();}
  private void lockCounter(UUID streamer,LocalDate date){jdbc.update("INSERT INTO appointment_operation_counter(streamer_user_id,booking_date) VALUES (?,?) ON CONFLICT DO NOTHING",streamer,date);jdbc.queryForObject("SELECT cancel_count FROM appointment_operation_counter WHERE streamer_user_id=? AND booking_date=? FOR UPDATE",Integer.class,streamer,date);}
  private int currentCount(UUID streamer,LocalDate date,String column){return jdbc.queryForObject("SELECT "+column+" FROM appointment_operation_counter WHERE streamer_user_id=? AND booking_date=?",Integer.class,streamer,date);}
  private void incrementCount(UUID streamer,LocalDate date,String column){jdbc.update("UPDATE appointment_operation_counter SET "+column+"="+column+"+1,updated_at=now() WHERE streamer_user_id=? AND booking_date=?",streamer,date);}
  private boolean hasAnyAvailability(LocalDate date,CurrentUser actor){return jdbc.query("SELECT id FROM makeup_artist WHERE is_active AND is_attending",(rs,n)->rs.getObject(1,UUID.class)).stream().anyMatch(id->hasMakeupArtistAvailability(id,date,actor));}
  private boolean hasMakeupArtistAvailability(UUID makeupArtistId,LocalDate date,CurrentUser actor){return ((List<?>)availability(actor,date,makeupArtistId).get("slots")).stream().anyMatch(x->Boolean.TRUE.equals(((Map<?,?>)x).get("available")));}
  private boolean overlap(UUID makeupArtist,ZonedDateTime start,ZonedDateTime end,UUID exclude){Integer n=jdbc.queryForObject("SELECT count(*) FROM appointment WHERE makeup_artist_id=? AND status='ACTIVE' AND tstzrange(start_at,end_at,'[)') && tstzrange(?,?, '[)') AND (?::uuid IS NULL OR id<>?::uuid)",Integer.class,makeupArtist,start.toOffsetDateTime(),end.toOffsetDateTime(),exclude==null?null:exclude.toString(),exclude==null?null:exclude.toString());return n!=null&&n>0;}
  private Map<String,Object> defaults(UUID userId,LocalDate date){Map<String,Object> m=new LinkedHashMap<>();List<Object[]> rows=jdbc.query("SELECT last_makeup_artist_id,last_start_time,last_team_id FROM app_user WHERE id=?",(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getObject(2,LocalTime.class),rs.getObject(3,UUID.class)},userId);if(rows.isEmpty())return m;Object[] r=rows.getFirst();if(r[2]!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_active FROM team WHERE id=?",Boolean.class,r[2])))m.put("teamId",r[2]);if(r[0]!=null&&r[1]!=null){try{MakeupArtist t=makeupArtist((UUID)r[0]);policy.validateMakeupArtist(date,(LocalTime)r[1],t.workDays(),t.workStart(),t.workEnd(),t.scheduleEnabled(),t.active(),t.attending());ZonedDateTime start=policy.start(date,(LocalTime)r[1]);policy.validateStreamerLead(ZonedDateTime.now(clock),start);if(!overlap((UUID)r[0],start,start.plusMinutes(props.serviceDurationMinutes()),null)){m.put("makeupArtistId",r[0]);m.put("startTime",r[1].toString());}}catch(BusinessException ignored){m.put("message","上次选择当前不可用，已清空化妆师和时间。");}}return m;}
  private UUID streamerId(String identity){List<UUID> ids=jdbc.query("SELECT id FROM app_user WHERE role='STREAMER' AND is_active AND (dingtalk_user_id=? OR username=?)",(rs,n)->rs.getObject(1,UUID.class),identity,identity);if(ids.isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"STREAMER_UNAVAILABLE","主播不存在、已停用或身份不正确。");return ids.getFirst();}
  private User user(UUID id){return jdbc.queryForObject("SELECT id,nickname,is_attending FROM app_user WHERE id=? AND is_active",(rs,n)->new User(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3)),id);}
  private MakeupArtist makeupArtist(UUID id){List<MakeupArtist> rows=jdbc.query("SELECT id,name,work_days,work_start,work_end,schedule_enabled,is_active,is_attending FROM makeup_artist WHERE id=?",(rs,n)->new MakeupArtist(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,LocalTime.class),rs.getObject(5,LocalTime.class),rs.getBoolean(6),rs.getBoolean(7),rs.getBoolean(8)),id);if(rows.isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"MAKEUP_ARTIST_UNAVAILABLE","化妆师不存在或不可用。");return rows.getFirst();}
  private Team team(UUID id){List<Team> rows=jdbc.query("SELECT id,name,is_active FROM team WHERE id=?",(rs,n)->new Team(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3)),id);if(rows.isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"TEAM_UNAVAILABLE","团队不存在或已停用。");return rows.getFirst();}
  private Appointment appointment(UUID id){List<Appointment> rows=queryAppointment("SELECT id,booking_date,start_at,streamer_user_id,makeup_artist_id,team_id,status,version FROM appointment WHERE id=?",id);if(rows.isEmpty())throw notFound();return rows.getFirst();}
  private Appointment appointmentForUpdate(UUID id){List<Appointment> rows=queryAppointment("SELECT id,booking_date,start_at,streamer_user_id,makeup_artist_id,team_id,status,version FROM appointment WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw notFound();return rows.getFirst();}
  private List<Appointment> queryAppointment(String sql,Object... args){return jdbc.query(sql,(rs,n)->new Appointment(rs.getObject(1,UUID.class),rs.getObject(2,LocalDate.class),rs.getObject(3,OffsetDateTime.class).atZoneSameInstant(props.zoneId()),rs.getObject(4,UUID.class),rs.getObject(5,UUID.class),rs.getObject(6,UUID.class),rs.getString(7),rs.getInt(8)),args);}
  private void requireStreamer(CurrentUser actor){if(!actor.isStreamer())throw BusinessException.forbidden();}
  private void requireOwnedActive(CurrentUser actor,Appointment a,int version){if(!a.streamerId().equals(actor.id()))throw BusinessException.forbidden();requireActiveVersion(a,version);}
  private void requireActiveVersion(Appointment a,int version){if(!"ACTIVE".equals(a.status()))throw terminal();if(a.version()!=version)throw versionConflict();}
  private Map<String,Object> changeView(Appointment a){return Map.of("makeupArtistId",a.makeupArtistId(),"teamId",a.teamId(),"startTime",a.start().toLocalTime());}
  private void lock(String key){jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},key);}
  private boolean systemEnabled(){Boolean v=jdbc.queryForObject("SELECT boolean_value FROM system_setting WHERE key='SYSTEM_ENABLED'",Boolean.class);return Boolean.TRUE.equals(v);}
  /**
   * 以“用户 + 操作 + 幂等键”加事务级咨询锁，再比较请求摘要。
   * 相同请求重放已保存的响应；同一键携带不同内容会明确拒绝，避免误复用。
   */
  private Map<String,Object> idempotent(UUID actor,String operation,String key,Object request){if(key==null||key.isBlank())throw new BusinessException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED","缺少幂等键。");String hash=hash(request);lock(actor+"|"+operation+"|"+key);List<Map<String,Object>> rows=jdbc.query("SELECT request_hash,status,response_body::text FROM idempotency_record WHERE actor_user_id=? AND operation=? AND idempotency_key=?",(rs,n)->Map.of("hash",rs.getString(1),"status",rs.getString(2),"body",Objects.toString(rs.getString(3),"")),actor,operation,key);if(rows.isEmpty()){jdbc.update("INSERT INTO idempotency_record(actor_user_id,operation,idempotency_key,request_hash,status,expires_at) VALUES (?,?,?,?,'PROCESSING',now()+interval '24 hours')",actor,operation,key,hash);return null;}Map<String,Object> row=rows.getFirst();if(!hash.equals(row.get("hash")))throw new BusinessException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","同一幂等键不能用于不同请求。");if("SUCCEEDED".equals(row.get("status"))){try{return json.readValue((String)row.get("body"),Map.class);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}throw new BusinessException(HttpStatus.CONFLICT,"REQUEST_IN_PROGRESS","相同请求正在处理中，请稍后重试。");}
  private void completeIdempotent(UUID actor,String operation,String key,Object response){jdbc.update("UPDATE idempotency_record SET status='SUCCEEDED',response_status=200,response_body=CAST(? AS jsonb),updated_at=now() WHERE actor_user_id=? AND operation=? AND idempotency_key=?",toJson(response),actor,operation,key);}
  private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}catch(Exception e){throw new IllegalStateException(e);}}
  private void audit(UUID entity,String action,CurrentUser actor,String reason,Object before,Object after,String trace){jdbc.update("INSERT INTO audit_log(id,entity_type,entity_id,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,reason,before_data,after_data,trace_id) VALUES (?,'APPOINTMENT',?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)",UUID.randomUUID(),entity,action,actor.id(),actor.loginId(),actor.nickname(),blankToNull(reason),before==null?null:toJson(before),after==null?null:toJson(after),trace);}
  private BusinessException constraint(DataIntegrityViolationException e){String message=Objects.toString(e.getMostSpecificCause().getMessage(),"");if(message.contains("uq_active_appointment_streamer_date"))return new BusinessException(HttpStatus.CONFLICT,"DAILY_APPOINTMENT_EXISTS","该主播在这一天已有有效预约。");return new BusinessException(HttpStatus.CONFLICT,"BOOKING_CONFLICT","预约条件已发生变化，请刷新后重试。");}
  private BusinessException terminal(){return new BusinessException(HttpStatus.CONFLICT,"APPOINTMENT_TERMINAL","已取消预约不能再次修改或取消。");}private BusinessException versionConflict(){return new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","数据已变化，请刷新后重试。");}private BusinessException notFound(){return new BusinessException(HttpStatus.NOT_FOUND,"APPOINTMENT_NOT_FOUND","预约不存在或不可见。");}
  private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}private String toJson(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
  record User(UUID id,String name,boolean attending){}record MakeupArtist(UUID id,String name,String workDays,LocalTime workStart,LocalTime workEnd,boolean scheduleEnabled,boolean active,boolean attending){}record Team(UUID id,String name,boolean active){}record Appointment(UUID id,LocalDate date,ZonedDateTime start,UUID streamerId,UUID makeupArtistId,UUID teamId,String status,int version){}
  public record CreateCommand(LocalDate bookingDate,UUID makeupArtistId,UUID teamId,LocalTime startTime,String streamerUserId,String reason){public CreateCommand{if(reason==null)reason="";}}
  public record ModifyCommand(UUID makeupArtistId,UUID teamId,LocalTime startTime,int version,String reason){public ModifyCommand{if(reason==null)reason="";}}
  public record StatusCommand(int version,String reason){public StatusCommand{if(reason==null)reason="";}}
}
