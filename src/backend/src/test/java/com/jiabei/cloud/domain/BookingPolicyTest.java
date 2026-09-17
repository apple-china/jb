package com.jiabei.cloud.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.web.BusinessException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingPolicyTest {
  private BookingPolicy policy;private final ZoneId zone=ZoneId.of("Asia/Shanghai");
  @BeforeEach void setUp(){policy=new BookingPolicy(new BookingProperties(zone,10,20,20,1,120,10,"g","schedule","late","http://local",5));}
  @Test void streamerExactTwentyMinuteBoundaryIsAllowed(){ZonedDateTime now=at(12,0);assertThatCode(()->policy.validateStreamerLead(now,now.plusMinutes(20))).doesNotThrowAnyException();assertThatThrownBy(()->policy.validateStreamerLead(now,now.plusMinutes(19))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("MIN_LEAD_TIME_NOT_MET"));}
  @Test void adminExactOneMinuteBoundaryIsAllowed(){ZonedDateTime now=at(12,0);assertThatCode(()->policy.validateAdminLead(now,now.plusMinutes(1))).doesNotThrowAnyException();assertThatThrownBy(()->policy.validateAdminLead(now,now.plusSeconds(59))).isInstanceOf(BusinessException.class);}
  @Test void scheduleAndFullTwentyMinuteIntervalAreRequired(){LocalDate monday=LocalDate.of(2026,9,7);assertThatCode(()->policy.validateMakeupArtist(monday,LocalTime.of(19,40),"1,2,3,4,5",LocalTime.of(8,0),LocalTime.of(20,0),true,true,true)).doesNotThrowAnyException();assertThatThrownBy(()->policy.validateMakeupArtist(monday,LocalTime.of(19,50),"1,2,3,4,5",LocalTime.of(8,0),LocalTime.of(20,0),true,true,true)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->policy.validateMakeupArtist(monday.minusDays(1),LocalTime.NOON,"1,2,3,4,5",LocalTime.of(8,0),LocalTime.of(20,0),true,true,true)).isInstanceOf(BusinessException.class);}
  @Test void disabledScheduleAllowsAnyTenMinuteSlot(){LocalDate sunday=LocalDate.of(2026,9,6);assertThatCode(()->policy.validateMakeupArtist(sunday,LocalTime.of(23,50),"",LocalTime.of(8,0),LocalTime.of(20,0),false,true,true)).doesNotThrowAnyException();}
  @Test void attendanceBoundariesMatchConfirmedRules(){ZonedDateTime start=at(12,0);assertThat(policy.attendanceStatus(start,start,null)).isEqualTo("PENDING");assertThat(policy.attendanceStatus(start.plusMinutes(9).plusSeconds(59),start,null)).isEqualTo("PENDING");assertThat(policy.attendanceStatus(start.plusMinutes(10),start,null)).isEqualTo("NOT_ARRIVED");assertThat(policy.attendanceStatus(start.plusHours(3),start,null)).isEqualTo("NOT_ARRIVED");assertThat(policy.attendanceStatus(start.plusMinutes(10),start,start.plusMinutes(10))).isEqualTo("ARRIVED");assertThat(policy.attendanceStatus(start.plusMinutes(10).plusSeconds(1),start,start.plusMinutes(10).plusSeconds(1))).isEqualTo("LATE");}
  @Test void onlyTodayAndTomorrowAllowed(){LocalDate today=LocalDate.of(2026,9,6);assertThatCode(()->policy.validateDate(today.plusDays(1),today)).doesNotThrowAnyException();assertThatThrownBy(()->policy.validateDate(today.plusDays(2),today)).isInstanceOf(BusinessException.class);}
  @Test void rejectNonTenMinuteStep(){assertThatThrownBy(()->policy.validateStep(LocalTime.of(12,5))).isInstanceOf(BusinessException.class);}
  private ZonedDateTime at(int hour,int minute){return ZonedDateTime.of(2026,9,6,hour,minute,0,0,zone);}
}
