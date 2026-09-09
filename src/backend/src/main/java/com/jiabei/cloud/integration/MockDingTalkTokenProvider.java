package com.jiabei.cloud.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local","test"}) @Component
public class MockDingTalkTokenProvider {
  private final Clock clock;private final ReentrantLock lock=new ReentrantLock();private volatile Token cached;private volatile int refreshCount;
  record Token(String value,Instant expiresAt){}
  public MockDingTalkTokenProvider(Clock clock){this.clock=clock;}
  public String get(){Token token=cached;if(token!=null&&token.expiresAt().isAfter(clock.instant().plusSeconds(300)))return token.value();lock.lock();try{token=cached;if(token==null||!token.expiresAt().isAfter(clock.instant().plusSeconds(300))){refreshCount++;cached=new Token("mock-token-redacted",clock.instant().plusSeconds(7200));}return cached.value();}finally{lock.unlock();}}
  public void expire(){cached=new Token("expired",Instant.EPOCH);} public int refreshCount(){return refreshCount;}
}
