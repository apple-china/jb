package com.jiabei.cloud.domain;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.web.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BookingPolicy {
  private final BookingProperties properties;
  public BookingPolicy(BookingProperties properties){this.properties=properties;}
  public ZonedDateTime start(LocalDate date,LocalTime time){return ZonedDateTime.of(LocalDateTime.of(date,time),properties.zoneId());}
  public void validateDate(LocalDate date,LocalDate today){if(!date.equals(today)&&!date.equals(today.plusDays(1)))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"BOOKING_DATE_NOT_ALLOWED","仅可操作今天或明天的预约。");}
  public void validateStep(LocalTime time){if(time.getSecond()!=0||time.getNano()!=0||time.getMinute()%properties.timeStepMinutes()!=0)throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"TIME_STEP_INVALID","请选择 10 分钟刻度的时间。");}
  public void validateLead(ZonedDateTime now,ZonedDateTime start,int minutes,String message){if(now.isAfter(start.minusMinutes(minutes)))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"MIN_LEAD_TIME_NOT_MET",message);}
  public void validateStreamerLead(ZonedDateTime now,ZonedDateTime start){validateLead(now,start,properties.streamerLeadMinutes(),"预约时间须至少提前 20 分钟。");}
  public void validateAdminLead(ZonedDateTime now,ZonedDateTime start){validateLead(now,start,properties.adminLeadMinutes(),"预约时间须至少提前 1 分钟。");}
  public void validateMakeupArtist(LocalDate date,LocalTime start,String workDays,LocalTime workStart,LocalTime workEnd,boolean scheduleEnabled,boolean active,boolean attending){
    validateStep(start);
    if(!active||!attending)throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"MAKEUP_ARTIST_UNAVAILABLE","化妆师在该日期或时间不可预约。");
    if(!scheduleEnabled)return;
    LocalTime end=start.plusMinutes(properties.serviceDurationMinutes());String day=Integer.toString(date.getDayOfWeek().getValue());boolean scheduled=Arrays.asList(workDays.split(",")).contains(day);
    if(!scheduled||start.isBefore(workStart)||end.isAfter(workEnd))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"MAKEUP_ARTIST_UNAVAILABLE","化妆师在该日期或时间不可预约。");
  }
  public String attendanceStatus(ZonedDateTime now,ZonedDateTime start,ZonedDateTime evidence){
    ZonedDateTime late=start.plusMinutes(properties.lateGraceMinutes());
    if(evidence!=null)return evidence.isBefore(late)?"ARRIVED":"LATE";
    if(now.isBefore(start))return "PENDING";
    if(now.isBefore(late))return "NOT_ARRIVED";
    return "LATE";
  }
}
