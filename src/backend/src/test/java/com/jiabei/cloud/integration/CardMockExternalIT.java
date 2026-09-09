package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.security.PasswordService;
import com.jiabei.cloud.security.SessionService;
import com.jiabei.cloud.service.CardOutboxWorker;
import com.jiabei.cloud.service.CardProjectionService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named="JIABEI_IT_JDBC_URL",matches=".+")
class CardMockExternalIT {
  private static final String URL=System.getenv("JIABEI_IT_JDBC_URL"),USER=System.getenv().getOrDefault("JIABEI_IT_DATABASE_USER","jiabei"),PASSWORD=System.getenv().getOrDefault("JIABEI_IT_DATABASE_PASSWORD","test");
  private JdbcTemplate jdbc;private MockCardGateway gateway;private CardOutboxWorker worker;private CardProjectionService projection;
  @BeforeEach void reset(){restoreLocalData();DriverManagerDataSource ds=new DriverManagerDataSource(URL,USER,PASSWORD);jdbc=new JdbcTemplate(ds);BookingProperties props=new BookingProperties(ZoneId.of("Asia/Shanghai"),10,20,20,1,120,10,"mock-group-001","mock-schedule-template","mock-late-template","http://127.0.0.1:5173/booking",5);ObjectMapper json=new ObjectMapper().findAndRegisterModules();gateway=new MockCardGateway(jdbc,json);projection=new CardProjectionService(jdbc,props);worker=new CardOutboxWorker(jdbc,projection,gateway,props,new TransactionTemplate(new DataSourceTransactionManager(ds)));seedCard();}
  @AfterAll static void restoreAfterTests(){restoreLocalData();}
  @Test void projectionContainsOnlyBoundEnabledStreamerPrivateData(){CardPayload p=projection.projectSchedule(LocalDate.now(),"mock-group-001");assertThat(p.privateData()).containsKeys("streamer01","streamer02","streamer03").doesNotContainKeys("streamer04","admin01","operator01","observer01");}
  @Test void olderContentCannotOverwriteNewerDelivery(){LocalDate date=LocalDate.now();String out="version-guard-card";CardPayload newer=new CardPayload(out,"mock-group-001",date,Map.of("title","new"),Map.of(),2),older=new CardPayload(out,"mock-group-001",date,Map.of("title","old"),Map.of(),1);gateway.create(newer);gateway.update(older);Map<String,Object> row=jdbc.queryForMap("SELECT content_version,card_data->>'title' title FROM mock_card_delivery WHERE out_track_id=?",out);assertThat(row.get("content_version")).isEqualTo(2L);assertThat(row.get("title")).isEqualTo("new");}
  @Test void retryThenSuccessUsesStableBusinessKey(){jdbc.update("UPDATE mock_fault_setting SET enabled=true,remaining_count=1 WHERE fault_key='HTTP_5XX'");worker.poll();assertThat(jdbc.queryForObject("SELECT status FROM integration_job WHERE business_key=?",String.class,key())).isEqualTo("RETRY_WAIT");jdbc.update("UPDATE integration_job SET next_attempt_at=now() WHERE business_key=?",key());worker.poll();assertThat(jdbc.queryForObject("SELECT count(*) FROM mock_card_delivery WHERE out_track_id='schedule-card-test' AND status='ACTIVE'",Integer.class)).isEqualTo(1);}
  @Test void successfulDeliveryIsRecordedOnlyAfterGatewaySuccess(){assertThat(jdbc.queryForObject("SELECT first_delivered_at FROM daily_card WHERE out_track_id='schedule-card-test'",java.time.OffsetDateTime.class)).isNull();worker.poll();assertThat(jdbc.queryForObject("SELECT first_delivered_at FROM daily_card WHERE out_track_id='schedule-card-test'",java.time.OffsetDateTime.class)).isNotNull();}
  @Test void replacementPreservesSuccessfulDeliveryHistory(){worker.poll();java.time.OffsetDateTime first=jdbc.queryForObject("SELECT first_delivered_at FROM daily_card WHERE out_track_id='schedule-card-test'",java.time.OffsetDateTime.class);jdbc.update("INSERT INTO integration_job(id,job_type,business_key,status,max_attempts) VALUES (?,'CARD_REFRESH',?,'PENDING',5)",UUID.randomUUID(),key());jdbc.update("UPDATE daily_card SET content_version=content_version+1 WHERE out_track_id='schedule-card-test'");jdbc.update("UPDATE mock_fault_setting SET enabled=true,remaining_count=1 WHERE fault_key='CARD_DELETED'");worker.poll();Map<String,Object> row=jdbc.queryForMap("SELECT out_track_id,delivered_version FROM daily_card WHERE business_date=current_date AND group_open_conversation_id='mock-group-001'");java.time.OffsetDateTime preserved=jdbc.queryForObject("SELECT first_delivered_at FROM daily_card WHERE business_date=current_date AND group_open_conversation_id='mock-group-001'",java.time.OffsetDateTime.class);assertThat(preserved).isEqualTo(first);assertThat(row.get("out_track_id")).isNotEqualTo("schedule-card-test");assertThat(row.get("delivered_version")).isEqualTo(0L);}
  @Test void passwordChangeCommitsTogetherWithAudit(){UUID id=jdbc.queryForObject("SELECT id FROM app_user WHERE username='superadmin'",UUID.class);PasswordService passwords=new PasswordService();SessionService sessions=new SessionService(jdbc,passwords,8,false);CurrentUser actor=new CurrentUser(id,"superadmin","admin01","超级管理员",CurrentUser.Role.SUPER_ADMIN,null,true,true,true,true,"csrf");sessions.changePassword(actor,"Admin123!","Admin456789!","password-it");Map<String,Object> user=jdbc.queryForMap("SELECT password_hash,must_change_password FROM app_user WHERE id=?",id);assertThat(passwords.matches("Admin456789!",(String)user.get("password_hash"))).isTrue();assertThat(user.get("must_change_password")).isEqualTo(false);assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='PASSWORD_CHANGED' AND trace_id='password-it'",Integer.class)).isEqualTo(1);}
  private void seedCard(){jdbc.update("INSERT INTO daily_card(id,business_date,group_open_conversation_id,out_track_id,template_id,status,content_version) VALUES (?,current_date,'mock-group-001','schedule-card-test','mock-schedule-template','PENDING',1)",UUID.randomUUID());jdbc.update("INSERT INTO integration_job(id,job_type,business_key,status,max_attempts) VALUES (?,'CARD_REFRESH',?,'PENDING',5)",UUID.randomUUID(),key());}
  private String key(){return "mock-group-001|"+LocalDate.now();}
  private static void restoreLocalData(){Flyway f=Flyway.configure().locations("classpath:db/migration","classpath:db/local").cleanDisabled(false).dataSource(URL,USER,PASSWORD).load();f.clean();f.migrate();}
}
