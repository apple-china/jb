package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.security.PasswordService;
import com.jiabei.cloud.service.AccountService;
import com.jiabei.cloud.service.AdminResourceService;
import com.jiabei.cloud.web.BusinessException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="JIABEI_IT_JDBC_URL",matches=".+")
class ExternalPostgreSqlIT {
  private static final String URL=System.getenv("JIABEI_IT_JDBC_URL"),USER=System.getenv().getOrDefault("JIABEI_IT_DATABASE_USER","jiabei"),PASSWORD=System.getenv().getOrDefault("JIABEI_IT_DATABASE_PASSWORD","test");
  @BeforeAll static void migrate()throws Exception{ComplexMockDataExternalIT.requireIsolatedDatabase(URL,USER,PASSWORD);Flyway f=Flyway.configure().locations("classpath:db/migration","classpath:db/local").cleanDisabled(false).dataSource(URL,USER,PASSWORD).load();f.clean();assertThat(f.migrate().migrationsExecuted).isEqualTo(9);}
  @Test void migrationSeedsExplicitMakeupPermissionsAndSchedules(){JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource(URL,USER,PASSWORD));assertThat(jdbc.queryForObject("SELECT can_create_appointments FROM app_user WHERE username='makeup01'",Boolean.class)).isTrue();assertThat(jdbc.queryForObject("SELECT can_create_appointments FROM app_user WHERE username='makeup02'",Boolean.class)).isFalse();assertThat(jdbc.queryForObject("SELECT count(*) FROM makeup_artist WHERE schedule_enabled",Integer.class)).isPositive();}
  @Test void cancelledAppointmentReleasesDateQualification()throws Exception{UUID s=user("释放资格");LocalDate d=LocalDate.now().plusDays(1);UUID first=insert(s,d,"12:00","20000000-0000-0000-0000-000000000001",false);updateStatus(first,"CANCELLED");insert(s,d,"13:00","20000000-0000-0000-0000-000000000002",false);assertThat(count("appointment","streamer_user_id",s)).isEqualTo(2);}
  @Test void databaseAllowsExplicitMakeupArtistConflictButKeepsStreamerUnique()throws Exception{LocalDate d=LocalDate.now().plusDays(1);UUID a=user("冲突A"),b=user("冲突B");insert(a,d,"14:00","20000000-0000-0000-0000-000000000001",false);insert(b,d,"14:10","20000000-0000-0000-0000-000000000001",true);assertThatThrownBy(()->insert(a,d,"15:00","20000000-0000-0000-0000-000000000002",false)).isInstanceOf(SQLException.class).hasMessageContaining("uq_active_appointment_streamer_date");}
  @Test void concurrentCreateHasOneWinnerForStreamerDate()throws Exception{UUID s=user("并发主播");LocalDate d=LocalDate.now().plusDays(1);assertThat(race(()->insert(s,d,"15:00","20000000-0000-0000-0000-000000000001",false),()->insert(s,d,"16:00","20000000-0000-0000-0000-000000000002",false))).isEqualTo(1);}
  @Test void operationCountersAreBoundedAndSharedAcrossRecords()throws Exception{UUID s=user("次数主播");LocalDate d=LocalDate.now().plusDays(1);try(Connection c=connection()){c.createStatement().execute("INSERT INTO appointment_operation_counter(streamer_user_id,booking_date,cancel_count,modify_count) VALUES ('"+s+"','"+d+"',2,3)");assertThatThrownBy(()->c.createStatement().execute("UPDATE appointment_operation_counter SET cancel_count=3 WHERE streamer_user_id='"+s+"' AND booking_date='"+d+"'" )).isInstanceOf(SQLException.class).hasMessageContaining("ck_operation_counts");}}
  @Test void voidStatusAndSecondSuperAdminAreRejected()throws Exception{UUID s=user("状态主播");UUID id=insert(s,LocalDate.now().plusDays(1),"17:00","20000000-0000-0000-0000-000000000001",false);assertThatThrownBy(()->updateStatus(id,"VOIDED")).isInstanceOf(SQLException.class).hasMessageContaining("ck_appointment_status");try(Connection c=connection();var p=c.prepareStatement("INSERT INTO app_user(id,username,nickname,role) VALUES (?,?,?,'SUPER_ADMIN')")){p.setObject(1,UUID.randomUUID());p.setString(2,"second-super");p.setString(3,"第二超管");assertThatThrownBy(p::executeUpdate).isInstanceOf(SQLException.class).hasMessageContaining("uq_single_super_admin");}}
  @Test void accountUsesImmutableDingTalkIdentityAndAllocatedPasswordLifecycle(){
    JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource(URL,USER,PASSWORD));PasswordService passwords=new PasswordService();
    AccountService service=new AccountService(jdbc,passwords,new ObjectMapper(),new MockDingTalkDirectoryGateway());
    CurrentUser admin=new CurrentUser(UUID.fromString("10000000-0000-0000-0000-000000000001"),"admin01","admin01","Admin",CurrentUser.Role.SUPER_ADMIN,null,true,true,true,false,"csrf");
    assertThatThrownBy(()->service.create(admin,new AccountService.Create("not-in-directory","陌生人","陌生人","STREAMER",true,true,false,false,null),"it-invalid")).isInstanceOf(BusinessException.class).hasMessageContaining("员工列表");
    Map<String,Object> created=service.create(admin,new AccountService.Create("user123456","张三","三三","STREAMER",true,true,false,false,null),"it-create");UUID id=(UUID)created.get("id");
    assertThat(created).containsEntry("dingTalkUsername","张三").containsEntry("nickname","三三").containsEntry("role","STREAMER");
    assertThatThrownBy(()->service.create(admin,new AccountService.Create("user654321","李四"," 三三 ","STREAMER",true,true,false,false,null),"it-nickname-duplicate"))
      .isInstanceOf(BusinessException.class).hasMessageContaining("昵称已存在");
    Map<String,Object> operator=service.create(admin,new AccountService.Create("user889900","王五","运营测试","OPERATOR",true,true,true,true,null),"it-operator");
    assertThat(operator).containsEntry("active",true).containsEntry("canModifyAppointments",true).containsEntry("canCancelAppointments",true).containsEntry("canCreateAppointments",true);
    CurrentUser operatorActor=new CurrentUser((UUID)operator.get("id"),"user889900","user889900","运营测试",CurrentUser.Role.OPERATOR,null,true,true,true,false,"csrf");
    assertThatThrownBy(()->service.create(operatorActor,new AccountService.Create("user776655","赵六","另一运营","OPERATOR",true,true,false,false,null),"it-operator-forbidden"))
      .isInstanceOf(BusinessException.class);
    Map<String,Object> switched=service.update(admin,id,new AccountService.Patch("三三","MAKEUP",true,true,null,null,true,0),"it-role");
    assertThat(switched).containsEntry("role","MAKEUP").containsEntry("canCreateAppointments",true);assertThat(switched.get("makeupArtistId")).isNotNull();
    UUID makeupArtistId=(UUID)switched.get("makeupArtistId");
    Map<String,Object> disabled=service.update(admin,id,new AccountService.Patch("三三","MAKEUP",false,true,null,null,true,1),"it-disable");
    assertThat(jdbc.queryForObject("SELECT is_active FROM makeup_artist WHERE id=?",Boolean.class,makeupArtistId)).isFalse();
    service.update(admin,id,new AccountService.Patch("三三","MAKEUP",true,true,null,null,true,((Number)disabled.get("version")).intValue()),"it-enable");
    assertThat(jdbc.queryForObject("SELECT is_active FROM makeup_artist WHERE id=?",Boolean.class,makeupArtistId)).isTrue();
    assertThatThrownBy(()->jdbc.update("UPDATE app_user SET dingtalk_username='李四' WHERE id=?",id)).hasMessageContaining("DingTalk identity is immutable");
    Map<String,String> credential=service.assignPassword(admin,id,"it-assign");assertThat(credential.get("username")).matches("JB\\d{10}");assertThat(credential.get("password")).isEqualTo("123456");
    Map<String,Object> stored=jdbc.queryForMap("SELECT username,password_hash,must_change_password FROM app_user WHERE id=?",id);assertThat(stored.get("username")).isEqualTo(credential.get("username"));assertThat(passwords.matches("123456",(String)stored.get("password_hash"))).isTrue();assertThat(stored.get("must_change_password")).isEqualTo(true);
    service.revokePassword(admin,id,"it-revoke");stored=jdbc.queryForMap("SELECT username,password_hash,must_change_password FROM app_user WHERE id=?",id);assertThat(stored.get("username")).isNull();assertThat(stored.get("password_hash")).isNull();assertThat(stored.get("must_change_password")).isEqualTo(false);
  }
  @Test void duplicateTeamNameReturnsSpecificBusinessMessage(){
    JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource(URL,USER,PASSWORD));
    CurrentUser admin=new CurrentUser(UUID.fromString("10000000-0000-0000-0000-000000000001"),"admin01","admin01","Admin",CurrentUser.Role.SUPER_ADMIN,null,true,true,true,false,"csrf");
    AdminResourceService service=new AdminResourceService(jdbc,new ObjectMapper());
    assertThatThrownBy(()->service.updateTeam(admin,UUID.fromString("30000000-0000-0000-0000-000000000002"),new AdminResourceService.TeamPatch("星河一团",null,true,0,null),"it-team-duplicate"))
      .isInstanceOf(BusinessException.class).hasMessageContaining("团队名称已存在");
  }
  private static UUID user(String name)throws Exception{UUID id=UUID.randomUUID();try(Connection c=connection();var p=c.prepareStatement("INSERT INTO app_user(id,username,nickname,role) VALUES (?,?,?,'STREAMER')")){p.setObject(1,id);p.setString(2,"it-"+id);p.setString(3,name);p.executeUpdate();}return id;}
  private static UUID insert(UUID streamer,LocalDate date,String time,String makeupArtist,boolean override)throws SQLException{UUID id=UUID.randomUUID();String sql="INSERT INTO appointment(id,booking_date,start_at,end_at,streamer_user_id,makeup_artist_id,team_id,streamer_name_snapshot,makeup_artist_name_snapshot,team_name_snapshot,status,source,conflict_override,created_by_user_id) VALUES (?,?,(?::date+?::time) AT TIME ZONE 'Asia/Shanghai',((?::date+?::time) AT TIME ZONE 'Asia/Shanghai')+interval '20 minutes',?,?,'30000000-0000-0000-0000-000000000001','测试主播','测试化妆师','星河一团','ACTIVE','STREAMER',?,?)";try(Connection c=connection();var p=c.prepareStatement(sql)){p.setObject(1,id);p.setObject(2,date);p.setString(3,date.toString());p.setString(4,time);p.setString(5,date.toString());p.setString(6,time);p.setObject(7,streamer);p.setObject(8,UUID.fromString(makeupArtist));p.setBoolean(9,override);p.setObject(10,streamer);p.executeUpdate();}return id;}
  private static void updateStatus(UUID id,String status)throws SQLException{try(Connection c=connection();var p=c.prepareStatement("UPDATE appointment SET status=? WHERE id=?")){p.setString(1,status);p.setObject(2,id);p.executeUpdate();}}
  private static int count(String table,String column,UUID value)throws SQLException{try(Connection c=connection();var p=c.prepareStatement("SELECT count(*) FROM "+table+" WHERE "+column+"=?")){p.setObject(1,value);try(var rs=p.executeQuery()){rs.next();return rs.getInt(1);}}}
  private static int race(SqlAction a,SqlAction b)throws Exception{var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);try{Future<Boolean>x=pool.submit(()->run(a,ready,go)),y=pool.submit(()->run(b,ready,go));ready.await();go.countDown();return(x.get()?1:0)+(y.get()?1:0);}finally{pool.shutdownNow();}}
  private static boolean run(SqlAction a,CountDownLatch ready,CountDownLatch go){ready.countDown();try{go.await();a.run();return true;}catch(Exception e){return false;}}
  private static Connection connection()throws SQLException{return DriverManager.getConnection(URL,USER,PASSWORD);}@FunctionalInterface interface SqlAction{void run()throws Exception;}
}
