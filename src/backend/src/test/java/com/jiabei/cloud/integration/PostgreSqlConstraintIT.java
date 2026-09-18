package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.service.AppointmentAnalyticsService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlConstraintIT {
  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("jiabei").withUsername("jiabei").withPassword("test");

  @Test
  void newBaselineMigratesFromEmptyDatabase() {
    Flyway flyway = Flyway.configure().locations("classpath:db/migration", "classpath:db/local")
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(18);

    JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='auth_session' AND column_name='password_login'", Integer.class)).isEqualTo(1);

    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    jdbc.update("INSERT INTO team(id,name) VALUES (?,?)", first, "编号测试一");
    jdbc.update("INSERT INTO team(id,name) VALUES (?,?)", second, "编号测试二");
    assertThat(jdbc.queryForObject("SELECT team_no FROM team WHERE id=?", Integer.class, first)).isEqualTo(100001);
    assertThat(jdbc.queryForObject("SELECT team_no FROM team WHERE id=?", Integer.class, second)).isEqualTo(100002);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM team WHERE team_no IS NULL", Integer.class)).isPositive();

    LocalDate start = jdbc.queryForObject("SELECT min(booking_date) FROM appointment", LocalDate.class);
    LocalDate end = jdbc.queryForObject("SELECT max(booking_date) FROM appointment", LocalDate.class);
    CurrentUser admin = new CurrentUser(UUID.fromString("10000000-0000-0000-0000-000000000001"),
        "admin01", "admin01", "Admin", CurrentUser.Role.SUPER_ADMIN, null,
        true, true, true, false, "csrf");
    Map<String,Object> analytics = new AppointmentAnalyticsService(jdbc).analytics(admin, start, end);
    @SuppressWarnings("unchecked")
    Map<String,Object> summary = (Map<String,Object>)analytics.get("summary");
    assertThat(summary).containsKeys("arrived", "averageEarlyMinutes", "averageLateMinutes");
    assertThat((java.util.List<?>)analytics.get("daily")).isNotEmpty();
    assertThat((java.util.List<?>)analytics.get("makeupArtists")).isNotEmpty();
    assertThat((java.util.List<?>)analytics.get("teams")).isNotEmpty();
  }
}
