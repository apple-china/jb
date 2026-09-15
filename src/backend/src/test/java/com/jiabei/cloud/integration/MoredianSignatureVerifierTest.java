package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoredianSignatureVerifierTest {
  private static final String RAW = "{\"callbackTag\":\"REC_SUCCESS\",\"data\":{\"memberId\":\"ding-user-test\",\"deviceSn\":\"device-test\",\"recognizeTime\":\"1789432200000\"}}";
  private final MoredianSignatureVerifier verifier = new MoredianSignatureVerifier();

  @Test
  void verifiesKnownHmacSha1VectorAgainstExactRawBody() {
    assertThat(verifier.verify(
        RAW,
        "nonce-test",
        "org-test",
        "1",
        "1789432200123",
        "w+3bIHW5mdvS71k+o6Gll4PdMLI=",
        "auth-key-test")).isTrue();
  }

  @Test
  void rejectsChangedRawBodyOrSignature() {
    assertThat(verifier.verify(
        RAW + " ", "nonce-test", "org-test", "1", "1789432200123",
        "w+3bIHW5mdvS71k+o6Gll4PdMLI=", "auth-key-test")).isFalse();
    assertThat(verifier.verify(
        RAW, "nonce-test", "org-test", "1", "1789432200123",
        "invalid", "auth-key-test")).isFalse();
  }

  @Test
  void rejectsMissingSecretOrSignature() {
    assertThat(verifier.verify(RAW, "n", "o", "1", "t", null, "key")).isFalse();
    assertThat(verifier.verify(RAW, "n", "o", "1", "t", "signature", "")).isFalse();
  }
}
