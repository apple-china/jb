package com.jiabei.cloud.integration;

public interface DingTalkIdentityGateway {
  DingTalkUser exchangeAuthCode(String authCode);
  record DingTalkUser(String userId,String nickname){}
}
