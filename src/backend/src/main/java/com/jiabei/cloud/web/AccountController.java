package com.jiabei.cloud.web;

import com.jiabei.cloud.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/admin/accounts")
public class AccountController {
  private final AccountService service;public AccountController(AccountService service){this.service=service;}
  @Schema(name="AccountCreateRequest",title="账号添加请求",description="使用尚未注册的钉钉身份添加账号。")
  record CreateBody(@Schema(title="钉钉用户 ID",description="钉钉唯一身份，创建后不可修改。",example="user123456") String dingTalkUserId,@Schema(title="钉钉姓名",description="钉钉通讯录姓名，创建后作为身份快照保存。",example="张三") String dingTalkUsername,@Schema(title="昵称",description="系统内显示名称，通常与钉钉姓名一致。",example="张三") String nickname,@Schema(title="角色",description="添加账号角色。",allowableValues={"OPERATOR","OBSERVER","MAKEUP","STREAMER"},example="STREAMER") String role,@Schema(title="账号启用",description="账号添加后的初始启用状态，省略时默认启用。",nullable=true,example="true") Boolean active,@Schema(title="在岗出勤",description="主播或化妆师初始出勤状态。",example="true") boolean attending,@Schema(title="允许修改预约",description="运营角色的初始修改权限；只有超管可配置。",nullable=true) Boolean canModifyAppointments,@Schema(title="允许取消预约",description="运营角色的初始取消权限；只有超管可配置。",nullable=true) Boolean canCancelAppointments,@Schema(title="允许代预约",description="运营或化妆师默认 true；其他角色忽略。",nullable=true) Boolean canCreateAppointments){}
  @Schema(name="AccountPatchRequest",title="账号修改请求",description="可空字段保持原值，version 必须为最新值。")
  record PatchBody(@Schema(title="昵称",description="修改后的系统显示名称。",nullable=true) String nickname,@Schema(title="角色",description="修改后的角色。",allowableValues={"OPERATOR","OBSERVER","MAKEUP","STREAMER"},nullable=true) String role,@Schema(title="账号启用",description="关闭后账号不能登录，并同步关联化妆师资源。",nullable=true) Boolean active,@Schema(title="在岗出勤",description="主播或化妆师出勤状态。",nullable=true) Boolean attending,@Schema(title="允许修改预约",description="运营角色的修改权限。",nullable=true) Boolean canModifyAppointments,@Schema(title="允许取消预约",description="运营角色的取消权限。",nullable=true) Boolean canCancelAppointments,@Schema(title="允许代预约",description="运营或化妆师角色的代预约权限。",nullable=true) Boolean canCreateAppointments,@Schema(title="版本号",description="账号当前 version。",example="0") int version){}
  @GetMapping ApiResponse<List<Map<String,Object>>> list(HttpServletRequest r){return ApiResponse.ok(service.list(AuthController.current(r)),Trace.id(r));}
  @PostMapping ApiResponse<Map<String,Object>> create(@RequestBody CreateBody b,HttpServletRequest r){return ApiResponse.ok(service.create(AuthController.current(r),new AccountService.Create(b.dingTalkUserId(),b.dingTalkUsername(),b.nickname(),b.role(),b.active(),b.attending(),b.canModifyAppointments(),b.canCancelAppointments(),b.canCreateAppointments()),Trace.id(r)),Trace.id(r));}
  @PatchMapping("/{id}") ApiResponse<Map<String,Object>> update(@PathVariable UUID id,@RequestBody PatchBody b,HttpServletRequest r){return ApiResponse.ok(service.update(AuthController.current(r),id,new AccountService.Patch(b.nickname(),b.role(),b.active(),b.attending(),b.canModifyAppointments(),b.canCancelAppointments(),b.canCreateAppointments(),b.version()),Trace.id(r)),Trace.id(r));}
  @PostMapping("/{id}/assign-password") ApiResponse<Map<String,String>> assignPassword(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.ok(service.assignPassword(AuthController.current(r),id,Trace.id(r)),Trace.id(r));}
  @PostMapping("/{id}/revoke-password") ApiResponse<Void> revokePassword(@PathVariable UUID id,HttpServletRequest r){service.revokePassword(AuthController.current(r),id,Trace.id(r));return ApiResponse.ok(null,Trace.id(r));}
}
