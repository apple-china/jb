package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class GateEventMaintenanceServiceTest {
  @Test
  void detachesEvidenceLinksBeforeDeletingExpiredOrOverflowEvents() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), anyInt(), anyInt())).thenReturn(2, 25);
    GateEventMaintenanceService service = new GateEventMaintenanceService(jdbc, 180, 60000);

    service.cleanup();

    InOrder order = inOrder(jdbc);
    order.verify(jdbc).update(
        org.mockito.ArgumentMatchers.startsWith("UPDATE appointment"), eq(180), eq(60000));
    order.verify(jdbc).update(
        org.mockito.ArgumentMatchers.startsWith("DELETE FROM gate_event"), eq(180), eq(60000));
  }

  @Test
  void rejectsRetentionOutsideConfiguredSafetyBounds() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    assertThatThrownBy(() -> new GateEventMaintenanceService(jdbc, 4, 60000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GateEventMaintenanceService(jdbc, 180, 60001))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
