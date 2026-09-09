package com.jiabei.cloud.integration;

import com.jiabei.cloud.web.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Profile("production") @Component
public class DisabledDingTalkIdentityGateway implements DingTalkIdentityGateway {
  @Override public DingTalkUser exchangeAuthCode(String authCode){throw new BusinessException(HttpStatus.BAD_GATEWAY,"DINGTALK_UNAVAILABLE","钉钉免登尚未配置，请联系管理员。");}
}
