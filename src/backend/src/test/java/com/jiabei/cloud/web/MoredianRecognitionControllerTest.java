package com.jiabei.cloud.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.integration.MoredianSignatureVerifier;
import com.jiabei.cloud.service.AttendanceService;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MoredianRecognitionControllerTest {
  private static final String ORG = "org-test";
  private static final String DEVICE = "device-test";
  private static final String RAW = "{\"callbackTag\":\"REC_SUCCESS\",\"data\":{\"memberId\":\"ding-user-test\",\"deviceSn\":\"device-test\",\"recognizeTime\":\"1789432200000\"}}";

  private AttendanceService attendance;
  private MoredianSignatureVerifier verifier;
  private MoredianRecognitionController controller;

  @BeforeEach
  void setUp() {
    attendance = mock(AttendanceService.class);
    verifier = mock(MoredianSignatureVerifier.class);
    controller = new MoredianRecognitionController(
        attendance, new ObjectMapper(), verifier, ORG, "auth-key-test", DEVICE, true);
  }

  @Test
  void acceptsValidSignedSuccessEventAndPassesExactRawBody() {
    when(verifier.verify(RAW, "nonce", ORG, "1", "timestamp", "signature", "auth-key-test"))
        .thenReturn(true);
    when(attendance.ingest(
        any(), eq(ORG), eq(DEVICE), eq("ding-user-test"), any(ZonedDateTime.class),
        eq(RAW), any())).thenReturn(true);

    Map<String, String> response = controller.receive(
        ORG, "1", "timestamp", "nonce", "signature", RAW);

    assertThat(response).containsEntry("result", "0");
    verify(verifier).verify(RAW, "nonce", ORG, "1", "timestamp", "signature", "auth-key-test");
    verify(attendance).ingest(
        any(), eq(ORG), eq(DEVICE), eq("ding-user-test"), any(ZonedDateTime.class),
        eq(RAW), any());
  }

  @Test
  void rejectsInvalidSignatureBeforeParsingOrPersistence() {
    when(verifier.verify(RAW, "nonce", ORG, "1", "timestamp", "bad", "auth-key-test"))
        .thenReturn(false);

    assertThatThrownBy(() -> controller.receive(ORG, "1", "timestamp", "nonce", "bad", RAW))
        .isInstanceOfSatisfying(BusinessException.class, error -> {
          assertThat(error.status().value()).isEqualTo(403);
          assertThat(error.code()).isEqualTo("INVALID_SIGNATURE");
        });
    verifyNoInteractions(attendance);
  }

  @Test
  void acknowledgesOtherOrganizationWithoutVerifyingOrPersisting() {
    assertThat(controller.receive("other-org", "1", "timestamp", "nonce", "signature", RAW))
        .containsEntry("result", "0");
    verifyNoInteractions(verifier, attendance);
  }

  @Test
  void acknowledgesUnsupportedTagAndOtherDeviceWithoutPersisting() {
    when(verifier.verify(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

    String otherTag = "{\"callbackTag\":\"DEVICE_HEARTBEAT\",\"data\":{}}";
    assertThat(controller.receive(ORG, "1", "timestamp", "nonce", "signature", otherTag))
        .containsEntry("result", "0");

    String otherDevice = RAW.replace(DEVICE, "other-device");
    assertThat(controller.receive(ORG, "1", "timestamp", "nonce", "signature", otherDevice))
        .containsEntry("result", "0");
    verify(attendance, never()).ingest(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsMissingFieldsMalformedTimeAndOversizedBody() {
    when(verifier.verify(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

    String missing = "{\"callbackTag\":\"REC_SUCCESS\",\"data\":{\"deviceSn\":\"device-test\"}}";
    assertInvalidCallback(missing, "INVALID_CALLBACK", 400);

    String malformedTime = RAW.replace("1789432200000", "not-a-time");
    assertInvalidCallback(malformedTime, "INVALID_CALLBACK", 400);

    String oversized = "x".repeat(64 * 1024 + 1);
    assertInvalidCallback(oversized, "PAYLOAD_TOO_LARGE", 413);
  }

  @Test
  void propagatesPersistenceFailureSoMoredianCanRetry() {
    when(verifier.verify(RAW, "nonce", ORG, "1", "timestamp", "signature", "auth-key-test"))
        .thenReturn(true);
    when(attendance.ingest(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("database unavailable"));

    assertThatThrownBy(() -> controller.receive(
        ORG, "1", "timestamp", "nonce", "signature", RAW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database unavailable");
  }

  private void assertInvalidCallback(String raw, String code, int status) {
    assertThatThrownBy(() -> controller.receive(ORG, "1", "timestamp", "nonce", "signature", raw))
        .isInstanceOfSatisfying(BusinessException.class, error -> {
          assertThat(error.status().value()).isEqualTo(status);
          assertThat(error.code()).isEqualTo(code);
        });
  }
}
