package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BookingServiceJdbcDateTest {
  @Test
  void treatsJdbcSqlDateAsTheSameAppointmentDate() {
    LocalDate bookingDate = LocalDate.of(2026, 9, 18);

    assertThat(BookingService.jdbcLocalDate(java.sql.Date.valueOf(bookingDate)))
        .isEqualTo(bookingDate);
    assertThat(BookingService.jdbcLocalDate(bookingDate))
        .isEqualTo(bookingDate);
  }
}
