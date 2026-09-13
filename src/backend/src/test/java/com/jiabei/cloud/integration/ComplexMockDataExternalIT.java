package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Executes the complete local dataset against a disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "JIABEI_IT_JDBC_URL", matches = ".+")
class ComplexMockDataExternalIT {
  private static final String URL = System.getenv("JIABEI_IT_JDBC_URL");
  private static final String USER = System.getenv().getOrDefault("JIABEI_IT_DATABASE_USER", "jiabei");
  private static final String PASSWORD = System.getenv().getOrDefault("JIABEI_IT_DATABASE_PASSWORD", "test");
  private static JdbcTemplate jdbc;

  @BeforeAll static void migrate() throws Exception {
    // Validate the connected database, not the URL spelling, before destructive cleanup.
    requireIsolatedDatabase(URL, USER, PASSWORD);
    Flyway flyway = Flyway.configure().locations("classpath:db/migration", "classpath:db/local")
        .cleanDisabled(false).dataSource(URL, USER, PASSWORD).load();
    flyway.clean();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(7);
    jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USER, PASSWORD));
  }

  static void requireIsolatedDatabase(String url, String user, String password) throws Exception {
    try (var connection = DriverManager.getConnection(url, user, password);
         var statement = connection.createStatement();
         var result = statement.executeQuery("SELECT current_database()")) {
      result.next();
      if (!result.getString(1).endsWith("_mock_it")) {
        throw new IllegalStateException("Complex Mock integration test requires an isolated *_mock_it database");
      }
    }
  }

  @Test void createsExactPeopleAndResourceCounts() {
    assertThat(count("SELECT count(*) FROM app_user WHERE role='SUPER_ADMIN'")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM app_user WHERE role='OPERATOR'")).isEqualTo(5);
    assertThat(count("SELECT count(*) FROM app_user WHERE role='OBSERVER'")).isEqualTo(5);
    assertThat(count("SELECT count(*) FROM app_user WHERE role='MAKEUP'")).isEqualTo(10);
    assertThat(count("SELECT count(*) FROM app_user WHERE role='STREAMER'")).isEqualTo(30);
    assertThat(count("SELECT count(*) FROM makeup_artist")).isEqualTo(10);
    assertThat(count("SELECT count(*) FROM team")).isEqualTo(10);
  }

  @Test void createsExactDynamicAppointmentDistribution() {
    assertDateDistribution(0, 100, 30, 70);
    assertDateDistribution(1, 100, 30, 70);
    assertDateDistribution(-1, 34, 18, 16);
    assertDateDistribution(-2, 33, 17, 16);
    assertDateDistribution(-3, 33, 16, 17);
    assertThat(count("SELECT count(*) FROM appointment WHERE booking_date BETWEEN (now() AT TIME ZONE 'Asia/Shanghai')::date-3 AND (now() AT TIME ZONE 'Asia/Shanghai')::date-1")).isEqualTo(100);
    assertThat(count("SELECT count(*) FROM appointment")).isEqualTo(300);
  }

  @Test void identitiesMatchDirectoryAndLeaveCandidatesUnregistered() {
    var employees = new MockDingTalkDirectoryGateway().employees("");
    assertThat(count("SELECT count(*) FROM app_user WHERE dingtalk_user_id IS NOT NULL")).isEqualTo(41);
    for (var employee : employees) {
      boolean candidate = employee.userId().startsWith("candidate") || employee.userId().startsWith("user");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE dingtalk_user_id=? AND dingtalk_username=?",
          Integer.class, employee.userId(), employee.username())).isEqualTo(candidate ? 0 : 1);
    }
    assertThat(count("SELECT count(*) FROM app_user WHERE username ~ '^streamer(2[1-9]|30)$' AND dingtalk_user_id IS NULL AND dingtalk_username IS NULL AND password_hash IS NOT NULL")).isEqualTo(10);
    assertThat(count("SELECT count(*) FROM app_user WHERE username='superadmin' AND id='10000000-0000-0000-0000-000000000001' AND password_hash='{noop}Admin123!' AND must_change_password")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM app_user WHERE (username,id::text) IN (('operator01','10000000-0000-0000-0000-000000000002'),('observer01','10000000-0000-0000-0000-000000000003'),('makeup01','10000000-0000-0000-0000-000000000004'),('streamer01','10000000-0000-0000-0000-000000000011'),('streamer02','10000000-0000-0000-0000-000000000012'),('streamer03','10000000-0000-0000-0000-000000000013'),('streamer04','10000000-0000-0000-0000-000000000014'))")).isEqualTo(7);
  }

  @Test void variesPermissionsAndValidSchedules() {
    for (String role : List.of("OPERATOR", "MAKEUP")) {
      assertThat(count("SELECT count(DISTINCT can_create_appointments) FROM app_user WHERE role='" + role + "'")).isEqualTo(2);
    }
    assertThat(count("SELECT count(DISTINCT (work_days,work_start,work_end,schedule_enabled,is_attending,is_active)) FROM makeup_artist")).isGreaterThanOrEqualTo(6);
    assertThat(count("SELECT count(*) FROM makeup_artist WHERE work_start >= work_end OR (schedule_enabled AND work_days='')")).isZero();
    assertThat(count("SELECT count(*) FROM makeup_artist WHERE NOT schedule_enabled AND work_start IS NOT NULL AND work_end IS NOT NULL")).isPositive();
  }

  @Test void preservesBookingRulesAndSnapshotRelationships() {
    assertThat(strings("SELECT DISTINCT source FROM appointment")).containsExactlyInAnyOrder("STREAMER", "SUPER_ADMIN", "OPERATOR", "MAKEUP");
    assertThat(strings("SELECT DISTINCT attendance_status FROM appointment")).containsExactlyInAnyOrder("PENDING", "ARRIVED", "NOT_ARRIVED", "LATE");
    assertThat(count("SELECT count(*) FROM appointment WHERE end_at-start_at<>interval '20 minutes' OR duration_minutes<>20 OR extract(minute FROM start_at AT TIME ZONE 'Asia/Shanghai')::int%10<>0")).isZero();
    assertThat(count("SELECT count(*) FROM (SELECT streamer_user_id,booking_date FROM appointment WHERE status='ACTIVE' GROUP BY 1,2 HAVING count(*)>1) duplicates")).isZero();
    assertThat(count("SELECT count(*) FROM appointment a JOIN appointment b ON a.id<b.id AND a.makeup_artist_id=b.makeup_artist_id AND a.start_at<b.end_at AND b.start_at<a.end_at WHERE a.status='ACTIVE' AND b.status='ACTIVE'")).isZero();
    assertThat(count("SELECT count(*) FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id JOIN makeup_artist m ON m.id=a.makeup_artist_id JOIN team t ON t.id=a.team_id WHERE a.streamer_name_snapshot<>u.nickname OR a.makeup_artist_name_snapshot<>m.name OR a.team_name_snapshot<>t.name")).isZero();
    assertThat(count("SELECT count(*) FROM appointment WHERE created_by_user_id<>streamer_user_id")).isPositive();
    assertThat(count("SELECT count(*) FROM appointment WHERE conflict_override AND source<>'STREAMER'")).isPositive();
    assertThat(count("SELECT count(*) FROM appointment WHERE conflict_override AND source='STREAMER'")).isZero();
    assertThat(count("SELECT count(*) FROM appointment WHERE (status='CANCELLED')<>attendance_frozen OR created_at>updated_at OR updated_at>clock_timestamp()")).isZero();
    assertThat(count("SELECT count(*) FROM appointment WHERE status='CANCELLED' AND (cancel_reason IS NULL OR cancelled_by_user_id IS NULL OR cancelled_at IS NULL OR cancelled_at>=updated_at)")).isZero();
    assertThat(count("SELECT count(*) FROM appointment WHERE status='ACTIVE' AND (cancel_reason IS NOT NULL OR cancelled_at IS NOT NULL OR cancelled_by_user_id IS NOT NULL)")).isZero();
    assertThat(count("SELECT count(*) FROM appointment WHERE booking_date>=(now() AT TIME ZONE 'Asia/Shanghai')::date AND attendance_status<>'PENDING'")).isZero();
  }

  @Test void containsAuditHistoryAndAttendanceEvidence() {
    assertThat(count("SELECT count(*) FROM audit_log WHERE entity_type='APPOINTMENT' AND action='CREATE'")).isEqualTo(300);
    assertThat(count("SELECT count(*) FROM audit_log WHERE entity_type='APPOINTMENT' AND action='CANCEL'")).isEqualTo(189);
    assertThat(count("SELECT count(*) FROM audit_log WHERE entity_type='APPOINTMENT' AND action='MODIFY'")).isEqualTo(40);
    assertThat(count("SELECT count(*) FROM audit_log WHERE action='MODIFY' AND (before_data IS NULL OR after_data IS NULL OR NOT before_data ?& ARRAY['startTime','makeupArtistId','teamId','conflictOverride'] OR NOT after_data ?& ARRAY['startTime','makeupArtistId','teamId','conflictOverride'] OR before_data=after_data)")).isZero();
    assertThat(strings("SELECT DISTINCT action FROM audit_log WHERE entity_type='AUTH'")).contains(
        "LOGIN_SUCCESS", "LOGIN_FAILED", "LOGIN_DENIED", "PERMISSION_DENIED");
    assertThat(count("SELECT count(*) FROM audit_log a JOIN app_user u ON u.id=a.actor_user_id WHERE a.entity_type='AUTH' AND a.action='LOGIN_DENIED' AND NOT u.is_active")).isPositive();
    assertThat(count("SELECT count(*) FROM audit_log a JOIN app_user u ON u.id=a.actor_user_id WHERE a.entity_type='AUTH' AND a.action='PERMISSION_DENIED' AND u.is_active AND NOT u.can_create_appointments AND a.after_data ?& ARRAY['method','path','code']")).isPositive();
    assertThat(count("SELECT count(*) FROM audit_log WHERE entity_type='AUTH'")).isGreaterThanOrEqualTo(12);
    assertThat(strings("SELECT DISTINCT entity_type FROM audit_log")).contains("SYSTEM", "TELEMETRY", "ERROR");
    assertThat(count("SELECT count(*) FROM audit_log WHERE entity_type IN ('SYSTEM','TELEMETRY','ERROR')")).isGreaterThanOrEqualTo(12);
    assertThat(count("SELECT count(*) FROM appointment WHERE attendance_event_id IS NOT NULL AND attendance_evidence_at IS NOT NULL")).isPositive();
    assertThat(count("SELECT count(*) FROM appointment a JOIN gate_event g ON g.id=a.attendance_event_id JOIN app_user u ON u.id=a.streamer_user_id WHERE g.dingtalk_user_id<>u.dingtalk_user_id OR a.attendance_evidence_at<>g.occurred_at")).isZero();
    assertThat(count("SELECT count(*) FROM appointment_operation_counter")).isPositive();
    assertThat(count("SELECT count(*) FROM appointment_operation_counter WHERE cancel_count NOT BETWEEN 0 AND 2 OR modify_count NOT BETWEEN 0 AND 3")).isZero();
  }

  @Test void containsDeliveryLifecycleAndFailureScenarios() {
    assertThat(strings("SELECT DISTINCT status FROM integration_job")).containsExactlyInAnyOrder("PENDING", "RUNNING", "RETRY_WAIT", "SUCCEEDED", "DEAD");
    assertThat(count("SELECT count(DISTINCT business_date) FROM daily_card")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM daily_card WHERE first_delivered_at IS NULL")).isPositive();
    assertThat(count("SELECT count(*) FROM daily_card WHERE first_delivered_at IS NOT NULL")).isPositive();
    assertThat(count("SELECT count(*) FROM daily_card WHERE group_open_conversation_id='mock-group-001'")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM mock_card_delivery WHERE card_data ?& ARRAY['title','date_text','schedule_markdown','summary','entry_url'] AND private_data ? 'streamer01'")).isPositive();
    assertThat(count("SELECT count(*) FROM integration_job j WHERE j.job_type='CARD_REFRESH' AND NOT EXISTS (SELECT 1 FROM daily_card c WHERE j.business_key=c.group_open_conversation_id || '|' || c.business_date)")).isZero();
    assertThat(count("SELECT count(*) FROM mock_card_call_log WHERE result_code='OK'")).isPositive();
    assertThat(count("SELECT count(*) FROM mock_card_call_log WHERE result_code<>'OK'")).isPositive();
    assertThat(count("SELECT count(*) FROM late_notification")).isPositive();
    assertThat(count("SELECT count(*) FROM idempotency_record")).isPositive();
    assertThat(count("SELECT count(*) FROM mock_fault_setting")).isEqualTo(6);
  }

  @Test void keepsTodayAndTomorrowCardRefreshesClaimable() {
    String cardJoin = " FROM integration_job j JOIN daily_card c ON j.job_type='CARD_REFRESH' AND j.business_key=c.group_open_conversation_id || '|' || c.business_date";
    assertThat(List.of(
        count("SELECT count(*)" + cardJoin + " WHERE c.business_date BETWEEN (now() AT TIME ZONE 'Asia/Shanghai')::date AND (now() AT TIME ZONE 'Asia/Shanghai')::date+1 AND j.status='RUNNING'"),
        count("SELECT count(*)" + cardJoin + " WHERE c.business_date=(now() AT TIME ZONE 'Asia/Shanghai')::date+1 AND j.status IN ('PENDING','RETRY_WAIT') AND j.next_attempt_at<=clock_timestamp() AND (j.locked_at IS NULL OR j.locked_at<clock_timestamp()-interval '2 minutes')")))
        .containsExactly(0, 1);
  }

  private void assertDateDistribution(int offset, int total, int active, int cancelled) {
    String where = " WHERE booking_date=(now() AT TIME ZONE 'Asia/Shanghai')::date + " + offset;
    assertThat(count("SELECT count(*) FROM appointment" + where)).isEqualTo(total);
    assertThat(count("SELECT count(*) FROM appointment" + where + " AND status='ACTIVE'")).isEqualTo(active);
    assertThat(count("SELECT count(*) FROM appointment" + where + " AND status='CANCELLED'")).isEqualTo(cancelled);
  }

  private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }
  private List<String> strings(String sql) { return jdbc.queryForList(sql, String.class); }
}
