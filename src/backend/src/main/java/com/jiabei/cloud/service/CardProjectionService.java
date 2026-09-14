package com.jiabei.cloud.service;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CardProjectionService {
  private final JdbcTemplate jdbc;private final BookingProperties props;
  public CardProjectionService(JdbcTemplate jdbc,BookingProperties props){this.jdbc=jdbc;this.props=props;}
  public CardPayload projectSchedule(LocalDate date,String group){Map<String,Object> card=jdbc.queryForMap("SELECT out_track_id,content_version FROM daily_card WHERE business_date=? AND group_open_conversation_id=?",date,group);List<String> lines=jdbc.query("SELECT to_char(start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI')||'  '||streamer_name_snapshot||'  '||makeup_artist_name_snapshot||'  '||team_name_snapshot||CASE WHEN conflict_override THEN '  ⚠冲突例外' ELSE '' END FROM appointment WHERE booking_date=? AND status='ACTIVE' ORDER BY start_at",(rs,n)->rs.getString(1),date);Map<String,String> publicData=new LinkedHashMap<>();publicData.put("title","加贝云·化妆安排");publicData.put("date_text",date.toString());publicData.put("schedule_markdown",lines.isEmpty()?"当天暂时没有预约":String.join("\n\n",lines));publicData.put("summary","共"+lines.size()+"条预约 · 更新于"+LocalDateTime.now(props.zoneId()).format(DateTimeFormatter.ofPattern("HH:mm")));publicData.put("entry_url",props.appEntryUrl());Map<String,Map<String,String>> privateData=new LinkedHashMap<>();jdbc.query("SELECT u.dingtalk_user_id,to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') t,a.makeup_artist_name_snapshot,a.team_name_snapshot FROM app_user u LEFT JOIN appointment a ON a.streamer_user_id=u.id AND a.booking_date=? AND a.status='ACTIVE' WHERE u.is_active AND u.role='STREAMER' AND u.dingtalk_user_id IS NOT NULL ORDER BY u.dingtalk_user_id",rs->{String text=rs.getString("t")==null?"当天暂未预约":"我的预约\n\n"+rs.getString("t")+"  "+rs.getString("makeup_artist_name_snapshot")+"  "+rs.getString("team_name_snapshot");privateData.put(rs.getString("dingtalk_user_id"),Map.of("my_appointment",text));},date);return new CardPayload((String)card.get("out_track_id"),group,props.cardTemplateId(),date,publicData,privateData,((Number)card.get("content_version")).longValue());}
  public CardPayload projectLate(UUID appointmentId){Map<String,Object> row=jdbc.queryForMap("SELECT n.out_track_id,n.dingtalk_user_id,a.booking_date,to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') start_time,a.streamer_name_snapshot,a.makeup_artist_name_snapshot,a.team_name_snapshot FROM late_notification n JOIN appointment a ON a.id=n.appointment_id WHERE n.appointment_id=?",appointmentId);String ding=(String)row.get("dingtalk_user_id"),name=(String)row.get("streamer_name_snapshot");Map<String,String> data=new LinkedHashMap<>();data.put("title","化妆签到提醒");data.put("reminder_markdown","<a atId="+ding+">"+name+"</a> 您预约的化妆时间为"+row.get("start_time")+"，目前尚未检测到有效门禁记录，请尽快前往。");data.put("appointment_text",row.get("makeup_artist_name_snapshot")+" · "+row.get("team_name_snapshot"));data.put("at_user_id",ding);data.put("at_user_name",name);data.put("entry_url",props.appEntryUrl());return new CardPayload((String)row.get("out_track_id"),props.groupId(),props.lateCardTemplateId(),(LocalDate)row.get("booking_date"),data,Map.of(),1);}
}
