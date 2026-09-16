package com.jiabei.cloud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GateEventMaintenanceService {
  private static final Logger log = LoggerFactory.getLogger(GateEventMaintenanceService.class);
  private static final String RETENTION_PREDICATE = """
      received_at < now() - make_interval(days => ?)
      OR id IN (
        SELECT id FROM gate_event
        ORDER BY received_at DESC, id DESC
        OFFSET ?
      )
      """;

  private final JdbcTemplate jdbc;
  private final int retentionDays;
  private final int maxEvents;

  public GateEventMaintenanceService(
      JdbcTemplate jdbc,
      @Value("${jiabei.moredian.retention-days:180}") int retentionDays,
      @Value("${jiabei.moredian.max-events:60000}") int maxEvents) {
    if (retentionDays < 5 || retentionDays > 180) {
      throw new IllegalArgumentException("MOREDIAN_RETENTION_DAYS must be between 5 and 180");
    }
    if (maxEvents < 1 || maxEvents > 60000) {
      throw new IllegalArgumentException("MOREDIAN_MAX_EVENTS must be between 1 and 60000");
    }
    this.jdbc = jdbc;
    this.retentionDays = retentionDays;
    this.maxEvents = maxEvents;
  }

  @Scheduled(
      initialDelayString = "${jiabei.moredian.cleanup-initial-delay-ms:300000}",
      fixedDelayString = "${jiabei.moredian.cleanup-interval-ms:604800000}")
  @Transactional
  public void cleanup() {
    int detached = jdbc.update(
        "UPDATE appointment SET attendance_event_id=NULL,updated_at=now() "
            + "WHERE attendance_event_id IN (SELECT id FROM gate_event WHERE "
            + RETENTION_PREDICATE + ")",
        retentionDays, maxEvents);
    int deleted = jdbc.update(
        "DELETE FROM gate_event WHERE " + RETENTION_PREDICATE,
        retentionDays, maxEvents);
    if (deleted > 0 || detached > 0) {
      log.info(
          "Moredian gate-event retention completed: deleted={}, detachedEvidenceLinks={}, retentionDays={}, maxEvents={}",
          deleted, detached, retentionDays, maxEvents);
    }
  }
}
