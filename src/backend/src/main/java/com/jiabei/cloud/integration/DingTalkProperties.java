package com.jiabei.cloud.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dingtalk-test")
@Component
@ConfigurationProperties(prefix = "jiabei.dingtalk")
public class DingTalkProperties {
  private String clientId = "";
  private String clientSecret = "";
  private String appId = "";
  private String agentId = "";
  private long syncIntervalMs = 300_000;

  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getClientSecret() { return clientSecret; }
  public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
  public String getAppId() { return appId; }
  public void setAppId(String appId) { this.appId = appId; }
  public String getAgentId() { return agentId; }
  public void setAgentId(String agentId) { this.agentId = agentId; }
  public long getSyncIntervalMs() { return syncIntervalMs; }
  public void setSyncIntervalMs(long syncIntervalMs) { this.syncIntervalMs = syncIntervalMs; }

  public void requireCredentials() {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw new IllegalStateException("DingTalk test credentials are not configured");
    }
  }
}
