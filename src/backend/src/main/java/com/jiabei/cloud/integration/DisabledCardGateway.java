package com.jiabei.cloud.integration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"production","dingtalk-test"}) @Component
public class DisabledCardGateway implements CardGateway {
  @Override public void create(CardPayload payload){throw new CardGatewayException("REAL_INTEGRATION_NOT_CONFIGURED",false,false);}
  @Override public void update(CardPayload payload){throw new CardGatewayException("REAL_INTEGRATION_NOT_CONFIGURED",false,false);}
}
