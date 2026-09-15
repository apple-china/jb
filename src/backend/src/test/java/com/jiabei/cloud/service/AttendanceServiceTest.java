package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.domain.BookingPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

class AttendanceServiceTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  @Test
  void attendanceEvidenceWindowEndsAtLateThreshold() {
    CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
    BookingProperties properties = new BookingProperties(
        ZONE, 10, 20, 20, 1, 120, 10, "group", "schedule", "late", "https://example.test", 5);
    AttendanceService service = new AttendanceService(
        jdbc, new BookingPolicy(properties), properties, Clock.system(ZONE), new ObjectMapper());
    ZonedDateTime start = ZonedDateTime.of(2026, 9, 15, 8, 30, 0, 0, ZONE);
    AttendanceService.Row appointment = new AttendanceService.Row(
        UUID.randomUUID(), start, LocalDate.of(2026, 9, 15), "ACTIVE", "PENDING", false,
        null, "ding-user-1", "玲玲");

    ReflectionTestUtils.invokeMethod(service, "firstEvidence", appointment);

    assertThat(jdbc.arguments[1]).isEqualTo(start.minusMinutes(120).toOffsetDateTime());
    assertThat(jdbc.arguments[2]).isEqualTo(start.plusMinutes(10).toOffsetDateTime());
  }

  @Test
  void queuesExistingScheduleCardAfterAttendanceChanges() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), eq(LocalDate.of(2026, 9, 15)), eq("group"))).thenReturn(1);
    BookingProperties properties = new BookingProperties(
        ZONE, 10, 20, 20, 1, 120, 10, "group", "schedule", "late", "https://example.test", 5);
    AttendanceService service = new AttendanceService(
        jdbc, new BookingPolicy(properties), properties, Clock.system(ZONE), new ObjectMapper());

    new CardRefreshService(jdbc, properties, Clock.system(ZONE)).refreshExisting(LocalDate.of(2026, 9, 15));

    verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO integration_job"),
        any(UUID.class), eq("group|2026-09-15"), eq("2026-09-15"), eq("group"), eq(5));
  }
  @Test
  void refreshesCardsWhenAnAppointmentCrossesTheTwentyMinuteBoundary() {
    SchedulerJdbcTemplate jdbc = new SchedulerJdbcTemplate();
    CardRefreshService cards = mock(CardRefreshService.class);
    BookingProperties properties = new BookingProperties(
        ZONE, 10, 20, 20, 1, 120, 10, "group", "schedule", "late", "https://example.test", 5);
    Clock clock = Clock.fixed(java.time.Instant.parse("2026-09-15T01:00:00Z"), ZONE);
    AttendanceService service = new AttendanceService(
        jdbc, new BookingPolicy(properties), properties, clock, new ObjectMapper(), cards);

    service.refreshActive();

    assertThat(jdbc.pastRefreshSql).contains("card_past_refreshed_at IS NULL")
        .contains("start_at + interval '20 minutes' <= ?");
    assertThat(jdbc.pastRefreshArguments[0]).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(jdbc.pastRefreshArguments[1]).isEqualTo(LocalDate.of(2026, 9, 16));
    verify(cards).refreshExistingWindow();
  }

  private static final class SchedulerJdbcTemplate extends JdbcTemplate {
    private String pastRefreshSql;
    private Object[] pastRefreshArguments;

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      if (sql.startsWith("SELECT id")) return List.of();
      pastRefreshSql = sql;
      pastRefreshArguments = args;
      return (List<T>) List.of(LocalDate.of(2026, 9, 15));
    }
  }

  private static final class CapturingJdbcTemplate extends JdbcTemplate {
    private Object[] arguments;

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      arguments = args;
      return List.of();
    }
  }
}
