package com.jiabei.cloud.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class CardOutboxWorkerTest {
  private JdbcTemplate jdbc;
  private CardProjectionService projection;
  private CardGateway gateway;
  private CardOutboxWorker worker;
  private UUID jobId;
  private CardPayload payload;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    projection = mock(CardProjectionService.class);
    gateway = mock(CardGateway.class);
    jobId = UUID.randomUUID();
    payload = new CardPayload("track-1", "group-1", "template-1", LocalDate.of(2026, 9, 14), Map.of(), Map.of(), 2);
    BookingProperties properties = new BookingProperties(ZoneId.of("Asia/Shanghai"), 10, 20, 20, 1, 120, 10,
        "group-1", "template-1", "late-template", "https://example.test", 5);
    worker = new CardOutboxWorker(jdbc, projection, gateway, properties, mock(TransactionTemplate.class));
    when(jdbc.queryForMap("SELECT job_type,business_key,attempt_count,max_attempts FROM integration_job WHERE id=?", jobId))
        .thenReturn(Map.of("job_type", "CARD_REFRESH", "business_key", "group-1|2026-09-14", "attempt_count", 0, "max_attempts", 5));
    when(projection.projectSchedule(LocalDate.of(2026, 9, 14), "group-1")).thenReturn(payload);
  }

  @Test
  void createsWhenDailyCardHasNeverBeenDelivered() {
    when(jdbc.queryForObject("SELECT delivered_version FROM daily_card WHERE out_track_id=?", Integer.class, "track-1"))
        .thenReturn(0);
    worker.execute(jobId);
    verify(gateway).create(payload);
    verify(gateway, never()).update(any());
    verify(jdbc, never()).queryForObject(eq("SELECT count(*) FROM mock_card_delivery WHERE out_track_id=? AND status='ACTIVE'"), eq(Integer.class), any());
  }

  @Test
  void updatesWhenDailyCardWasAlreadyDelivered() {
    when(jdbc.queryForObject("SELECT delivered_version FROM daily_card WHERE out_track_id=?", Integer.class, "track-1"))
        .thenReturn(1);
    worker.execute(jobId);
    verify(gateway).update(payload);
    verify(gateway, never()).create(any());
  }
}