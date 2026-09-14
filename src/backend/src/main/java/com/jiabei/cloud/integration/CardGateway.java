package com.jiabei.cloud.integration;

import java.time.LocalDate;
import java.util.Map;

public interface CardGateway {
  void create(CardPayload payload);
  void update(CardPayload payload);
  record CardPayload(String outTrackId,String groupId,String templateId,LocalDate businessDate,Map<String,String> cardData,Map<String,Map<String,String>> privateData,long contentVersion){}
}
