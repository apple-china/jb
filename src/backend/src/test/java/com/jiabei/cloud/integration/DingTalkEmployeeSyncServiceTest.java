package com.jiabei.cloud.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DingTalkEmployeeSyncServiceTest {
  @Test
  void replacesDirectorySnapshotAndDisablesDepartedAccounts() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    DingTalkEmployeeSyncService service = new DingTalkEmployeeSyncService(
        jdbc, () -> List.of(new DingTalkRemoteDirectory.Employee("u1", "张三", "union-1", List.of(1L, 2L))));

    service.synchronize();

    verify(jdbc).update(contains("UPDATE dingtalk_employee SET is_active=false"));
    verify(jdbc).update(contains("INSERT INTO dingtalk_employee"), any(), any(), any(), any());
    verify(jdbc).update(contains("UPDATE app_user u SET is_active=false"));
  }

  @Test
  void refusesAnEmptySnapshotInsteadOfDeletingEveryEmployee() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    DingTalkEmployeeSyncService service = new DingTalkEmployeeSyncService(jdbc, List::of);
    assertThatThrownBy(service::synchronize).isInstanceOf(IllegalStateException.class);
    org.mockito.Mockito.verifyNoInteractions(jdbc);
  }
}
