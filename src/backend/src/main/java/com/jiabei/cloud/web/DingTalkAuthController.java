package com.jiabei.cloud.web;

import com.jiabei.cloud.integration.DingTalkIdentityGateway;
import com.jiabei.cloud.security.SessionService;
import com.jiabei.cloud.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class DingTalkAuthController {
  private final DingTalkIdentityGateway gateway;private final SessionService sessions;private final LoginRateLimiter limiter;
  public DingTalkAuthController(DingTalkIdentityGateway gateway,SessionService sessions,LoginRateLimiter limiter){this.gateway=gateway;this.sessions=sessions;this.limiter=limiter;}
  @Schema(name="DingTalkLoginRequest",title="钉钉免登请求",description="钉钉客户端提供的临时授权信息。")
  record LoginRequest(@Schema(title="授权码",description="钉钉免登临时授权码，只能使用一次且有效期很短。",example="mock-auth-streamer01") @NotBlank String authCode,@Schema(title="企业 ID",description="钉钉企业 CorpId；当前网关可根据部署配置校验。",example="ding-corp-example",nullable=true) String corpId){}
  @PostMapping("/dingtalk-login") ApiResponse<Map<String,Object>> login(@Valid @RequestBody LoginRequest body,HttpServletRequest request,HttpServletResponse response){limiter.check(request);var user=gateway.exchangeAuthCode(body.authCode());String trace=Trace.id(request);return ApiResponse.ok(AuthController.view(sessions.loginByDingTalk(user.userId(),response,trace)),trace);}
}
