package com.jiabei.cloud.service;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CardProjectionService {
  private static final DateTimeFormatter DATE_LABEL =
      DateTimeFormatter.ofPattern("MM-dd EEEE", Locale.CHINA);
  private static final DateTimeFormatter UPDATE_TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final JdbcTemplate jdbc;
  private final BookingProperties props;

  public CardProjectionService(JdbcTemplate jdbc, BookingProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  public CardPayload projectSchedule(LocalDate date, String group) {
    Map<String, Object> card = jdbc.queryForMap(
        "SELECT out_track_id,content_version FROM daily_card WHERE business_date=? AND group_open_conversation_id=?",
        date, group);

    List<String> lines = jdbc.query("""
        SELECT to_char(start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI')
               || '　' || streamer_name_snapshot
               || ' ' || makeup_artist_name_snapshot
               || ' · ' || team_name_snapshot
               || CASE WHEN conflict_override THEN ' ⚠冲突例外' ELSE '' END
        FROM appointment
        WHERE booking_date=? AND status='ACTIVE'
        ORDER BY start_at
        """, (rs, rowNum) -> rs.getString(1), date);

    Map<String, String> publicData = new LinkedHashMap<>();
    publicData.put("title", "加贝云·化妆预约");
    publicData.put("date_text", date.format(DATE_LABEL));
    publicData.put("update_time", LocalDateTime.now(props.zoneId()).format(UPDATE_TIME));
    publicData.put("schedule_markdown", lines.isEmpty() ? "当天暂时没有预约" : String.join("\n\n", lines));
    publicData.put("summary", String.valueOf(lines.size()));
    publicData.put("login_url", props.appEntryUrl());

    Map<String, Map<String, String>> privateData = new LinkedHashMap<>();
    jdbc.query("""
        SELECT u.dingtalk_user_id,u.role,
               to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') AS start_time,
               a.makeup_artist_name_snapshot,a.team_name_snapshot
        FROM app_user u
        LEFT JOIN LATERAL (
          SELECT start_at,makeup_artist_name_snapshot,team_name_snapshot
          FROM appointment
          WHERE streamer_user_id=u.id AND booking_date=? AND status='ACTIVE'
          ORDER BY start_at
          LIMIT 1
        ) a ON true
        WHERE u.is_active AND u.dingtalk_user_id IS NOT NULL
        ORDER BY u.dingtalk_user_id
        """, rs -> {
          String startTime = rs.getString("start_time");
          boolean canEnterBooking = "STREAMER".equals(rs.getString("role")) && startTime == null;
          String appointment = startTime == null ? "" : startTime + "　"
              + rs.getString("makeup_artist_name_snapshot") + " · " + rs.getString("team_name_snapshot");

          // 钉钉卡片按用户覆盖按钮：仅当天未预约的主播进入预约，其余身份只查看预约。
          Map<String, String> userData = new LinkedHashMap<>();
          userData.put("my_appointment", appointment);
          userData.put("login_button", canEnterBooking ? "进入预约" : "查看预约");
          userData.put("login_button_color", canEnterBooking ? "gold" : "blue");
          privateData.put(rs.getString("dingtalk_user_id"), userData);
        }, date);

    return new CardPayload(
        (String) card.get("out_track_id"),
        group,
        props.cardTemplateId(),
        date,
        publicData,
        privateData,
        ((Number) card.get("content_version")).longValue());
  }

  public CardPayload projectLate(UUID appointmentId) {
    Map<String, Object> row = jdbc.queryForMap("""
        SELECT n.out_track_id,n.dingtalk_user_id,a.booking_date,
               to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') start_time,
               a.streamer_name_snapshot,a.makeup_artist_name_snapshot,a.team_name_snapshot
        FROM late_notification n
        JOIN appointment a ON a.id=n.appointment_id
        WHERE n.appointment_id=?
        """, appointmentId);
    String dingTalkUserId = (String) row.get("dingtalk_user_id");
    String streamerName = (String) row.get("streamer_name_snapshot");
    Map<String, String> data = new LinkedHashMap<>();
    data.put("title", "化妆签到提醒");
    data.put("reminder_markdown", "<a atId=" + dingTalkUserId + ">" + streamerName
        + "</a> 您预约的化妆时间为" + row.get("start_time") + "，目前尚未检测到有效门禁记录，请尽快前往。");
    data.put("appointment_text", row.get("makeup_artist_name_snapshot") + " · " + row.get("team_name_snapshot"));
    data.put("at_user_id", dingTalkUserId);
    data.put("at_user_name", streamerName);
    data.put("entry_url", props.appEntryUrl());
    return new CardPayload(
        (String) row.get("out_track_id"),
        props.groupId(),
        props.lateCardTemplateId(),
        (LocalDate) row.get("booking_date"),
        data,
        Map.of(),
        1);
  }
}