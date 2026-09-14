package com.jiabei.cloud.integration;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 钉钉互动卡片真实网关，仅在 dingtalk-test 环境启用。
 *
 * <p>创建时使用“创建并投放”接口，更新时按业务键局部更新卡片数据。
 * 密钥与群、模板标识均来自运行环境，不写入代码或镜像。</p>
 */
@Profile("dingtalk-test")
@Component
public class DingTalkCardGateway implements CardGateway {
  private static final String TOKEN_HEADER = "x-acs-dingtalk-access-token";
  private final DingTalkOpenApiClient client;
  private final DingTalkProperties properties;
  private final BookingProperties booking;
  private final RestClient http;

  @Autowired
  public DingTalkCardGateway(
      DingTalkOpenApiClient client,
      DingTalkProperties properties,
      BookingProperties booking,
      RestClient.Builder builder) {
    this(client, properties, booking, builder.baseUrl("https://api.dingtalk.com").build());
  }

  DingTalkCardGateway(
      DingTalkOpenApiClient client,
      DingTalkProperties properties,
      BookingProperties booking,
      RestClient http) {
    this.client = client;
    this.properties = properties;
    this.booking = booking;
    this.http = http;
  }

  @Override
  public void create(CardPayload payload) {
    String templateId = required(valueOrDefault(payload.templateId(), booking.cardTemplateId()), "卡片模板 ID");
    String groupId = required(payload.groupId(), "群会话 ID");
    String robotCode = required(properties.getClientId(), "机器人编码");
    Map<String, Object> body = commonBody(payload);
    body.put("cardTemplateId", templateId);
    body.put("openSpaceId", "dtv1.card//IM_GROUP." + groupId);
    body.put("imGroupOpenSpaceModel", Map.of("supportForward", false));
    body.put("imGroupOpenDeliverModel", Map.of("robotCode", robotCode));
    exchange("POST", "/v1.0/card/instance/createAndDeliver", body);
  }

  @Override
  public void update(CardPayload payload) {
    Map<String, Object> body = commonBody(payload);
    body.put("cardUpdateOptions", Map.of(
        "updateCardDataByKey", true,
        "updatePrivateDataByKey", true));
    exchange("PUT", "/v1.0/card/instances", body);
  }

  private Map<String, Object> commonBody(CardPayload payload) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("outTrackId", required(payload.outTrackId(), "卡片业务键"));
    body.put("userIdType", 1);
    body.put("cardData", Map.of("cardParamMap", payload.cardData()));
    Map<String, Object> privateData = new LinkedHashMap<>();
    payload.privateData().forEach((userId, values) ->
        privateData.put(userId, Map.of("cardParamMap", values)));
    body.put("privateData", privateData);
    return body;
  }

  private void exchange(String method, String path, Map<String, Object> body) {
    try {
      RestClient.RequestBodySpec request = "POST".equals(method)
          ? http.post().uri(path)
          : http.put().uri(path);
      request.header(TOKEN_HEADER, client.accessToken())
          .header(HttpHeaders.CONTENT_TYPE, "application/json")
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException error) {
      int status = error.getStatusCode().value();
      if (status == 401) {
        client.invalidateAccessToken();
        throw new CardGatewayException("TOKEN_EXPIRED", true, false);
      }
      if (status == 404) throw new CardGatewayException("CARD_DELETED", false, true);
      if (status == 408 || status == 429 || status >= 500) {
        throw new CardGatewayException("DINGTALK_UNAVAILABLE", true, false);
      }
      throw new CardGatewayException("DINGTALK_CARD_REJECTED", false, false);
    } catch (ResourceAccessException error) {
      throw new CardGatewayException("NETWORK_TIMEOUT", true, false);
    } catch (CardGatewayException error) {
      throw error;
    } catch (RuntimeException error) {
      throw new CardGatewayException("DINGTALK_UNAVAILABLE", true, false);
    }
  }

  private static String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new CardGatewayException("DINGTALK_CARD_CONFIG_MISSING", false, false);
    }
    return value.trim();
  }
}