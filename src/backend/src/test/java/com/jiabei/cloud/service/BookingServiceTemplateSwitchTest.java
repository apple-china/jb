package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.domain.BookingPolicy;
import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.security.CurrentUser.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class BookingServiceTemplateSwitchTest {
  @Test
  void resetsTheCardInstanceWhenTheConfiguredTemplateChanges() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BookingProperties props = new BookingProperties(
        ZoneId.of("Asia/Shanghai"), 10, 20, 20, 1, 120, 10,
        "group-1", "new-template.schema", "late-template.schema",
        "https://example.test", 5);
    Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), props.zoneId());
    BookingService service = new BookingService(
        jdbc, new BookingPolicy(props), props, clock,
        new ObjectMapper().findAndRegisterModules(), mock(AttendanceService.class));
    CurrentUser admin = new CurrentUser(
        UUID.randomUUID(), "admin", null, "Admin", Role.SUPER_ADMIN,
        null, true, true, true, false, "csrf");

    service.manualScheduleCard(admin, LocalDate.of(2026, 9, 15));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(2)).update(sql.capture(), any(Object[].class));
    String upsert = sql.getAllValues().getFirst();
    assertThat(upsert)
        .contains("template_id=excluded.template_id")
        .contains("delivered_version=CASE")
        .contains("out_track_id=CASE");
  }
}