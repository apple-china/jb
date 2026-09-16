package com.jiabei.cloud.service;

import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.web.BusinessException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AppointmentAnalyticsService {
  private final JdbcTemplate jdbc;

  public AppointmentAnalyticsService(JdbcTemplate jdbc){this.jdbc=jdbc;}

  public Map<String,Object> analytics(CurrentUser actor,LocalDate start,LocalDate end){
    if(actor.role()!=CurrentUser.Role.SUPER_ADMIN&&actor.role()!=CurrentUser.Role.OPERATOR&&actor.role()!=CurrentUser.Role.OBSERVER)throw BusinessException.forbidden();
    if(start==null||end==null||start.isAfter(end)||start.plusDays(366).isBefore(end))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_ANALYTICS_RANGE","统计日期范围无效或超过 366 天。");
    Map<String,Object> rawSummary=jdbc.queryForMap("""
      SELECT count(*) total,
        sum(CASE WHEN status='ACTIVE' THEN 1 ELSE 0 END) active,
        sum(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END) cancelled,
        sum(CASE WHEN attendance_status='LATE' THEN 1 ELSE 0 END) late,
        sum(CASE WHEN attendance_status='NOT_ARRIVED' THEN 1 ELSE 0 END) not_arrived
      FROM appointment WHERE booking_date BETWEEN ? AND ?
      """,start,end);
    Map<String,Object> summary=new LinkedHashMap<>();
    summary.put("total",number(rawSummary.get("total")));summary.put("active",number(rawSummary.get("active")));summary.put("cancelled",number(rawSummary.get("cancelled")));summary.put("late",number(rawSummary.get("late")));summary.put("notArrived",number(rawSummary.get("not_arrived")));
    Map<String,Object> operations=jdbc.queryForMap("""
      SELECT sum(CASE WHEN al.action='MODIFY' THEN 1 ELSE 0 END) modifications,
        sum(CASE WHEN al.action='CANCEL' THEN 1 ELSE 0 END) cancellations
      FROM audit_log al JOIN appointment a ON a.id=al.entity_id
      WHERE al.entity_type='APPOINTMENT' AND al.action IN ('MODIFY','CANCEL') AND a.booking_date BETWEEN ? AND ?
      """,start,end);
    summary.put("modifications",number(operations.get("modifications")));
    summary.put("cancellations",number(operations.get("cancellations")));
    List<Long> lateMinutes=jdbc.query("SELECT start_at,attendance_evidence_at FROM appointment WHERE booking_date BETWEEN ? AND ? AND attendance_status='LATE' AND attendance_evidence_at IS NOT NULL",(rs,n)->Math.max(0,Duration.between(rs.getObject(1,OffsetDateTime.class),rs.getObject(2,OffsetDateTime.class)).toMinutes()),start,end);
    summary.put("averageLateMinutes",lateMinutes.isEmpty()?0:Math.round(lateMinutes.stream().mapToLong(Long::longValue).average().orElse(0)));

    Map<LocalDate,Map<String,Object>> daily=new LinkedHashMap<>();
    for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1)){Map<String,Object> row=new LinkedHashMap<>();row.put("date",date);row.put("total",0);row.put("late",0);row.put("notArrived",0);daily.put(date,row);}
    jdbc.query("""
      SELECT booking_date,count(*) total,
        sum(CASE WHEN attendance_status='LATE' THEN 1 ELSE 0 END) late,
        sum(CASE WHEN attendance_status='NOT_ARRIVED' THEN 1 ELSE 0 END) not_arrived
      FROM appointment WHERE booking_date BETWEEN ? AND ? GROUP BY booking_date ORDER BY booking_date
      """,rs->{Map<String,Object> row=daily.get(rs.getObject(1,LocalDate.class));row.put("total",rs.getLong(2));row.put("late",rs.getLong(3));row.put("notArrived",rs.getLong(4));},start,end);

    Map<UUID,Map<String,Object>> streamerRows=new LinkedHashMap<>();
    jdbc.query("""
      SELECT streamer_user_id,max(streamer_name_snapshot),count(*),
        sum(CASE WHEN attendance_status='LATE' THEN 1 ELSE 0 END),
        sum(CASE WHEN attendance_status='NOT_ARRIVED' THEN 1 ELSE 0 END)
      FROM appointment WHERE booking_date BETWEEN ? AND ? GROUP BY streamer_user_id
      """,rs->{Map<String,Object> row=new LinkedHashMap<>();UUID id=rs.getObject(1,UUID.class);row.put("streamerId",id);row.put("streamerName",rs.getString(2));row.put("appointments",rs.getLong(3));row.put("late",rs.getLong(4));row.put("notArrived",rs.getLong(5));row.put("modifications",0L);row.put("cancellations",0L);streamerRows.put(id,row);},start,end);
    jdbc.query("""
      SELECT a.streamer_user_id,
        sum(CASE WHEN al.action='MODIFY' THEN 1 ELSE 0 END),
        sum(CASE WHEN al.action='CANCEL' THEN 1 ELSE 0 END)
      FROM audit_log al JOIN appointment a ON a.id=al.entity_id
      WHERE al.entity_type='APPOINTMENT' AND al.action IN ('MODIFY','CANCEL') AND a.booking_date BETWEEN ? AND ?
      GROUP BY a.streamer_user_id
      """,rs->{Map<String,Object> row=streamerRows.get(rs.getObject(1,UUID.class));if(row!=null){row.put("modifications",rs.getLong(2));row.put("cancellations",rs.getLong(3));}},start,end);
    List<Map<String,Object>> streamers=new ArrayList<>(streamerRows.values());
    streamers.sort(Comparator.comparingLong((Map<String,Object> row)->number(row.get("appointments"))).reversed().thenComparing(row->String.valueOf(row.get("streamerName"))));
    return Map.of("startDate",start,"endDate",end,"summary",summary,"daily",new ArrayList<>(daily.values()),"streamers",streamers);
  }

  private long number(Object value){return value instanceof Number n?n.longValue():0;}
}
