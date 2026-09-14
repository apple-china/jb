package com.jiabei.cloud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.domain.BookingPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
  private final JdbcTemplate jdbc;private final BookingPolicy policy;private final BookingProperties props;private final Clock clock;private final ObjectMapper json;
  public AttendanceService(JdbcTemplate jdbc,BookingPolicy policy,BookingProperties props,Clock clock,ObjectMapper json){this.jdbc=jdbc;this.policy=policy;this.props=props;this.clock=clock;this.json=json;}

  @Transactional
  public void recalculate(UUID appointmentId,String trace){
    List<Row> rows=jdbc.query("SELECT a.id,a.start_at,a.booking_date,a.status,a.attendance_status,a.attendance_frozen,a.attendance_event_id,u.dingtalk_user_id,a.streamer_name_snapshot FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.id=?",(rs,n)->new Row(rs.getObject(1,UUID.class),rs.getObject(2,OffsetDateTime.class).atZoneSameInstant(props.zoneId()),rs.getObject(3,LocalDate.class),rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getObject(7,UUID.class),rs.getString(8),rs.getString(9)),appointmentId);if(rows.isEmpty())return;Row row=rows.getFirst();if(row.frozen()||!"ACTIVE".equals(row.status()))return;
    Evidence evidence=firstEvidence(row);String status=policy.attendanceStatus(ZonedDateTime.now(clock),row.start(),evidence==null?null:evidence.at());UUID evidenceId=evidence==null?null:evidence.id();OffsetDateTime evidenceAt=evidence==null?null:evidence.at().toOffsetDateTime();
    if(!status.equals(row.attendanceStatus())||!Objects.equals(evidenceId,row.evidenceId())){jdbc.update("UPDATE appointment SET attendance_status=?,attendance_event_id=?,attendance_evidence_at=?,updated_at=now() WHERE id=? AND NOT attendance_frozen",status,evidenceId,evidenceAt,row.id());Map<String,Object> before=Map.of("status",row.attendanceStatus());Map<String,Object> after=new LinkedHashMap<>();after.put("status",status);after.put("evidenceAt",evidenceAt);audit(row.id(),"ATTENDANCE_RECALCULATED",before,after,trace);}
    if("LATE".equals(status)&&evidence==null&&row.dingTalkUserId()!=null)ensureLateReminder(row);
  }

  @Transactional
  public boolean ingest(String externalEventId,String orgId,String deviceSn,String dingTalkUserId,ZonedDateTime occurredAt,String rawPayload,String trace){
    int inserted=jdbc.update("INSERT INTO gate_event(id,external_event_id,org_id,device_sn,dingtalk_user_id,occurred_at,event_type,raw_payload) VALUES (?,?,?,?,?,?,'REC_SUCCESS',CAST(? AS jsonb)) ON CONFLICT(external_event_id) DO NOTHING",UUID.randomUUID(),externalEventId,orgId,deviceSn,dingTalkUserId,occurredAt.toOffsetDateTime(),rawPayload);
    if(inserted==0)return false;
    List<UUID> appointments=jdbc.query("SELECT a.id FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.status='ACTIVE' AND NOT a.attendance_frozen AND u.dingtalk_user_id=? AND a.booking_date BETWEEN ? AND ?",(rs,n)->rs.getObject(1,UUID.class),dingTalkUserId,occurredAt.toLocalDate().minusDays(1),occurredAt.toLocalDate().plusDays(1));appointments.forEach(id->recalculate(id,trace));return true;
  }

  @Scheduled(fixedDelayString="${jiabei.booking.attendance-poll-ms:60000}")
  @Transactional public void refreshActive(){LocalDate today=LocalDate.now(clock);List<UUID> ids=jdbc.query("SELECT id FROM appointment WHERE status='ACTIVE' AND NOT attendance_frozen AND booking_date BETWEEN ? AND ?",(rs,n)->rs.getObject(1,UUID.class),today,today.plusDays(1));ids.forEach(id->recalculate(id,"attendance-scheduler"));}

  private Evidence firstEvidence(Row row){if(row.dingTalkUserId()==null)return null;List<Evidence> events=jdbc.query("SELECT id,occurred_at FROM gate_event WHERE dingtalk_user_id=? AND occurred_at>? AND occurred_at<? AND occurred_at<=? ORDER BY occurred_at,id LIMIT 1",(rs,n)->new Evidence(rs.getObject(1,UUID.class),rs.getObject(2,OffsetDateTime.class).atZoneSameInstant(props.zoneId())),row.dingTalkUserId(),row.start().minusMinutes(props.attendanceWindowMinutes()).toOffsetDateTime(),row.date().plusDays(1).atStartOfDay(props.zoneId()).toOffsetDateTime(),ZonedDateTime.now(clock).toOffsetDateTime());return events.isEmpty()?null:events.getFirst();}
  private void ensureLateReminder(Row row){
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('late-reminder-message-pool',0))",rs->{});
    List<Integer> recentTitles=jdbc.query("SELECT title_index FROM late_notification WHERE title_index IS NOT NULL ORDER BY created_at DESC,appointment_id DESC LIMIT 5",(rs,n)->rs.getInt(1));
    List<Integer> recentMessages=jdbc.query("SELECT message_index FROM late_notification WHERE message_index IS NOT NULL ORDER BY created_at DESC,appointment_id DESC LIMIT 5",(rs,n)->rs.getInt(1));
    CardProjectionService.LateReminderSelection selection=CardProjectionService.selectLateReminder(row.id(),row.streamerName(),row.dingTalkUserId(),recentTitles,recentMessages);
    String out="late-reminder-"+row.id();
    int inserted=jdbc.update("INSERT INTO late_notification(appointment_id,out_track_id,dingtalk_user_id,title_index,message_index,message_text) VALUES (?,?,?,?,?,?) ON CONFLICT(appointment_id) DO NOTHING",row.id(),out,row.dingTalkUserId(),selection.titleIndex(),selection.messageIndex(),selection.text());
    if(inserted>0)jdbc.update("INSERT INTO integration_job(id,job_type,business_key,payload,status,max_attempts) VALUES (?,'LATE_REMINDER',?,jsonb_build_object('appointmentId',?::text),'PENDING',?) ON CONFLICT DO NOTHING",UUID.randomUUID(),row.id().toString(),row.id().toString(),props.outboxMaxAttempts());
  }
  private void audit(UUID appointment,String action,Object before,Object after,String trace){jdbc.update("INSERT INTO audit_log(id,entity_type,entity_id,action,actor_name_snapshot,before_data,after_data,trace_id) VALUES (?,'APPOINTMENT',?,?, '系统',CAST(? AS jsonb),CAST(? AS jsonb),?)",UUID.randomUUID(),appointment,action,toJson(before),toJson(after),trace);}
  private String toJson(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
  record Row(UUID id,ZonedDateTime start,LocalDate date,String status,String attendanceStatus,boolean frozen,UUID evidenceId,String dingTalkUserId,String streamerName){}record Evidence(UUID id,ZonedDateTime at){}
}
