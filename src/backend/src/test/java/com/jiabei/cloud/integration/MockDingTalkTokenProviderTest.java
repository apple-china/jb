package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MockDingTalkTokenProviderTest {
  @Test void concurrentRefreshUsesSingleFlight() throws Exception {MockDingTalkTokenProvider provider=new MockDingTalkTokenProvider(Clock.systemUTC());try(var executor=Executors.newFixedThreadPool(12)){var tasks=IntStream.range(0,50).mapToObj(i->(java.util.concurrent.Callable<String>)provider::get).toList();for(var result:executor.invokeAll(tasks))assertThat(result.get()).isEqualTo("mock-token-redacted");}assertThat(provider.refreshCount()).isEqualTo(1);provider.expire();provider.get();assertThat(provider.refreshCount()).isEqualTo(2);}
}
