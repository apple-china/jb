package com.jiabei.cloud.integration;

import com.jiabei.cloud.web.BusinessException;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Profile({"local","test"}) @Component
public class MockDingTalkIdentityGateway implements DingTalkIdentityGateway {
  private final MockDingTalkTokenProvider tokens;
  private static final Map<String,DingTalkUser> USERS=Map.of(
      "mock-auth-admin01",new DingTalkUser("admin01","Admin"),
      "mock-auth-operator01",new DingTalkUser("operator01","运营一号"),
      "mock-auth-observer01",new DingTalkUser("observer01","观察员一号"),
      "mock-auth-makeup01",new DingTalkUser("makeup01","小贝老师"),
      "mock-auth-streamer01",new DingTalkUser("streamer01","玲玲"),
      "mock-auth-streamer02",new DingTalkUser("streamer02","小狼"),
      "mock-auth-streamer03",new DingTalkUser("streamer03","米粒"));
  public MockDingTalkIdentityGateway(MockDingTalkTokenProvider tokens){this.tokens=tokens;}
  @Override public DingTalkUser exchangeAuthCode(String authCode){tokens.get();if("mock-auth-expired".equals(authCode))throw new BusinessException(HttpStatus.UNAUTHORIZED,"DINGTALK_AUTH_CODE_INVALID","免登状态已失效，请重新进入。");DingTalkUser user=USERS.get(authCode);if(user==null)throw new BusinessException(HttpStatus.UNAUTHORIZED,"DINGTALK_AUTH_CODE_INVALID","免登状态已失效，请重新进入。");return user;}
}
