package com.jiabei.cloud.web;

import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1")
public class AuthController {
  private final SessionService sessions;
  public AuthController(SessionService sessions){this.sessions=sessions;}
  @GetMapping("/me") ApiResponse<Map<String,Object>> me(HttpServletRequest request){return ApiResponse.ok(view(current(request)),Trace.id(request));}
  @PostMapping("/logout") ApiResponse<Void> logout(HttpServletRequest request,HttpServletResponse response){sessions.logout(request,response);return ApiResponse.ok(null,Trace.id(request));}
  @Schema(name="PasswordLoginRequest",title="账号密码登录请求",description="密码登录凭据。")
  record PasswordLogin(@Schema(title="账号",description="去除首尾空格后长度为 6–100 位。",example="operator01",minLength=6,maxLength=100) @NotBlank @Size(min=6,max=100) String username,@Schema(title="密码",description="密码原文，长度为 6–100 位。",example="******",minLength=6,maxLength=100) @NotBlank @Size(min=6,max=100) String password){}
  @Schema(name="PasswordChangeRequest",title="修改密码请求",description="首次登录或主动修改密码使用。")
  record PasswordChange(@Schema(title="当前密码",description="当前有效密码。",example="******") @NotBlank String currentPassword,@Schema(title="新密码",description="长度 6–100 位且至少包含一个字母。",example="newpass",minLength=6,maxLength=100) @NotBlank @Size(min=6,max=100) String newPassword){}
  @PostMapping("/auth/password-login") ApiResponse<Map<String,Object>> passwordLogin(@Valid @RequestBody PasswordLogin body,HttpServletRequest request,HttpServletResponse response){return ApiResponse.ok(view(sessions.loginByPassword(body.username().trim(),body.password(),response,Trace.id(request))),Trace.id(request));}
  @PostMapping("/auth/change-password") ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChange body,HttpServletRequest request){sessions.changePassword(current(request),body.currentPassword(),body.newPassword(),Trace.id(request));return ApiResponse.ok(null,Trace.id(request));}
  static CurrentUser current(HttpServletRequest request){Object value=request.getAttribute("currentUser");if(value instanceof CurrentUser u)return u;throw BusinessException.unauthorized();}
  static Map<String,Object> view(CurrentUser u){Map<String,Object> m=new LinkedHashMap<>();m.put("userId",u.loginId());m.put("dingTalkUserId",u.dingTalkUserId());m.put("nickname",u.nickname());m.put("role",u.role().name());m.put("makeupArtistId",u.makeupArtistId());m.put("canModifyAppointments",u.mayModifyAppointments());m.put("canCancelAppointments",u.mayCancelAppointments());m.put("canCreateAppointments",u.mayCreateAppointments());m.put("mustChangePassword",u.mustChangePassword());m.put("csrfToken",u.csrfToken());return m;}
}
