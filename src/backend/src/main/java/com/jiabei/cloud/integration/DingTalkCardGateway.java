package com.jiabei.cloud.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@Profile({"dingtalk-test", "production"})
@ConditionalOnProperty(name = "jiabei.dingtalk.enabled", havingValue = "true")
@Component
public class DingTalkCardGateway implements CardGateway {
  private static final String TOKEN_HEADER = "x-acs-dingtalk-access-token";
  private static final ObjectMapper JSON = new ObjectMapper();
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
    String groupId = required(payload.groupId(), "群会话 ID");
    String robotCode = required(properties.getClientId(), "机器人编码");
    String atUserId = payload.cardData().get("at_user_id");
    String atUserName = atUserId == null || atUserId.isBlank()
        ? null : valueOrDefault(payload.cardData().get("at_user_name"), atUserId);
    if (atUserId != null && !atUserId.isBlank()
        && (payload.templateId() == null || payload.templateId().isBlank())) {
      sendLateReminderText(payload, groupId, robotCode, atUserId);
      return;
    }
    String templateId = required(valueOrDefault(payload.templateId(), booking.cardTemplateId()), "卡片模板 ID");
    Map<String, Object> body = commonBody(payload);
    body.put("cardTemplateId", templateId);
    body.put("openSpaceId", "dtv1.card//IM_GROUP." + groupId);
    Map<String, Object> groupSpace = new LinkedHashMap<>();
    groupSpace.put("supportForward", false);
    groupSpace.put("lastMessageI18n", Map.of(
        "ZH_CN", atUserName == null
            ? "预约安排 " + chineseWeekday(payload.businessDate())
            : "预约提醒 @" + atUserName));
    body.put("imGroupOpenSpaceModel", groupSpace);
    Map<String, Object> deliver = new LinkedHashMap<>();
    deliver.put("robotCode", robotCode);
    if (atUserId != null && !atUserId.isBlank()) {
      deliver.put("atUserIds", Map.of(atUserId, atUserName));
      body.put("cardAtUserIds", List.of(atUserId));
      String alertContent = valueOrDefault(payload.cardData().get("reminder_markdown"),
          "@" + atUserName);
      groupSpace.put("notification", Map.of(
          "notificationOff", false, "alertContent", visibleMention(alertContent, atUserId, atUserName)));
    }
    body.put("imGroupOpenDeliverModel", deliver);
    exchange("POST", "/v1.0/card/instances/createAndDeliver", body);
  }

  private void sendLateReminderText(
      CardPayload payload, String groupId, String robotCode, String atUserId) {
    String atUserName = valueOrDefault(payload.cardData().get("at_user_name"), atUserId);
    String richText = valueOrDefault(payload.cardData().get("reminder_markdown"), "@" + atUserName);
    String plainText = visibleMention(richText, atUserId, atUserName);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("robotCode", robotCode);
    body.put("openConversationId", groupId);
    body.put("msgKey", "sampleText");
    try {
      body.put("msgParam", JSON.writeValueAsString(Map.of("content", plainText)));
    } catch (JsonProcessingException error) {
      throw new CardGatewayException("DINGTALK_MESSAGE_INVALID", false, false);
    }
    exchange("POST", "/v1.0/robot/groupMessages/send", body);
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
      String responseBody = request.header(TOKEN_HEADER, client.accessToken())
          .header(HttpHeaders.CONTENT_TYPE, "application/json")
          .body(body)
          .retrieve()
          .body(String.class);
      if (path.endsWith("/createAndDeliver")) {
        validateCreateAndDeliverResponse(responseBody);
      }
    } catch (RestClientResponseException error) {
      int status = error.getStatusCode().value();
      if (status == 401) {
        client.invalidateAccessToken();
        throw new CardGatewayException("TOKEN_EXPIRED", true, false);
      }
      // 只有更新现有实例的 404 才表示卡片已删除；创建接口的 404 通常是路径或权限配置错误。
      if (status == 404 && "PUT".equals(method)) {
        throw new CardGatewayException("CARD_DELETED", false, true);
      }
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

  private static void validateCreateAndDeliverResponse(String responseBody) {
    try {
      JsonNode response = JSON.readTree(responseBody);
      JsonNode deliverResults = response.path("result").path("deliverResults");
      boolean delivered = response.path("success").asBoolean(false)
          && deliverResults.isArray()
          && !deliverResults.isEmpty();
      if (delivered) {
        for (JsonNode result : deliverResults) {
          if (!result.path("success").asBoolean(false)) {
            delivered = false;
            break;
          }
        }
      }
      if (!delivered) {
        throw new CardGatewayException("DINGTALK_DELIVERY_FAILED", true, false);
      }
    } catch (JsonProcessingException | NullPointerException error) {
      throw new CardGatewayException("DINGTALK_RESPONSE_INVALID", true, false);
    }
  }

  private static String chineseWeekday(java.time.LocalDate date) {
    return "周" + switch (date.getDayOfWeek()) {
      case MONDAY -> "一";
      case TUESDAY -> "二";
      case WEDNESDAY -> "三";
      case THURSDAY -> "四";
      case FRIDAY -> "五";
      case SATURDAY -> "六";
      case SUNDAY -> "日";
    };
  }
  private static String visibleMention(String text, String atUserId, String atUserName) {
    return text.replace(
        "<a atId=" + atUserId + ">" + atUserName + "</a>", "@" + atUserName);
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
