package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiabei.cloud.web.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingServiceAttendancePermissionTest {
  private static final ZonedDateTime START = ZonedDateTime.of(
      2026, 9, 17, 12, 0, 0, 0, ZoneId.of("Asia/Shanghai"));

  @Test
  void onlyPendingAttendanceAllowsModificationOrStreamerCancellation() {
    assertThatCode(() -> requirePending("PENDING")).doesNotThrowAnyException();
    for (String status : new String[]{"ARRIVED", "NOT_ARRIVED", "LATE"}) {
      assertThatThrownBy(() -> requirePending(status))
          .isInstanceOfSatisfying(BusinessException.class,
              error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("ATTENDANCE_LOCKED"));
    }
  }

  private void requirePending(String attendanceStatus) {
    BookingService.Appointment appointment = new BookingService.Appointment(
        UUID.randomUUID(), LocalDate.of(2026, 9, 17), START,
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ACTIVE", 0, attendanceStatus);
    BookingService.requireAttendancePending(appointment);
  }
}
