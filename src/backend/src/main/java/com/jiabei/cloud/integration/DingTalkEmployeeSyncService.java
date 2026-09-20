package com.jiabei.cloud.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.service.CardRefreshService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Replaces only a complete snapshot. Missing employees are logically deleted and linked accounts are disabled. */
@Profile({"dev", "prod", "dingtalk-test", "production"})
@ConditionalOnProperty(name = "jiabei.dingtalk.enabled", havingValue = "true")
@Service
public class DingTalkEmployeeSyncService implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DingTalkEmployeeSyncService.class);
  private final JdbcTemplate jdbc;
  private final DingTalkRemoteDirectory remote;
  private final ObjectMapper json;
  private final CardRefreshService cards;

  @Autowired
  public DingTalkEmployeeSyncService(JdbcTemplate jdbc, DingTalkRemoteDirectory remote, CardRefreshService cards) {
    this(jdbc, remote, new ObjectMapper(), cards);
  }

  DingTalkEmployeeSyncService(JdbcTemplate jdbc, DingTalkRemoteDirectory remote) {
    this(jdbc, remote, new ObjectMapper(), null);
  }

  DingTalkEmployeeSyncService(JdbcTemplate jdbc, DingTalkRemoteDirectory remote, ObjectMapper json, CardRefreshService cards) {
    this.jdbc = jdbc;
    this.remote = remote;
    this.json = json;
    this.cards = cards;
  }

  @Override public void run(ApplicationArguments args) { safeSynchronize(); }

  @Scheduled(
      initialDelayString = "${jiabei.dingtalk.sync-interval-ms:300000}",
      fixedDelayString = "${jiabei.dingtalk.sync-interval-ms:300000}")
  public void safeSynchronize() {
    try { synchronize(); }
    catch (Exception error) { log.warn("DingTalk directory synchronization failed: {}", error.getMessage()); }
  }

  public void synchronize() {
    List<DingTalkRemoteDirectory.Employee> snapshot = remote.fetchAll();
    // 企业通讯录不应为空；拒绝空快照可避免权限配置错误时误判全部员工离职。
    if (snapshot.isEmpty()) throw new IllegalStateException("DingTalk directory returned an empty snapshot");
    replaceSnapshot(snapshot);
  }

  @Transactional
  void replaceSnapshot(List<DingTalkRemoteDirectory.Employee> employees) {
    jdbc.update("UPDATE dingtalk_employee SET is_active=false,deleted_at=coalesce(deleted_at,now()),updated_at=now()");
    for (var employee : employees) {
      jdbc.update("""
          INSERT INTO dingtalk_employee(user_id,name,union_id,department_ids,is_active,deleted_at,last_synced_at)
          VALUES (?,?,?,CAST(? AS jsonb),true,NULL,now())
          ON CONFLICT(user_id) DO UPDATE SET name=excluded.name,union_id=excluded.union_id,
            department_ids=excluded.department_ids,is_active=true,deleted_at=NULL,last_synced_at=now(),updated_at=now()
          """, employee.userId(), employee.name(), employee.unionId(), departments(employee.departmentIds()));
    }
    int renamed = jdbc.update("""
        UPDATE appointment a SET streamer_name_snapshot=e.name,updated_at=now()
        FROM app_user u JOIN dingtalk_employee e ON e.user_id=u.dingtalk_user_id
        WHERE a.streamer_user_id=u.id AND u.role='STREAMER' AND e.is_active
          AND a.streamer_name_snapshot<>e.name
        """);
    jdbc.update("""
        UPDATE app_user u SET nickname=e.name,version=version+1,updated_at=now()
        FROM dingtalk_employee e
        WHERE u.dingtalk_user_id=e.user_id AND u.role='STREAMER' AND e.is_active
          AND u.nickname<>e.name
        """);
    if (renamed > 0 && cards != null) cards.refreshExistingWindow();
    jdbc.update("""
        UPDATE app_user u SET is_active=false,credential_version=credential_version+1,version=version+1,updated_at=now()
        FROM dingtalk_employee e
        WHERE u.dingtalk_user_id=e.user_id AND NOT e.is_active AND u.is_active
        """);
  }

  private String departments(List<Long> ids) {
    try { return json.writeValueAsString(ids == null ? List.of() : ids); }
    catch (JsonProcessingException error) { throw new IllegalStateException(error); }
  }
}
