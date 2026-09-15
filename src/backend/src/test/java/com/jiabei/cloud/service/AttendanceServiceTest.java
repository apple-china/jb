package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;

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

  private static final class CapturingJdbcTemplate extends JdbcTemplate {
    private Object[] arguments;

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      arguments = args;
      return List.of();
    }
  }
}
