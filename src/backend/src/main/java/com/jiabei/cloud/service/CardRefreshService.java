package com.jiabei.cloud.service;

import com.jiabei.cloud.config.BookingProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Centralizes schedule-card versioning and outbox de-duplication. */
@Service
public class CardRefreshService {
  private final JdbcTemplate jdbc;
  private final BookingProperties props;
  private final Clock clock;

  public CardRefreshService(JdbcTemplate jdbc, BookingProperties props, Clock clock) {
    this.jdbc = jdbc;
    this.props = props;
    this.clock = clock;
  }

  public void ensureAndRefreshWindow(LocalDate date, boolean allowEmpty) {
    ensureScheduleCard(date, allowEmpty);
    refreshExistingWindowExcept(date);
  }

  public void ensureScheduleCard(LocalDate date, boolean allowEmpty) {
    if (!allowEmpty) {
      Integer active = jdbc.queryForObject(
          "SELECT count(*) FROM appointment WHERE booking_date=? AND status='ACTIVE'",
          Integer.class, date);
      if (active == null || active == 0) return;
    }
    String business = props.groupId() + "|" + date;
    String out = "schedule-card-" + date + "-"
        + Integer.toHexString(Objects.hash(props.groupId(), props.cardTemplateId()));
    jdbc.update("""
        INSERT INTO daily_card(id,business_date,group_open_conversation_id,out_track_id,template_id,status,content_version)
        VALUES (?,?,?,?,?,'PENDING',1)
        ON CONFLICT(group_open_conversation_id,business_date) DO UPDATE SET
          out_track_id=CASE WHEN daily_card.template_id<>excluded.template_id THEN excluded.out_track_id ELSE daily_card.out_track_id END,
          template_id=excluded.template_id,
          status=CASE WHEN daily_card.template_id<>excluded.template_id THEN 'PENDING' ELSE daily_card.status END,
          content_version=daily_card.content_version+1,
          delivered_version=CASE WHEN daily_card.template_id<>excluded.template_id THEN 0 ELSE daily_card.delivered_version END,
          last_error_code=CASE WHEN daily_card.template_id<>excluded.template_id THEN 'TEMPLATE_CHANGED' ELSE daily_card.last_error_code END,
          updated_at=now()
        """, UUID.randomUUID(), date, props.groupId(), out, props.cardTemplateId());
    enqueue(date);
  }

  public void refreshExistingWindow() {
    refreshExistingWindowExcept(null);
  }

  public void refreshExisting(LocalDate date) {
    int updated = jdbc.update("""
        UPDATE daily_card SET content_version=content_version+1,updated_at=now()
        WHERE business_date=? AND group_open_conversation_id=?
        """, date, props.groupId());
    if (updated > 0) enqueue(date);
  }

  private void refreshExistingWindowExcept(LocalDate excluded) {
    LocalDate today = LocalDate.now(clock);
    for (int offset = -1; offset <= 1; offset++) {
      LocalDate date = today.plusDays(offset);
      if (!date.equals(excluded)) refreshExisting(date);
    }
  }

  private void enqueue(LocalDate date) {
    String key = props.groupId() + "|" + date;
    jdbc.update("""
        INSERT INTO integration_job(id,job_type,business_key,payload,status,max_attempts)
        VALUES (?,'CARD_REFRESH',?,jsonb_build_object('businessDate',?::text,'groupId',?),'PENDING',?)
        ON CONFLICT (job_type,business_key) WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')
        DO UPDATE SET payload=excluded.payload,next_attempt_at=now(),updated_at=now()
        """, UUID.randomUUID(), key, date.toString(), props.groupId(), props.outboxMaxAttempts());
  }
}
