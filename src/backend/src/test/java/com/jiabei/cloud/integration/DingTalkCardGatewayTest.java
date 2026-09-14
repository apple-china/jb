package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DingTalkCardGatewayTest {
  private MockRestServiceServer server;
  private DingTalkCardGateway gateway;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.dingtalk.test");
    server = MockRestServiceServer.bindTo(builder).build();
    DingTalkOpenApiClient client = mock(DingTalkOpenApiClient.class);
    when(client.accessToken()).thenReturn("access-token");
    DingTalkProperties properties = new DingTalkProperties();
    properties.setClientId("robot-code");
    BookingProperties booking = new BookingProperties(ZoneId.of("Asia/Shanghai"), 10, 20, 20, 1, 120, 10,
        "group-default", "schedule-template", "late-template", "https://jb.example/booking", 5);
    gateway = new DingTalkCardGateway(client, properties, booking, builder.build());
  }

  @Test
  void createsAndDeliversCardToConfiguredGroup() {
    server.expect(requestTo("https://api.dingtalk.test/v1.0/card/instance/createAndDeliver"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-acs-dingtalk-access-token", "access-token"))
        .andExpect(content().json("""
            {
              "cardTemplateId":"schedule-template",
              "outTrackId":"track-1",
              "openSpaceId":"dtv1.card//IM_GROUP.group-1",
              "userIdType":1,
              "cardData":{"cardParamMap":{"title":"安排"}},
              "privateData":{"user-1":{"cardParamMap":{"my_appointment":"08:00"}}},
              "imGroupOpenSpaceModel":{"supportForward":false},
              "imGroupOpenDeliverModel":{"robotCode":"robot-code"}
            }
            """, false))
        .andRespond(withSuccess());

    gateway.create(payload());
    server.verify();
  }

  @Test
  void updatesCardDataByBusinessKey() {
    server.expect(requestTo("https://api.dingtalk.test/v1.0/card/instances"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("x-acs-dingtalk-access-token", "access-token"))
        .andExpect(content().json("""
            {
              "outTrackId":"track-1",
              "userIdType":1,
              "cardData":{"cardParamMap":{"title":"安排"}},
              "privateData":{"user-1":{"cardParamMap":{"my_appointment":"08:00"}}},
              "cardUpdateOptions":{"updateCardDataByKey":true,"updatePrivateDataByKey":true}
            }
            """, false))
        .andRespond(withSuccess());

    gateway.update(payload());
    server.verify();
  }

  @Test
  void treatsMissingCardAsReplaceableInstance() {
    server.expect(requestTo("https://api.dingtalk.test/v1.0/card/instances"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
            .body("{\"code\":\"card.instance.not.exist\"}"));

    assertThatThrownBy(() -> gateway.update(payload()))
        .isInstanceOfSatisfying(CardGatewayException.class, error -> {
          org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("CARD_DELETED");
          org.assertj.core.api.Assertions.assertThat(error.unrecoverable()).isTrue();
        });
  }

  private CardPayload payload() {
    return new CardPayload("track-1", "group-1", "schedule-template", LocalDate.of(2026, 9, 14),
        Map.of("title", "安排"), Map.of("user-1", Map.of("my_appointment", "08:00")), 1);
  }
}