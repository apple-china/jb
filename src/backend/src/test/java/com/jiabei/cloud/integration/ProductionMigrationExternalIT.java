package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="JIABEI_IT_JDBC_URL",matches=".+")
class ProductionMigrationExternalIT {
  @BeforeAll static void requireIsolatedDatabase()throws Exception{ComplexMockDataExternalIT.requireIsolatedDatabase(URL,USER,PASSWORD);}
  private static final String URL=System.getenv("JIABEI_IT_JDBC_URL"),USER=System.getenv().getOrDefault("JIABEI_IT_DATABASE_USER","jiabei"),PASSWORD=System.getenv().getOrDefault("JIABEI_IT_DATABASE_PASSWORD","test");
  @Test void productionMigrationsNeverInstallMockAccounts()throws Exception{Flyway f=Flyway.configure().locations("classpath:db/migration").cleanDisabled(false).dataSource(URL,USER,PASSWORD).load();f.clean();assertThat(f.migrate().migrationsExecuted).isEqualTo(9);try(var c=DriverManager.getConnection(URL,USER,PASSWORD);var s=c.createStatement()){try(var users=s.executeQuery("SELECT count(*) FROM app_user")){users.next();assertThat(users.getInt(1)).isZero();}try(var setting=s.executeQuery("SELECT boolean_value FROM system_setting WHERE key='SYSTEM_ENABLED'")){assertThat(setting.next()).isTrue();assertThat(setting.getBoolean(1)).isTrue();}try(var columns=s.executeQuery("SELECT column_default FROM information_schema.columns WHERE table_name='makeup_artist' AND column_name='schedule_enabled'")){assertThat(columns.next()).isTrue();assertThat(columns.getString(1)).contains("true");}try(var cardColumn=s.executeQuery("SELECT is_nullable FROM information_schema.columns WHERE table_name='daily_card' AND column_name='first_delivered_at'")){assertThat(cardColumn.next()).isTrue();assertThat(cardColumn.getString(1)).isEqualTo("YES");}try(var lateColumns=s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name='late_notification' AND column_name IN ('title_index','message_index','message_text')")){assertThat(lateColumns.next()).isTrue();assertThat(lateColumns.getInt(1)).isEqualTo(3);}}}
}
