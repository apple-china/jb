package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockDingTalkDirectoryGatewayTest {
  private final MockDingTalkDirectoryGateway gateway = new MockDingTalkDirectoryGateway();

  @Test void exposesExactlyFiftyUnregisteredCandidatesWithUniqueIdentityAndName() {
    List<DingTalkDirectoryGateway.Employee> all = gateway.employees("");
    Set<String> legacy = Set.of("user123456", "user654321", "user889900", "user776655");
    List<DingTalkDirectoryGateway.Employee> candidates = all.stream()
        .filter(e -> legacy.contains(e.userId()) || e.userId().startsWith("candidate"))
        .toList();
    assertThat(candidates).hasSize(50);
    assertThat(new HashSet<>(candidates.stream().map(DingTalkDirectoryGateway.Employee::userId).toList())).hasSize(50);
    assertThat(new HashSet<>(candidates.stream().map(DingTalkDirectoryGateway.Employee::username).toList())).hasSize(50);
  }

  @Test void searchesCandidatesByTrimmedIdAndChineseName() {
    assertThat(gateway.employees(" candidate050 "))
        .extracting(DingTalkDirectoryGateway.Employee::userId)
        .containsExactly("candidate050");
    assertThat(gateway.employees("张三"))
        .extracting(DingTalkDirectoryGateway.Employee::userId)
        .containsExactly("user123456");
  }

  @Test void preservesExistingAutomationIdentities() {
    assertThat(gateway.employees(""))
        .extracting(DingTalkDirectoryGateway.Employee::userId)
        .contains("admin01", "operator01", "observer01", "makeup01",
            "streamer01", "streamer02", "streamer03", "streamer04");
  }
}
