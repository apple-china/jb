package com.jiabei.cloud.integration;
import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
@Testcontainers(disabledWithoutDocker=true)
class PostgreSqlConstraintIT {
  @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("jiabei").withUsername("jiabei").withPassword("test");
  @Test void newBaselineMigratesFromEmptyDatabase(){Flyway f=Flyway.configure().locations("classpath:db/migration","classpath:db/local").dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).load();assertThat(f.migrate().migrationsExecuted).isEqualTo(15);}
}
