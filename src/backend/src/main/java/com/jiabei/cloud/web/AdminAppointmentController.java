package com.jiabei.cloud.web;

import com.jiabei.cloud.service.BookingService;
import com.jiabei.cloud.service.AppointmentRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/appointments")
public class AdminAppointmentController {
  private final BookingService service;
  private final AppointmentRecordService records;
  public AdminAppointmentController(BookingService service,AppointmentRecordService records){this.service=service;this.records=records;}
  @Schema(name="AdminAppointmentCreateRequest",title="代预约创建请求",description="管理员或有权限化妆师为主播创建预约。")
  record CreateBody(@Schema(title="预约日期",description="上海时区今天或明天。",format="date",example="2026-09-08") @NotNull LocalDate bookingDate,@Schema(title="主播用户 ID",description="目标主播的钉钉 ID 或兼容账号。",example="streamer01") @NotBlank String streamerUserId,@Schema(title="化妆师 ID",description="管理员必填；化妆师角色会强制使用自己的资源。",format="uuid",nullable=true) UUID makeupArtistId,@Schema(title="团队 ID",description="启用团队 UUID。",format="uuid") @NotNull UUID teamId,@Schema(title="开始时间",description="按 10 分钟步长选择。",type="string",format="time",example="19:00") @NotNull LocalTime startTime,@Schema(title="代预约原因",description="操作审计原因，可为空。",example="无法自行预约",nullable=true) String reason){}
  @Schema(name="AdminAppointmentModifyRequest",title="管理端预约修改请求",description="修改有效预约并记录原因。")
  record ModifyBody(@Schema(title="化妆师 ID",description="修改后的化妆师资源 UUID。",format="uuid") @NotNull UUID makeupArtistId,@Schema(title="团队 ID",description="修改后的团队 UUID。",format="uuid") @NotNull UUID teamId,@Schema(title="开始时间",description="修改后的开始时间。",type="string",format="time",example="19:00") @NotNull LocalTime startTime,@Schema(title="版本号",description="预约当前 version。",example="0") int version,@Schema(title="修改原因",description="写入审计记录的可选原因。",nullable=true) String reason,@Schema(title="预约日期",description="不可修改字段；只为兼容旧请求保留，传入即返回 BOOKING_DATE_IMMUTABLE。",deprecated=true,nullable=true,format="date") LocalDate bookingDate){}
  @Schema(name="AdminAppointmentCancelRequest",title="管理端预约取消请求",description="取消有效预约并记录原因。")
  record StatusBody(@Schema(title="版本号",description="预约当前 version。",example="0") int version,@Schema(title="取消原因",description="写入预约和审计记录的可选原因。",nullable=true) String reason){}
  @GetMapping ApiResponse<Map<String,Object>> list(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,@RequestParam(required=false) String streamer,@RequestParam(required=false) String makeupArtist,@RequestParam(required=false) String attendanceStatus,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="30") int size,HttpServletRequest request){return ApiResponse.ok(records.list(AuthController.current(request),filter(date,startDate,endDate,streamer,makeupArtist,attendanceStatus,status,page,size)),Trace.id(request));}
  @GetMapping("/options") ApiResponse<Map<String,Object>> options(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(required=false) UUID appointmentId,HttpServletRequest request){return ApiResponse.ok(service.adminBookingOptions(AuthController.current(request),date,appointmentId),Trace.id(request));}
  @GetMapping("/availability") ApiResponse<Map<String,Object>> availability(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam UUID makeupArtistId,@RequestParam(required=false) UUID appointmentId,HttpServletRequest request){return ApiResponse.ok(service.adminAvailability(AuthController.current(request),date,makeupArtistId,appointmentId),Trace.id(request));}
  @GetMapping("/export") ResponseEntity<byte[]> export(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,@RequestParam(required=false) String streamer,@RequestParam(required=false) String makeupArtist,@RequestParam(required=false) String attendanceStatus,@RequestParam(required=false) String status,HttpServletRequest request){AppointmentRecordService.Filter filter=filter(null,startDate,endDate,streamer,makeupArtist,attendanceStatus,status,1,30);byte[] body=records.export(AuthController.current(request),filter);String name="加贝云·妆造预约记录（"+filter.startDate()+"_"+filter.endDate()+"）.xlsx";String encoded=URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20");return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+encoded).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).contentLength(body.length).body(body);}
  @GetMapping("/{id}") ApiResponse<Map<String,Object>> detail(@PathVariable UUID id,HttpServletRequest request){return ApiResponse.ok(service.detail(AuthController.current(request),id),Trace.id(request));}
  @PostMapping ApiResponse<Map<String,Object>> create(@Valid @RequestBody CreateBody body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){return ApiResponse.ok(service.create(AuthController.current(request),new BookingService.CreateCommand(body.bookingDate(),body.makeupArtistId(),body.teamId(),body.startTime(),body.streamerUserId(),body.reason()),key,Trace.id(request)),Trace.id(request));}
  @PatchMapping("/{id}") ApiResponse<Map<String,Object>> modify(@PathVariable UUID id,@Valid @RequestBody ModifyBody body,HttpServletRequest request){if(body.bookingDate()!=null)throw new BusinessException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"BOOKING_DATE_IMMUTABLE","预约日期不可修改。");return ApiResponse.ok(service.adminModify(AuthController.current(request),id,new BookingService.ModifyCommand(body.makeupArtistId(),body.teamId(),body.startTime(),body.version(),body.reason()),Trace.id(request)),Trace.id(request));}
  @PostMapping("/{id}/cancel") ApiResponse<Map<String,Object>> cancel(@PathVariable UUID id,@Valid @RequestBody StatusBody body,HttpServletRequest request){return ApiResponse.ok(service.adminCancel(AuthController.current(request),id,new BookingService.StatusCommand(body.version(),body.reason()),Trace.id(request)),Trace.id(request));}
  @Schema(name="ScheduleCardRequest",title="钉钉群卡片发送请求",description="创建或刷新指定日期的群卡片任务。")
  record CardBody(@Schema(title="预约日期",description="仅允许上海时区今天或明天。",format="date",example="2026-09-08") @NotNull LocalDate bookingDate){}
  @GetMapping("/schedule-card/status") ApiResponse<Map<String,Object>> scheduleCardStatus(HttpServletRequest request){return ApiResponse.ok(service.scheduleCardStatus(AuthController.current(request)),Trace.id(request));}
  @PostMapping("/schedule-card") ApiResponse<Map<String,Object>> scheduleCard(@Valid @RequestBody CardBody body,HttpServletRequest request){return ApiResponse.ok(service.manualScheduleCard(AuthController.current(request),body.bookingDate()),Trace.id(request));}
  private AppointmentRecordService.Filter filter(LocalDate date,LocalDate start,LocalDate end,String streamer,String makeupArtist,String attendance,String status,int page,int size){LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));if(date!=null){start=date;end=date;}if(start==null)start=today.withDayOfMonth(1);if(end==null)end=today;return new AppointmentRecordService.Filter(start,end,streamer,makeupArtist,attendance,status,page,size);}
}
