package com.jiabei.cloud.web;

import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1")
public class AppointmentController {
  private final BookingService service;
  public AppointmentController(BookingService service){this.service=service;}
  @Schema(name="SelfAppointmentCreateRequest",title="本人预约创建请求",description="主播为本人创建预约。")
  record CreateBody(@Schema(title="预约日期",description="上海时区今天或明天。",format="date",example="2026-09-08") @NotNull LocalDate bookingDate,@Schema(title="化妆师 ID",description="启用且出勤的化妆师资源 UUID。",format="uuid") @NotNull UUID makeupArtistId,@Schema(title="团队 ID",description="启用团队 UUID。",format="uuid") @NotNull UUID teamId,@Schema(title="开始时间",description="按 10 分钟步长选择的开始时间。",type="string",format="time",example="19:00") @NotNull LocalTime startTime,@Schema(title="主播用户 ID",description="兼容字段，服务端忽略并始终使用当前登录主播。",deprecated=true,nullable=true) String streamerUserId,@Schema(title="角色",description="兼容字段，服务端忽略。",deprecated=true,nullable=true) String role,@Schema(title="昵称",description="兼容字段，服务端忽略。",deprecated=true,nullable=true) String nickname){}
  @Schema(name="SelfAppointmentCancelRequest",title="本人预约取消请求",description="使用乐观锁版本取消预约。")
  record CancelBody(@Schema(title="版本号",description="预约当前 version，过期版本返回 VERSION_CONFLICT。",example="0") int version){}
  @Schema(name="SelfAppointmentModifyRequest",title="本人预约修改请求",description="预约日期不可修改。")
  record ModifyBody(@Schema(title="化妆师 ID",description="修改后的化妆师资源 UUID。",format="uuid") @NotNull UUID makeupArtistId,@Schema(title="团队 ID",description="修改后的团队 UUID。",format="uuid") @NotNull UUID teamId,@Schema(title="开始时间",description="修改后的开始时间。",type="string",format="time",example="19:00") @NotNull LocalTime startTime,@Schema(title="版本号",description="预约当前 version。",example="0") int version){}
  @GetMapping("/booking-context") ApiResponse<Map<String,Object>> context(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,HttpServletRequest request){return ApiResponse.ok(service.context(current(request),date),Trace.id(request));}
  @GetMapping("/availability") ApiResponse<Map<String,Object>> availability(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam UUID makeupArtistId,HttpServletRequest request){return ApiResponse.ok(service.availability(current(request),date,makeupArtistId),Trace.id(request));}
  @PostMapping("/appointments") ApiResponse<Map<String,Object>> create(@Valid @RequestBody CreateBody body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){CurrentUser actor=current(request);return ApiResponse.ok(service.create(actor,new BookingService.CreateCommand(body.bookingDate(),body.makeupArtistId(),body.teamId(),body.startTime(),actor.loginId(),""),key,Trace.id(request)),Trace.id(request));}
  @PatchMapping("/appointments/{id}") ApiResponse<Map<String,Object>> modify(@PathVariable UUID id,@Valid @RequestBody ModifyBody body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){return ApiResponse.ok(service.streamerModify(current(request),id,new BookingService.ModifyCommand(body.makeupArtistId(),body.teamId(),body.startTime(),body.version(),""),key,Trace.id(request)),Trace.id(request));}
  @PostMapping("/appointments/{id}/cancel") ApiResponse<Map<String,Object>> cancel(@PathVariable UUID id,@RequestBody CancelBody body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){return ApiResponse.ok(service.streamerCancel(current(request),id,body.version(),key,Trace.id(request)),Trace.id(request));}
  private CurrentUser current(HttpServletRequest request){return AuthController.current(request);}
}
