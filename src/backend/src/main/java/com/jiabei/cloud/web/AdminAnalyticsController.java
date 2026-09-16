package com.jiabei.cloud.web;

import com.jiabei.cloud.service.AppointmentAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {
  private final AppointmentAnalyticsService service;
  public AdminAnalyticsController(AppointmentAnalyticsService service){this.service=service;}
  @GetMapping ApiResponse<Map<String,Object>> analytics(
    @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
    HttpServletRequest request){
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    return ApiResponse.ok(service.analytics(AuthController.current(request),startDate==null?today.minusDays(29):startDate,endDate==null?today:endDate),Trace.id(request));
  }
}
