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
  private static final List<String> LATE_TITLES = List.of(
      "姐姐", "小姐姐", "主播姐姐", "我的姐", "宝子", "宝儿", "我的宝");
  private static final List<String> LATE_MESSAGES = List.of(
      "化妆老师在等你呢～请尽快到司签到",
      "到化妆时间啦～老师已经准备好，请尽快到司签到喔",
      "温馨提醒：化妆预约时间已到，请尽快来签到哈",
      "该来解锁今天的漂亮造型啦～请尽快到司签到",
      "今天的精致妆容已为你预留～请尽快到司签到呢",
      "化妆预约已经开始啦～别让老师等太久哈",
      "轻轻提醒一下：化妆时间已到，请尽快到司噢～",
      "你的专属化妆时间到啦～老师正在等你哦",
      "老师已经准备啦～快来签到开启今日造型吧",
      "开播前的妆造时间到啦～请尽快来签到呢");
  private static final List<String> LATE_EMOJIS = List.of(
      "✨", "💄", "🌷", "💫", "🌸", "🌟", "💕", "💖", "🎀", "🪞",
      "🪄", "🫶", "😊", "🥰", "😘", "🤗", "🐰", "🐱", "🐣", "🙋‍♀️");

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
              !now.isBefore(start.plusMinutes(20)));
        }, date);
    int appointmentCount = appointments.size();
    appointments = withEmptyState(appointments);

    Map<String, String> publicData = new LinkedHashMap<>();
    publicData.put("data_date", date.format(DATE) + " " + chineseWeekday(date));
    publicData.put("update_time", "更新于" + LocalDateTime.now(clock).format(UPDATE_TIME));
    // 钉钉 cardParamMap 的对象数组必须以 JSON 字符串传递。
    publicData.put("appointment_list", toJson(appointments));
    publicData.put("summary", String.valueOf(appointmentCount));

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

  static List<Map<String, Object>> withEmptyState(List<Map<String, Object>> appointments) {
    if (!appointments.isEmpty()) {
      return appointments;
    }
    Map<String, Object> empty = new LinkedHashMap<>();
    empty.put("time", "");
    empty.put("streamer", "暂无预约记录");
    empty.put("status", "");
    empty.put("makeup_artist", "");
    empty.put("team", "");
    empty.put("row_light_color", PAST_LIGHT);
    empty.put("row_dark_color", PAST_DARK);
    empty.put("status_color", "gray");
    empty.put("status_visible", false);
    empty.put("status_placeholder_visible", true);
    return List.of(empty);
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
    String statusColor = switch (attendanceStatus) {
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

  static String lateReminderText(UUID appointmentId, String streamerName, String dingTalkUserId) {
    return selectLateReminder(
        appointmentId, streamerName, dingTalkUserId, List.of(), List.of()).text();
  }

  static LateReminderSelection selectLateReminder(
      UUID appointmentId,
      String streamerName,
      String dingTalkUserId,
      List<Integer> recentTitleIndexes,
      List<Integer> recentMessageIndexes) {
    int hash = appointmentId.hashCode();
    int titleIndex = availableIndex(hash, LATE_TITLES.size(), recentTitleIndexes);
    int messageIndex = availableIndex(
        Integer.rotateLeft(hash, 11), LATE_MESSAGES.size(), recentMessageIndexes);
    int emojiCount = 1 + Math.floorMod(hash, 2);
    int firstEmoji = Math.floorMod(Integer.rotateLeft(hash, 22), LATE_EMOJIS.size());
    StringBuilder emojis = new StringBuilder(LATE_EMOJIS.get(firstEmoji));
    if (emojiCount == 2) {
      int secondEmoji = Math.floorMod(Integer.rotateLeft(hash, 13), LATE_EMOJIS.size());
      if (secondEmoji == firstEmoji) {
        secondEmoji = (secondEmoji + 1) % LATE_EMOJIS.size();
      }
      emojis.append(LATE_EMOJIS.get(secondEmoji));
    }
    String text = "<a atId=" + dingTalkUserId + ">" + streamerName + "</a> "
        + LATE_TITLES.get(titleIndex) + "，" + LATE_MESSAGES.get(messageIndex)
        + " " + emojis;
    return new LateReminderSelection(titleIndex, messageIndex, emojiCount, text);
  }

  private static int availableIndex(int seed, int size, List<Integer> excluded) {
    int start = Math.floorMod(seed, size);
    for (int offset = 0; offset < size; offset++) {
      int candidate = (start + offset) % size;
      if (!excluded.contains(candidate)) {
        return candidate;
      }
    }
    return start;
  }

  record LateReminderSelection(int titleIndex, int messageIndex, int emojiCount, String text) {}
  public CardPayload projectLate(UUID appointmentId) {
    Map<String, Object> row = jdbc.queryForMap("""
        SELECT n.out_track_id,n.dingtalk_user_id,n.message_text,a.booking_date,
               to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') start_time,
               a.streamer_name_snapshot,a.makeup_artist_name_snapshot,a.team_name_snapshot
        FROM late_notification n
        JOIN appointment a ON a.id=n.appointment_id
        WHERE n.appointment_id=?
        """, appointmentId);
    String dingTalkUserId = (String) row.get("dingtalk_user_id");
    String streamerName = (String) row.get("streamer_name_snapshot");
    LocalDate businessDate = toLocalDate(row.get("booking_date"));
    Map<String, String> data = new LinkedHashMap<>();
    String savedMessage = (String) row.get("message_text");
    String message = savedMessage == null || savedMessage.isBlank()
        ? lateReminderText(appointmentId, streamerName, dingTalkUserId)
        : savedMessage;
    String visibleMessage = message.replace(
        "<a atId=" + dingTalkUserId + ">" + streamerName + "</a>", "@" + streamerName);
    int comma = visibleMessage.indexOf('，');
    String title = comma >= 0
        ? visibleMessage.substring(("@" + streamerName).length(), comma).trim()
        : "";
    data.put("header_title", "预约提醒");
    data.put("appointment_time", (String) row.get("start_time"));
    data.put("mention_text", "@" + streamerName);
    data.put("greeting_text", title.isEmpty() ? "，" : title + "，");
    data.put("reminder_greeting", "<a atId=" + dingTalkUserId + ">" + streamerName
        + "</a> " + (title.isEmpty() ? "，" : title + "，"));
    data.put("reminder_text", comma >= 0 ? visibleMessage.substring(comma + 1) : visibleMessage);
    data.put("reminder_markdown", message);
    data.put("appointment_text",
        row.get("makeup_artist_name_snapshot") + " · " + row.get("team_name_snapshot"));
    data.put("at_user_id", dingTalkUserId);
    data.put("at_user_name", streamerName);
    data.put("entry_url", props.appEntryUrl());
    return new CardPayload(
        (String) row.get("out_track_id"),
        props.groupId(),
        props.lateCardTemplateId(),
        businessDate,
        data,
        Map.of(),
        1);
  }

  private static LocalDate toLocalDate(Object value) {
    if (value instanceof LocalDate date) return date;
    if (value instanceof java.sql.Date date) return date.toLocalDate();
    throw new IllegalArgumentException("Unsupported booking date type: " + value);
  }
}
