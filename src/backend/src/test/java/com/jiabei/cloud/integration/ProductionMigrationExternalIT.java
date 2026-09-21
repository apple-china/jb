package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "JIABEI_IT_JDBC_URL", matches = ".+")
class ProductionMigrationExternalIT {
  private static final String URL = System.getenv("JIABEI_IT_JDBC_URL");
  private static final String USER = System.getenv().getOrDefault("JIABEI_IT_DATABASE_USER", "jiabei");
  private static final String PASSWORD = System.getenv().getOrDefault("JIABEI_IT_DATABASE_PASSWORD", "test");

  @BeforeAll
  static void requireIsolatedDatabase() throws Exception {
    ComplexMockDataExternalIT.requireIsolatedDatabase(URL, USER, PASSWORD);
  }

  @Test
  void productionMigrationsNeverInstallMockAccounts() throws Exception {
    Flyway flyway = Flyway.configure().locations("classpath:db/migration").cleanDisabled(false)
        .dataSource(URL, USER, PASSWORD).load();
    flyway.clean();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(18);

    try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
         var statement = connection.createStatement()) {
      try (var users = statement.executeQuery("SELECT count(*) FROM app_user")) {
        users.next();
        assertThat(users.getInt(1)).isZero();
      }
      try (var setting = statement.executeQuery("SELECT boolean_value FROM system_setting WHERE key='SYSTEM_ENABLED'")) {
        assertThat(setting.next()).isTrue();
        assertThat(setting.getBoolean(1)).isTrue();
      }
      try (var columns = statement.executeQuery("SELECT column_default FROM information_schema.columns WHERE table_name='makeup_artist' AND column_name='schedule_enabled'")) {
        assertThat(columns.next()).isTrue();
        assertThat(columns.getString(1)).contains("true");
      }
      try (var cardColumn = statement.executeQuery("SELECT is_nullable FROM information_schema.columns WHERE table_name='daily_card' AND column_name='first_delivered_at'")) {
        assertThat(cardColumn.next()).isTrue();
        assertThat(cardColumn.getString(1)).isEqualTo("YES");
      }
      try (var lateColumns = statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name='late_notification' AND column_name IN ('title_index','message_index','message_text')")) {
        lateColumns.next();
        assertThat(lateColumns.getInt(1)).isEqualTo(3);
      }
      try (var teamNo = statement.executeQuery("SELECT is_nullable,column_default FROM information_schema.columns WHERE table_name='team' AND column_name='team_no'")) {
        assertThat(teamNo.next()).isTrue();
        assertThat(teamNo.getString(1)).isEqualTo("YES");
        assertThat(teamNo.getString(2)).contains("team_no_seq");
      }
      try (var index = statement.executeQuery("SELECT count(*) FROM pg_indexes WHERE tablename='appointment' AND indexname='ix_appointment_booking_date_created'")) {
        index.next();
        assertThat(index.getInt(1)).isEqualTo(1);
      }
    }
  }
}
