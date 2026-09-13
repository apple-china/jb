package com.jiabei.cloud.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.web.BusinessException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Minimal DingTalk client shared by SSO and the scheduled directory reconciliation. */
@Profile("dingtalk-test")
@Component
public class DingTalkOpenApiClient implements DingTalkIdentityGateway, DingTalkRemoteDirectory {
  private static final String API = "https://api.dingtalk.com";
  private static final String OAPI = "https://oapi.dingtalk.com";
  private final DingTalkProperties properties;
  private final ObjectMapper json;
  private final RestClient http;
  private String accessToken;
  private Instant tokenExpiresAt = Instant.EPOCH;

  @Autowired
  public DingTalkOpenApiClient(DingTalkProperties properties, ObjectMapper json) {
    this(properties, json, RestClient.create());
  }

  DingTalkOpenApiClient(DingTalkProperties properties, ObjectMapper json, RestClient http) {
    this.properties = properties;
    this.json = json;
    this.http = http;
  }

  @Override
  public DingTalkUser exchangeAuthCode(String authCode) {
    JsonNode result = legacyPost("/topapi/v2/user/getuserinfo", Map.of("code", authCode)).path("result");
    String userId = text(result, "userid");
    if (userId.isBlank()) throw unavailable("钉钉免登未返回用户身份。");
    String name = text(result, "name");
    if (name.isBlank()) name = text(legacyPost("/topapi/v2/user/get", Map.of("userid", userId)).path("result"), "name");
    return new DingTalkUser(userId, name);
  }

  @Override
  public List<Employee> fetchAll() {
    Map<String, Employee> employees = new LinkedHashMap<>();
    Set<Long> visited = new LinkedHashSet<>();
    ArrayDeque<Long> departments = new ArrayDeque<>();
    departments.add(1L);
    while (!departments.isEmpty()) {
      long departmentId = departments.removeFirst();
      if (!visited.add(departmentId)) continue;
      JsonNode children = legacyPost("/topapi/v2/department/listsub", Map.of("dept_id", departmentId)).path("result");
      if (children.isArray()) children.forEach(node -> departments.add(node.path("dept_id").asLong()));
      long cursor = 0;
      do {
        JsonNode page = legacyPost("/topapi/v2/user/list", Map.of("dept_id", departmentId, "cursor", cursor, "size", 100)).path("result");
        JsonNode rows = page.path("list");
        if (rows.isArray()) rows.forEach(node -> {
          String userId = text(node, "userid");
          if (!userId.isBlank()) employees.put(userId, new Employee(userId, text(node, "name"), text(node, "unionid"), longList(node.path("dept_id_list"))));
        });
        if (!page.path("has_more").asBoolean(false)) break;
        cursor = page.path("next_cursor").asLong(cursor + 100);
      } while (true);
    }
    return List.copyOf(employees.values());
  }

  private JsonNode legacyPost(String path, Object body) {
    String url = OAPI + path + "?access_token=" + URLEncoder.encode(token(), StandardCharsets.UTF_8);
    try {
      JsonNode response = http.post().uri(url).body(body).retrieve().body(JsonNode.class);
      if (response == null || response.path("errcode").asInt(-1) != 0) {
        throw unavailable("钉钉接口返回失败：" + (response == null ? "empty" : response.path("errmsg").asText("unknown")));
      }
      return response;
    } catch (BusinessException error) {
      throw error;
    } catch (Exception error) {
      throw unavailable("钉钉服务暂时不可用。");
    }
  }

  private synchronized String token() {
    if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) return accessToken;
    properties.requireCredentials();
    try {
      JsonNode response = http.post().uri(API + "/v1.0/oauth2/accessToken")
          .body(Map.of("appKey", properties.getClientId(), "appSecret", properties.getClientSecret()))
          .retrieve().body(JsonNode.class);
      accessToken = response == null ? "" : response.path("accessToken").asText("");
      if (accessToken.isBlank()) throw unavailable("无法获取钉钉访问令牌。");
      long expiresIn = response.path("expireIn").asLong(7200);
      tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 300));
      return accessToken;
    } catch (BusinessException error) {
      throw error;
    } catch (Exception error) {
      throw unavailable("钉钉服务暂时不可用。");
    }
  }

  private static String text(JsonNode node, String field) { return node.path(field).asText("").trim(); }
  private static List<Long> longList(JsonNode node) {
    List<Long> values = new ArrayList<>();
    if (node.isArray()) node.forEach(value -> values.add(value.asLong()));
    return List.copyOf(values);
  }
  private static BusinessException unavailable(String message) {
    return new BusinessException(HttpStatus.BAD_GATEWAY, "DINGTALK_UNAVAILABLE", message);
  }
}
