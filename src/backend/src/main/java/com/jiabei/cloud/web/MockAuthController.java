package com.jiabei.cloud.web;

import com.jiabei.cloud.security.SessionService;
import com.jiabei.cloud.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local","test"}) @RestController @RequestMapping("/api/v1/auth")
public class MockAuthController {
  private final SessionService sessions;private final LoginRateLimiter limiter;
  public MockAuthController(SessionService sessions,LoginRateLimiter limiter){this.sessions=sessions;this.limiter=limiter;}
  @Schema(name="MockLoginRequest",title="模拟登录请求",description="仅 Local/Test Profile 使用。")
  record LoginRequest(@Schema(title="模拟用户 ID",description="本地种子账号的钉钉 ID 或用户名。",example="admin01") @NotBlank String userId){}
  @PostMapping("/mock-login") ApiResponse<Map<String,Object>> login(@Valid @RequestBody LoginRequest body,HttpServletRequest request,HttpServletResponse response){limiter.check(request);String trace=Trace.id(request);return ApiResponse.ok(AuthController.view(sessions.loginMock(body.userId(),response,trace)),trace);}
}
