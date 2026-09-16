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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
  private final JdbcTemplate jdbc;private final BookingPolicy policy;private final BookingProperties props;private final Clock clock;private final ObjectMapper json;private final CardRefreshService cards;
  @Autowired public AttendanceService(JdbcTemplate jdbc,BookingPolicy policy,BookingProperties props,Clock clock,ObjectMapper json,CardRefreshService cards){this.jdbc=jdbc;this.policy=policy;this.props=props;this.clock=clock;this.json=json;this.cards=cards;}
  AttendanceService(JdbcTemplate jdbc,BookingPolicy policy,BookingProperties props,Clock clock,ObjectMapper json){this(jdbc,policy,props,clock,json,new CardRefreshService(jdbc,props,clock));}

  @Transactional
  public void recalculate(UUID appointmentId,String trace){
    List<Row> rows=jdbc.query("SELECT a.id,a.start_at,a.booking_date,a.status,a.attendance_status,a.attendance_frozen,a.attendance_event_id,a.attendance_evidence_at,u.dingtalk_user_id,a.streamer_name_snapshot FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.id=?",(rs,n)->new Row(rs.getObject(1,UUID.class),rs.getObject(2,OffsetDateTime.class).atZoneSameInstant(props.zoneId()),rs.getObject(3,LocalDate.class),rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getObject(7,UUID.class),rs.getObject(8,OffsetDateTime.class)==null?null:rs.getObject(8,OffsetDateTime.class).atZoneSameInstant(props.zoneId()),rs.getString(9),rs.getString(10)),appointmentId);if(rows.isEmpty())return;Row row=rows.getFirst();if(row.frozen()||!"ACTIVE".equals(row.status()))return;
    Evidence evidence=firstEvidence(row);String status=policy.attendanceStatus(ZonedDateTime.now(clock),row.start(),evidence==null?null:evidence.at());UUID evidenceId=evidence==null?null:evidence.id();OffsetDateTime evidenceAt=evidence==null?null:evidence.at().toOffsetDateTime();
    if(!status.equals(row.attendanceStatus())||!Objects.equals(evidenceId,row.evidenceId())){jdbc.update("UPDATE appointment SET attendance_status=?,attendance_event_id=?,attendance_evidence_at=?,updated_at=now() WHERE id=? AND NOT attendance_frozen",status,evidenceId,evidenceAt,row.id());Map<String,Object> before=Map.of("status",row.attendanceStatus());Map<String,Object> after=new LinkedHashMap<>();after.put("status",status);after.put("evidenceAt",evidenceAt);audit(row.id(),"ATTENDANCE_RECALCULATED",before,after,trace);cards.refreshExistingWindow();}
    if("LATE".equals(status)&&evidence==null&&row.dingTalkUserId()!=null)ensureLateReminder(row);
  }

  @Transactional
  public boolean ingest(String externalEventId,String orgId,String deviceSn,String dingTalkUserId,ZonedDateTime occurredAt,String rawPayload,String trace){
    UUID eventId=UUID.randomUUID();int inserted=jdbc.update("INSERT INTO gate_event(id,external_event_id,org_id,device_sn,dingtalk_user_id,occurred_at,event_type,raw_payload) VALUES (?,?,?,?,?,?,'REC_SUCCESS',CAST(? AS jsonb)) ON CONFLICT(external_event_id) DO NOTHING",eventId,externalEventId,orgId,deviceSn,dingTalkUserId,occurredAt.toOffsetDateTime(),rawPayload);
    if(inserted==0)return false;
    OffsetDateTime eventTime=occurredAt.toOffsetDateTime();List<UUID> appointments=jdbc.query("SELECT a.id FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id WHERE a.status='ACTIVE' AND NOT a.attendance_frozen AND u.dingtalk_user_id=? AND (a.attendance_event_id IS NULL OR a.attendance_evidence_at>?) AND a.start_at>? AND a.start_at<? ORDER BY CASE WHEN a.start_at<=? THEN 0 ELSE 1 END,abs(extract(epoch FROM (a.start_at-?::timestamptz))),a.start_at,a.id LIMIT 1 FOR UPDATE OF a SKIP LOCKED",(rs,n)->rs.getObject(1,UUID.class),dingTalkUserId,eventTime,eventTime.minusMinutes(props.lateGraceMinutes()),eventTime.plusMinutes(props.attendanceWindowMinutes()),eventTime,eventTime);appointments.stream().findFirst().ifPresent(id->recalculate(id,trace));return true;
  }

  @Scheduled(fixedDelayString="${jiabei.booking.attendance-poll-ms:10000}")
  @Transactional public void refreshActive(){LocalDate today=LocalDate.now(clock);LocalDate from=today.minusDays(1),to=today.plusDays(1);List<UUID> ids=jdbc.query("SELECT id FROM appointment WHERE status='ACTIVE' AND NOT attendance_frozen AND booking_date BETWEEN ? AND ? ORDER BY start_at,id",(rs,n)->rs.getObject(1,UUID.class),from,to);ids.forEach(id->recalculate(id,"attendance-scheduler"));List<LocalDate> crossed=jdbc.query("UPDATE appointment SET card_past_refreshed_at=now() WHERE status='ACTIVE' AND card_past_refreshed_at IS NULL AND booking_date BETWEEN ? AND ? AND start_at + interval '20 minutes' <= ? RETURNING booking_date",(rs,n)->rs.getObject(1,LocalDate.class),from,to,ZonedDateTime.now(clock).toOffsetDateTime());if(!crossed.isEmpty())cards.refreshExistingWindow();}

  private Evidence firstEvidence(Row row){if(row.evidenceAt()!=null)return new Evidence(row.evidenceId(),row.evidenceAt());if(row.dingTalkUserId()==null)return null;List<Evidence> events=jdbc.query("SELECT g.id,g.occurred_at FROM gate_event g WHERE g.dingtalk_user_id=? AND g.occurred_at>? AND g.occurred_at<? AND g.occurred_at<=? AND NOT EXISTS (SELECT 1 FROM appointment bound WHERE bound.attendance_event_id=g.id AND bound.status='ACTIVE' AND bound.id<>?) ORDER BY g.occurred_at,g.id LIMIT 1 FOR UPDATE OF g SKIP LOCKED",(rs,n)->new Evidence(rs.getObject(1,UUID.class),rs.getObject(2,OffsetDateTime.class).atZoneSameInstant(props.zoneId())),row.dingTalkUserId(),row.start().minusMinutes(props.attendanceWindowMinutes()).toOffsetDateTime(),row.start().plusMinutes(props.lateGraceMinutes()).toOffsetDateTime(),ZonedDateTime.now(clock).toOffsetDateTime(),row.id());return events.isEmpty()?null:events.getFirst();}
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
  record Row(UUID id,ZonedDateTime start,LocalDate date,String status,String attendanceStatus,boolean frozen,UUID evidenceId,ZonedDateTime evidenceAt,String dingTalkUserId,String streamerName){}record Evidence(UUID id,ZonedDateTime at){}
}
