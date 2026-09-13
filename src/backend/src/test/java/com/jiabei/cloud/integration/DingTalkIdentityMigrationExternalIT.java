package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "JIABEI_IT_JDBC_URL", matches = ".+")
class DingTalkIdentityMigrationExternalIT {
  private static final String URL = System.getenv("JIABEI_IT_JDBC_URL");
  private static final String USER = System.getenv().getOrDefault("JIABEI_IT_DATABASE_USER", "jiabei");
  private static final String PASSWORD = System.getenv().getOrDefault("JIABEI_IT_DATABASE_PASSWORD", "test");

  @Test
  void firstSuperAdminBindingUsesDirectoryNameAndThenBecomesImmutable() throws Exception {
    ComplexMockDataExternalIT.requireIsolatedDatabase(URL, USER, PASSWORD);
    var flyway = Flyway.configure()
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .dataSource(URL, USER, PASSWORD)
        .load();
    flyway.clean();
    flyway.migrate();

    try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
        var statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO dingtalk_employee(user_id, name)
          VALUES ('ding-super-admin', '测试超管')
          """);
      statement.executeUpdate("""
          INSERT INTO app_user(id, username, nickname, role)
          VALUES ('10000000-0000-0000-0000-000000000001', 'admin01', '超管', 'SUPER_ADMIN')
          """);

      statement.executeUpdate("""
          UPDATE app_user
          SET dingtalk_user_id='ding-super-admin'
          WHERE role='SUPER_ADMIN'
          """);

      try (var identity = statement.executeQuery("""
          SELECT dingtalk_user_id, dingtalk_username
          FROM app_user
          WHERE role='SUPER_ADMIN'
          """)) {
        assertThat(identity.next()).isTrue();
        assertThat(identity.getString("dingtalk_user_id")).isEqualTo("ding-super-admin");
        assertThat(identity.getString("dingtalk_username")).isEqualTo("测试超管");
      }

      assertThatThrownBy(() -> statement.executeUpdate("""
          UPDATE app_user
          SET dingtalk_user_id='another-user'
          WHERE role='SUPER_ADMIN'
          """))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("DingTalk identity is immutable");
    }
  }
}