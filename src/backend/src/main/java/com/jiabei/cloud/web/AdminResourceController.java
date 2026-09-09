package com.jiabei.cloud.web;

import com.jiabei.cloud.service.AdminResourceService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/admin")
public class AdminResourceController {
  private final AdminResourceService service;public AdminResourceController(AdminResourceService service){this.service=service;}
  @Schema(name="SystemSettingRequest",title="系统入口修改请求",description="使用乐观锁修改预约入口。")
  record SettingBody(@Schema(title="入口启用",description="true 开放普通预约入口，false 仅保留管理端。",example="true") boolean enabled,@Schema(title="版本号",description="系统设置当前 version。",example="0") int version){}
  @Schema(name="MakeupArtistRequest",title="化妆师资源请求",description="添加和修改共用；修改时可空字段保持原值。")
  record MakeupArtistBody(@Schema(title="名称",description="化妆师显示名称，去除首尾空格后不能为空。",example="小贝老师") String name,@Schema(title="头像地址",description="上传接口返回的相对 URL；空字符串表示清除。",example="/uploads/example.png",nullable=true) String avatarUrl,@Schema(title="工作日",description="ISO 星期值数组：1 为周一，7 为周日；排班启用时至少一项。",example="[1,2,3,4,5]") List<Integer> workDays,@Schema(title="上班时间",description="排班启用时必须早于下班时间。",type="string",format="time",example="08:00") LocalTime workStart,@Schema(title="下班时间",description="排班启用时必须晚于上班时间，不支持跨午夜。",type="string",format="time",example="20:00") LocalTime workEnd,@Schema(title="排班启用",description="关闭后忽略工作日和时间限制；添加时省略默认为 true。",nullable=true) Boolean scheduleEnabled,@Schema(title="资源启用",description="关闭后化妆师不可预约；添加时省略默认为 true。",nullable=true) Boolean active,@Schema(title="出勤",description="关闭后化妆师不可预约；添加时省略默认为 true。",nullable=true) Boolean attending,@Schema(title="版本号",description="修改时传当前 version；添加时为 0。",example="0") int version,@Schema(title="原因",description="写入审计记录的可选原因。",nullable=true) String reason){}
  @Schema(name="TeamRequest",title="团队资源请求",description="添加和修改共用；修改时可空字段保持原值。")
  record TeamBody(@Schema(title="名称",description="团队显示名称，去除首尾空格后不能为空。",example="星河一团") String name,@Schema(title="图标地址",description="上传接口返回的相对 URL；空字符串表示清除。",nullable=true) String logoUrl,@Schema(title="启用",description="停用团队不可用于新预约；添加时省略默认为 true。",nullable=true) Boolean active,@Schema(title="版本号",description="修改时传当前 version；添加时为 0。",example="0") int version,@Schema(title="原因",description="写入审计记录的可选原因。",nullable=true) String reason){}
  @GetMapping("/makeup-artists") ApiResponse<List<Map<String,Object>>> makeupArtists(HttpServletRequest r){return ApiResponse.ok(service.makeupArtists(AuthController.current(r)),Trace.id(r));}
  @PostMapping("/makeup-artists") ApiResponse<Map<String,Object>> createMakeupArtist(@RequestBody MakeupArtistBody b,HttpServletRequest r){return ApiResponse.ok(service.createMakeupArtist(AuthController.current(r),new AdminResourceService.MakeupArtistCreate(b.name(),b.avatarUrl(),b.workDays(),b.workStart(),b.workEnd(),b.scheduleEnabled()==null||b.scheduleEnabled(),b.active()==null||b.active(),b.attending()==null||b.attending(),b.reason()),Trace.id(r)),Trace.id(r));}
  @PatchMapping("/makeup-artists/{id}") ApiResponse<Map<String,Object>> makeupArtist(@PathVariable UUID id,@RequestBody MakeupArtistBody b,HttpServletRequest r){return ApiResponse.ok(service.updateMakeupArtist(AuthController.current(r),id,new AdminResourceService.MakeupArtistPatch(b.name(),b.avatarUrl(),b.workDays(),b.workStart(),b.workEnd(),b.scheduleEnabled(),b.active(),b.attending(),b.version(),b.reason()),Trace.id(r)),Trace.id(r));}
  @GetMapping("/teams") ApiResponse<List<Map<String,Object>>> teams(HttpServletRequest r){return ApiResponse.ok(service.teams(),Trace.id(r));}
  @PostMapping("/teams") ApiResponse<Map<String,Object>> createTeam(@RequestBody TeamBody b,HttpServletRequest r){return ApiResponse.ok(service.createTeam(AuthController.current(r),new AdminResourceService.TeamCreate(b.name(),b.logoUrl(),b.active()==null||b.active(),b.reason()),Trace.id(r)),Trace.id(r));}
  @PatchMapping("/teams/{id}") ApiResponse<Map<String,Object>> team(@PathVariable UUID id,@RequestBody TeamBody b,HttpServletRequest r){return ApiResponse.ok(service.updateTeam(AuthController.current(r),id,new AdminResourceService.TeamPatch(b.name(),b.logoUrl(),b.active(),b.version(),b.reason()),Trace.id(r)),Trace.id(r));}
  @GetMapping("/streamers") ApiResponse<List<Map<String,Object>>> streamers(HttpServletRequest r){return ApiResponse.ok(service.streamers(),Trace.id(r));}
  @GetMapping("/system-setting") ApiResponse<Map<String,Object>> setting(HttpServletRequest r){return ApiResponse.ok(service.setting(),Trace.id(r));}
  @PatchMapping("/system-setting") ApiResponse<Map<String,Object>> setting(@RequestBody SettingBody b,HttpServletRequest r){return ApiResponse.ok(service.updateSetting(AuthController.current(r),b.enabled(),b.version(),Trace.id(r)),Trace.id(r));}
  @GetMapping("/audit-logs") ApiResponse<List<Map<String,Object>>> audits(@RequestParam String entityType,@RequestParam UUID entityId,HttpServletRequest r){return ApiResponse.ok(service.auditLogs(entityType,entityId),Trace.id(r));}
}
