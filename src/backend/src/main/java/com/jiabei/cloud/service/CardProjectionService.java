package com.jiabei.cloud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CardProjectionService {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd");
  private static final DateTimeFormatter UPDATE_TIME = DateTimeFormatter.ofPattern("HH:mm");
  private static final String NORMAL_LIGHT = "#1F2329";
  private static final String NORMAL_DARK = "#F5F5F5";
  private static final String PAST_LIGHT = "#A6AAB3";
  private static final String PAST_DARK = "#7C818B";

  private final JdbcTemplate jdbc;
  private final BookingProperties props;
  private final Clock clock;
  private final ObjectMapper json;

  public CardProjectionService(
      JdbcTemplate jdbc, BookingProperties props, Clock clock, ObjectMapper json) {
    this.jdbc = jdbc;
    this.props = props;
    this.clock = clock;
    this.json = json;
  }

  public CardPayload projectSchedule(LocalDate date, String group) {
    Map<String, Object> card = jdbc.queryForMap(
        "SELECT out_track_id,content_version FROM daily_card WHERE business_date=? AND group_open_conversation_id=?",
        date, group);

    ZonedDateTime now = ZonedDateTime.now(clock);
    List<Map<String, Object>> appointments = jdbc.query("""
        SELECT to_char(start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') AS start_time,
               streamer_name_snapshot,makeup_artist_name_snapshot,team_name_snapshot,
               attendance_status,start_at
        FROM appointment
        WHERE booking_date=? AND status='ACTIVE'
        ORDER BY start_at
        """, (rs, rowNum) -> {
          ZonedDateTime start = rs.getObject("start_at", OffsetDateTime.class)
              .atZoneSameInstant(props.zoneId());
          return appointmentItem(
              rs.getString("start_time"),
              rs.getString("streamer_name_snapshot"),
              rs.getString("attendance_status"),
              rs.getString("makeup_artist_name_snapshot"),
              rs.getString("team_name_snapshot"),
              now.isAfter(start.plusMinutes(20)));
        }, date);

    Map<String, String> publicData = new LinkedHashMap<>();
    publicData.put("data_date", date.format(DATE) + " " + chineseWeekday(date));
    publicData.put("update_time", "更新于" + LocalDateTime.now(clock).format(UPDATE_TIME));
    // 钉钉 cardParamMap 的对象数组必须以 JSON 字符串传递。
    publicData.put("appointment_list", toJson(appointments));
    publicData.put("summary", String.valueOf(appointments.size()));

    Map<String, Map<String, String>> privateData = new LinkedHashMap<>();
    boolean pastDate = date.isBefore(LocalDate.now(clock));
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
          boolean hasAppointment = startTime != null;
          boolean canStartBooking = "STREAMER".equals(rs.getString("role"))
              && !hasAppointment && !pastDate;

          Map<String, String> userData = new LinkedHashMap<>();
          userData.put("my_visible", String.valueOf(hasAppointment));
          userData.put("my_time", hasAppointment ? startTime : "");
          userData.put("my_makeup_artist", hasAppointment
              ? abbreviate(rs.getString("makeup_artist_name_snapshot"), 4) : "");
          userData.put("my_team", hasAppointment
              ? abbreviate(rs.getString("team_name_snapshot"), 4) : "");
          userData.put("login_button_text", canStartBooking ? "开始预约" : "查看详情");
          userData.put("login_button_color", "gold");
          userData.put("login_button_icon",
              canStartBooking ? "icon_position" : "icon_cloud_tray_filled");
          userData.put("login_button_url", props.appEntryUrl());
          userData.put("login_button_visible", String.valueOf(!pastDate));
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

  static Map<String, Object> appointmentItem(
      String time,
      String streamer,
      String attendanceStatus,
      String makeupArtist,
      String team,
      boolean past) {
    String status = switch (attendanceStatus) {
      case "ARRIVED" -> "签到";
      case "NOT_ARRIVED" -> "未到";
      case "LATE" -> "迟到";
      default -> "";
    };
    boolean statusVisible = !status.isEmpty();
    String statusColor = past ? "gray" : switch (attendanceStatus) {
      case "ARRIVED" -> "green";
      case "NOT_ARRIVED" -> "red";
      case "LATE" -> "orange";
      default -> "blue";
    };

    Map<String, Object> item = new LinkedHashMap<>();
    item.put("time", time);
    item.put("streamer", abbreviate(streamer, 3));
    item.put("status", status);
    item.put("makeup_artist", abbreviate(makeupArtist, 4));
    item.put("team", abbreviate(team, 4));
    item.put("row_light_color", past ? PAST_LIGHT : NORMAL_LIGHT);
    item.put("row_dark_color", past ? PAST_DARK : NORMAL_DARK);
    item.put("status_color", statusColor);
    item.put("status_visible", statusVisible);
    item.put("status_placeholder_visible", !statusVisible);
    return item;
  }

  private static String abbreviate(String value, int maxCodePoints) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String text = value.trim();
    int count = text.codePointCount(0, text.length());
    if (count <= maxCodePoints) {
      return text;
    }
    int end = text.offsetByCodePoints(0, maxCodePoints);
    return text.substring(0, end) + "..";
  }

  private String toJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("无法生成钉钉卡片数据。", error);
    }
  }

  private static String chineseWeekday(LocalDate date) {
    return "周" + switch (date.getDayOfWeek()) {
      case MONDAY -> "一";
      case TUESDAY -> "二";
      case WEDNESDAY -> "三";
      case THURSDAY -> "四";
      case FRIDAY -> "五";
      case SATURDAY -> "六";
      case SUNDAY -> "日";
    };
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
        + "</a> 您预约的化妆时间为" + row.get("start_time")
        + "，目前尚未检测到有效门禁记录，请尽快前往。");
    data.put("appointment_text",
        row.get("makeup_artist_name_snapshot") + " · " + row.get("team_name_snapshot"));
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