package com.jiabei.cloud.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.integration.MoredianSignatureVerifier;
import com.jiabei.cloud.service.AttendanceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "jiabei.moredian.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/integrations/moredian")
public class MoredianRecognitionController {
  private static final Logger log = LoggerFactory.getLogger(MoredianRecognitionController.class);
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private static final int MAX_BODY_BYTES = 64 * 1024;

  private final AttendanceService attendance;
  private final ObjectMapper json;
  private final MoredianSignatureVerifier signatures;
  private final String expectedOrg;
  private final String key;
  private final String expectedDevice;
  private final boolean requireSignature;

  public MoredianRecognitionController(
      AttendanceService attendance,
      ObjectMapper json,
      MoredianSignatureVerifier signatures,
      @Value("${jiabei.moredian.org-id}") String expectedOrg,
      @Value("${jiabei.moredian.org-auth-key:}") String key,
      @Value("${jiabei.moredian.device-sn}") String expectedDevice,
      @Value("${jiabei.moredian.require-signature:true}") boolean requireSignature) {
    this.attendance = attendance;
    this.json = json;
    this.signatures = signatures;
    this.expectedOrg = expectedOrg;
    this.key = key;
    this.expectedDevice = expectedDevice;
    this.requireSignature = requireSignature;
    if (expectedOrg == null || expectedOrg.isBlank()) {
      throw new IllegalStateException("MOREDIAN_ORG_ID is required when Moredian integration is enabled");
    }
    if (expectedDevice == null || expectedDevice.isBlank()) {
      throw new IllegalStateException("MOREDIAN_DEVICE_SN is required when Moredian integration is enabled");
    }
    if (requireSignature && (key == null || key.isBlank())) {
      throw new IllegalStateException("MOREDIAN_ORG_AUTH_KEY is required when Moredian signature verification is enabled");
    }
  }

  @PostMapping("/recognition-events")
  public Map<String, String> receive(
      @RequestParam String orgId,
      @RequestParam String signVersion,
      @RequestParam String timestamp,
      @RequestParam String nonce,
      @RequestParam(required = false) String signature,
      @RequestBody String raw) {
    if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
      log.warn("Moredian callback alert: alertType=PAYLOAD_TOO_LARGE");
      throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "回调内容过大。");
    }
    if (!expectedOrg.equals(orgId)) {
      log.warn("Moredian callback alert: alertType=ORG_MISMATCH");
      return ok();
    }
    if (requireSignature
        && !signatures.verify(raw, nonce, orgId, signVersion, timestamp, signature, key)) {
      log.warn("Moredian callback alert: alertType=INVALID_SIGNATURE");
      throw new BusinessException(HttpStatus.FORBIDDEN, "INVALID_SIGNATURE", "回调签名无效。");
    }

    ParsedCallback callback;
    try {
      JsonNode root = json.readTree(raw);
      String callbackTag = root.path("callbackTag").asText();
      if (!"REC_SUCCESS".equals(callbackTag)) {
        log.warn(
            "Moredian callback alert: alertType=UNSUPPORTED_TAG, callbackTag={}",
            safe(callbackTag));
        return ok();
      }

      JsonNode data = root.path("data");
      String member = data.path("memberId").asText();
      String device = data.path("deviceSn").asText();
      String recognized = data.path("recognizeTime").asText();
      if (member.isBlank() || device.isBlank() || recognized.isBlank()) {
        throw new IllegalArgumentException("missing required callback field");
      }
      if (!expectedDevice.equals(device)) {
        log.warn(
            "Moredian callback alert: alertType=DEVICE_MISMATCH, callbackTag={}, memberId={}, deviceSn={}",
            callbackTag, mask(member), mask(device));
        return ok();
      }
      callback = new ParsedCallback(
          callbackTag, member, device, recognized,
          Instant.ofEpochMilli(Long.parseLong(recognized)).atZone(BUSINESS_ZONE));
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Moredian callback alert: alertType=INVALID_CALLBACK");
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CALLBACK", "回调字段无效。");
    }

    String dedup = sha256(
        orgId + "|" + callback.member() + "|" + callback.device() + "|" + callback.recognized());
    String traceId = "moredian-" + dedup.substring(0, 12);
    boolean inserted;
    try {
      inserted = attendance.ingest(
          dedup, orgId, callback.device(), callback.member(), callback.occurredAt(), raw, traceId);
    } catch (RuntimeException e) {
      log.error(
          "Moredian callback alert: alertType=PROCESSING_FAILED, dedupPrefix={}, traceId={}",
          dedup.substring(0, 12), traceId, e);
      throw e;
    }
    log.info(
        "Moredian callback processed: result={}, callbackTag={}, memberId={}, deviceSn={}, recognizeTime={}, dedupPrefix={}, traceId={}",
        inserted ? "ACCEPTED" : "DUPLICATE", callback.tag(), mask(callback.member()),
        mask(callback.device()), callback.recognized(), dedup.substring(0, 12), traceId);
    return ok();
  }

  private Map<String, String> ok() {
    return Map.of("result", "0", "message", "操作成功");
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String mask(String value) {
    if (value == null || value.isBlank()) return "<empty>";
    String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
    sanitized = sanitized.substring(0, Math.min(sanitized.length(), 80));
    if (sanitized.length() <= 6) return "***";
    return sanitized.substring(0, 3) + "***" + sanitized.substring(sanitized.length() - 3);
  }

  private String safe(String value) {
    if (value == null || value.isBlank()) return "<empty>";
    String sanitized = value.replaceAll("[^A-Za-z0-9_-]", "_");
    return sanitized.substring(0, Math.min(sanitized.length(), 40));
  }

  private record ParsedCallback(
      String tag, String member, String device, String recognized, java.time.ZonedDateTime occurredAt) {}
}
