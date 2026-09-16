package com.jiabei.cloud.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiabei.cloud.config.BookingProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CardRefreshServiceTest {
  @Test
  void refreshesYesterdayTodayAndTomorrowAndQueuesOneJobPerExistingCard() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(startsWith("UPDATE daily_card"), any(Object[].class))).thenReturn(1);
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    BookingProperties props = new BookingProperties(
        zone, 10, 20, 20, 1, 120, 10,
        "group", "schedule", "late", "https://example.test", 5);
    CardRefreshService service = new CardRefreshService(
        jdbc, props, Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), zone));

    service.refreshExistingWindow();

    verify(jdbc, times(3)).update(startsWith("UPDATE daily_card"), any(Object[].class));
    verify(jdbc, times(3)).update(startsWith("INSERT INTO integration_job"), any(Object[].class));
  }

  @Test
  void midnightRefreshAlsoRemovesTheStaleYesterdayLabelFromThePreviousCard() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(startsWith("UPDATE daily_card"), any(Object[].class))).thenReturn(1);
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    BookingProperties props = new BookingProperties(
        zone, 10, 20, 20, 1, 120, 10,
        "group", "schedule", "late", "https://example.test", 5);
    CardRefreshService service = new CardRefreshService(
        jdbc, props, Clock.fixed(Instant.parse("2026-09-15T16:00:00Z"), zone));

    service.refreshRelativeDateLabels();

    verify(jdbc, times(4)).update(startsWith("UPDATE daily_card"), any(Object[].class));
    verify(jdbc, times(4)).update(startsWith("INSERT INTO integration_job"), any(Object[].class));
  }
}
