package com.jiabei.cloud.service;

import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.web.BusinessException;
import java.time.LocalDate;
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
        count(*) FILTER (WHERE status='ACTIVE') active,
        count(*) FILTER (WHERE status='CANCELLED') cancelled,
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='ARRIVED') arrived,
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='LATE') late,
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='NOT_ARRIVED') not_arrived,
        coalesce(round(avg(greatest(extract(epoch FROM (start_at-attendance_evidence_at))/60.0,0))
          FILTER (WHERE status='ACTIVE' AND attendance_status='ARRIVED' AND attendance_evidence_at IS NOT NULL)),0)::bigint average_early_minutes,
        coalesce(round(avg(greatest(extract(epoch FROM (attendance_evidence_at-start_at))/60.0,0))
          FILTER (WHERE status='ACTIVE' AND attendance_status='LATE' AND attendance_evidence_at IS NOT NULL)),0)::bigint average_late_minutes
      FROM appointment WHERE booking_date BETWEEN ? AND ?
      """,start,end);
    Map<String,Object> summary=new LinkedHashMap<>();
    summary.put("total",number(rawSummary.get("total")));summary.put("active",number(rawSummary.get("active")));summary.put("cancelled",number(rawSummary.get("cancelled")));
    summary.put("arrived",number(rawSummary.get("arrived")));summary.put("late",number(rawSummary.get("late")));summary.put("notArrived",number(rawSummary.get("not_arrived")));
    summary.put("averageEarlyMinutes",number(rawSummary.get("average_early_minutes")));summary.put("averageLateMinutes",number(rawSummary.get("average_late_minutes")));
    Map<String,Object> operations=jdbc.queryForMap("""
      SELECT count(*) FILTER (WHERE al.action='MODIFY') modifications,
        count(*) FILTER (WHERE al.action='CANCEL') cancellations
      FROM audit_log al JOIN appointment a ON a.id=al.entity_id
      WHERE al.entity_type='APPOINTMENT' AND al.action IN ('MODIFY','CANCEL') AND a.booking_date BETWEEN ? AND ?
      """,start,end);
    summary.put("modifications",number(operations.get("modifications")));summary.put("cancellations",number(operations.get("cancellations")));

    Map<LocalDate,Map<String,Object>> daily=new LinkedHashMap<>();
    for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1)){Map<String,Object> row=new LinkedHashMap<>();row.put("date",date);row.put("total",0);row.put("active",0);row.put("cancelled",0);row.put("arrived",0);row.put("late",0);row.put("notArrived",0);daily.put(date,row);}
    jdbc.query("""
      SELECT booking_date,count(*) total,
        count(*) FILTER (WHERE status='ACTIVE') active,
        count(*) FILTER (WHERE status='CANCELLED') cancelled,
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='ARRIVED') arrived,
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='LATE') late,
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='NOT_ARRIVED') not_arrived
      FROM appointment WHERE booking_date BETWEEN ? AND ? GROUP BY booking_date ORDER BY booking_date
      """,rs->{Map<String,Object> row=daily.get(rs.getObject(1,LocalDate.class));row.put("total",rs.getLong(2));row.put("active",rs.getLong(3));row.put("cancelled",rs.getLong(4));row.put("arrived",rs.getLong(5));row.put("late",rs.getLong(6));row.put("notArrived",rs.getLong(7));},start,end);

    Map<UUID,Map<String,Object>> streamerRows=new LinkedHashMap<>();
    jdbc.query("""
      SELECT streamer_user_id,max(streamer_name_snapshot),count(*),
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='LATE'),
        count(*) FILTER (WHERE status='ACTIVE' AND attendance_status='NOT_ARRIVED')
      FROM appointment WHERE booking_date BETWEEN ? AND ? GROUP BY streamer_user_id
      """,rs->{Map<String,Object> row=new LinkedHashMap<>();UUID id=rs.getObject(1,UUID.class);row.put("streamerId",id);row.put("streamerName",rs.getString(2));row.put("appointments",rs.getLong(3));row.put("late",rs.getLong(4));row.put("notArrived",rs.getLong(5));row.put("modifications",0L);row.put("cancellations",0L);streamerRows.put(id,row);},start,end);
    jdbc.query("""
      SELECT a.streamer_user_id,
        count(*) FILTER (WHERE al.action='MODIFY'),
        count(*) FILTER (WHERE al.action='CANCEL')
      FROM audit_log al JOIN appointment a ON a.id=al.entity_id
      WHERE al.entity_type='APPOINTMENT' AND al.action IN ('MODIFY','CANCEL') AND a.booking_date BETWEEN ? AND ?
      GROUP BY a.streamer_user_id
      """,rs->{Map<String,Object> row=streamerRows.get(rs.getObject(1,UUID.class));if(row!=null){row.put("modifications",rs.getLong(2));row.put("cancellations",rs.getLong(3));}},start,end);
    List<Map<String,Object>> streamers=new ArrayList<>(streamerRows.values());
    streamers.sort(Comparator.comparingLong((Map<String,Object> row)->number(row.get("appointments"))).reversed().thenComparing(row->String.valueOf(row.get("streamerName"))));

    List<Map<String,Object>> makeupArtists=activeResourceRanking("makeup_artist_id","makeup_artist_name_snapshot","makeupArtistId","makeupArtistName",start,end);
    List<Map<String,Object>> teams=activeResourceRanking("team_id","team_name_snapshot","teamId","teamName",start,end);
    Map<String,Object> result=new LinkedHashMap<>();result.put("startDate",start);result.put("endDate",end);result.put("summary",summary);result.put("daily",new ArrayList<>(daily.values()));result.put("streamers",streamers);result.put("makeupArtists",makeupArtists);result.put("teams",teams);return result;
  }

  private List<Map<String,Object>> activeResourceRanking(String idColumn,String nameColumn,String idKey,String nameKey,LocalDate start,LocalDate end){
    String sql="SELECT "+idColumn+",max("+nameColumn+"),count(*) FROM appointment WHERE status='ACTIVE' AND booking_date BETWEEN ? AND ? GROUP BY "+idColumn+" ORDER BY count(*) DESC,max("+nameColumn+")";
    return jdbc.query(sql,(rs,n)->{Map<String,Object> row=new LinkedHashMap<>();row.put(idKey,rs.getObject(1,UUID.class));row.put(nameKey,rs.getString(2));row.put("activeAppointments",rs.getLong(3));return row;},start,end);
  }

  private long number(Object value){return value instanceof Number n?n.longValue():0;}
}
