package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DingTalkPropertiesTest {
  @Test
  void rejectsEnabledIntegrationWithoutCredentials() {
    DingTalkProperties properties = new DingTalkProperties();

    assertThatThrownBy(properties::validateEnabledConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DingTalk credentials");
  }

  @Test
  void acceptsEnabledIntegrationWithClientCredentials() {
    DingTalkProperties properties = new DingTalkProperties();
    properties.setClientId("client-id");
    properties.setClientSecret("client-secret");

    assertThatCode(properties::validateEnabledConfiguration).doesNotThrowAnyException();
  }
}
