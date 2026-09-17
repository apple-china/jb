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
  void attendanceEvidenceWindowIncludesBothTwoHourBoundaries() {
    CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
    BookingProperties properties = new BookingProperties(
        ZONE, 10, 20, 20, 1, 120, 10, "group", "schedule", "late", "https://example.test", 5);
    AttendanceService service = new AttendanceService(
        jdbc, new BookingPolicy(properties), properties, Clock.system(ZONE), new ObjectMapper());
    ZonedDateTime start = ZonedDateTime.of(2026, 9, 15, 8, 30, 0, 0, ZONE);
    AttendanceService.Row appointment = new AttendanceService.Row(
        UUID.randomUUID(), start, LocalDate.of(2026, 9, 15), "ACTIVE", "PENDING", false,
        null, null, "ding-user-1", "玲玲");

    ReflectionTestUtils.invokeMethod(service, "firstEvidence", appointment);

    assertThat(jdbc.arguments[1]).isEqualTo(start.minusMinutes(120).toOffsetDateTime());
    assertThat(jdbc.arguments[2]).isEqualTo(start.plusMinutes(120).toOffsetDateTime());
    assertThat(jdbc.arguments[4]).isEqualTo(appointment.id());
    assertThat(jdbc.sql).contains("bound.status='ACTIVE'")
        .contains("g.occurred_at>=?")
        .contains("g.occurred_at<=?")
        .contains("bound.id<>?")
        .contains("FOR UPDATE OF g SKIP LOCKED");
  }

  @Test
  void matchesOnlyTheClosestAppointmentByTimestampWhenAnEventIsIngested() {
    IngestJdbcTemplate jdbc = new IngestJdbcTemplate();
    BookingProperties properties = new BookingProperties(
        ZONE, 10, 20, 20, 1, 120, 10, "group", "schedule", "late", "https://example.test", 5);
    AttendanceService service = new AttendanceService(
        jdbc, new BookingPolicy(properties), properties, Clock.system(ZONE), new ObjectMapper());
    ZonedDateTime occurredAt = ZonedDateTime.of(2026, 9, 15, 19, 0, 0, 0, ZONE);

    assertThat(service.ingest("event-1", "org", "device", "ding-user-1", occurredAt, "{}", "trace")).isTrue();

    assertThat(jdbc.candidateSql).doesNotContain("booking_date")
        .contains("a.start_at>=?")
        .contains("a.start_at<=?")
        .contains("ORDER BY CASE WHEN a.start_at<=? THEN 0 ELSE 1 END")
        .contains("abs(extract(epoch")
        .contains("LIMIT 1 FOR UPDATE OF a SKIP LOCKED");
    assertThat(jdbc.candidateArguments[2]).isEqualTo(occurredAt.minusMinutes(120).toOffsetDateTime());
    assertThat(jdbc.candidateArguments[3]).isEqualTo(occurredAt.plusMinutes(120).toOffsetDateTime());
    assertThat(jdbc.candidateArguments[4]).isEqualTo(occurredAt.toOffsetDateTime());
    assertThat(jdbc.candidateArguments[5]).isEqualTo(occurredAt.toOffsetDateTime());
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

  private static final class IngestJdbcTemplate extends JdbcTemplate {
    private String candidateSql;
    private Object[] candidateArguments;

    @Override public int update(String sql, Object... args) { return 1; }
    @Override public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      candidateSql = sql;
      candidateArguments = args;
      return List.of();
    }
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

  @Test
  void duplicateGateEventIsAcknowledgedWithoutRecalculatingAppointments() {
    DuplicateJdbcTemplate jdbc = new DuplicateJdbcTemplate();
    BookingProperties properties = new BookingProperties(
        ZONE, 10, 20, 20, 1, 120, 10, "group", "schedule", "late", "https://example.test", 5);
    AttendanceService service = new AttendanceService(
        jdbc, new BookingPolicy(properties), properties, Clock.system(ZONE), new ObjectMapper());

    boolean inserted = service.ingest(
        "event-1", "org", "device", "ding-user-1",
        ZonedDateTime.of(2026, 9, 15, 8, 20, 0, 0, ZONE), "{}", "trace");

    assertThat(inserted).isFalse();
    assertThat(jdbc.queried).isFalse();
  }

  @Test
  void retainedEvidenceTimeSurvivesGateEventCleanup() {
    CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
    BookingProperties properties = new BookingProperties(
        ZONE, 10, 20, 20, 1, 120, 10, "group", "schedule", "late", "https://example.test", 5);
    AttendanceService service = new AttendanceService(
        jdbc, new BookingPolicy(properties), properties, Clock.system(ZONE), new ObjectMapper());
    ZonedDateTime start = ZonedDateTime.of(2026, 9, 15, 8, 30, 0, 0, ZONE);
    ZonedDateTime evidenceAt = start.minusMinutes(15);
    AttendanceService.Row appointment = new AttendanceService.Row(
        UUID.randomUUID(), start, LocalDate.of(2026, 9, 15), "ACTIVE", "ARRIVED", false,
        null, evidenceAt, "ding-user-1", "玲玲");

    AttendanceService.Evidence evidence =
        ReflectionTestUtils.invokeMethod(service, "firstEvidence", appointment);

    assertThat(evidence).isNotNull();
    assertThat(evidence.at()).isEqualTo(evidenceAt);
    assertThat(jdbc.arguments[1]).isEqualTo(start.minusMinutes(120).toOffsetDateTime());
    assertThat(jdbc.arguments[2]).isEqualTo(evidenceAt.minusNanos(1).toOffsetDateTime());
  }

  private static final class CapturingJdbcTemplate extends JdbcTemplate {
    private String sql;
    private Object[] arguments;

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      this.sql = sql;
      arguments = args;
      return List.of();
    }
  }

  private static final class DuplicateJdbcTemplate extends JdbcTemplate {
    private boolean queried;

    @Override
    public int update(String sql, Object... args) {
      return 0;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      queried = true;
      return List.of();
    }
  }
}
