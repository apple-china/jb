package com.jiabei.cloud.security;

import com.jiabei.cloud.web.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
  private final Clock clock;private final int limit;private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>();
  public LoginRateLimiter(Clock clock,@Value("${jiabei.security.login-rate-limit-per-minute:20}") int limit){this.clock=clock;this.limit=limit;}
  public void check(HttpServletRequest request){long minute=clock.instant().getEpochSecond()/60;String key=request.getRemoteAddr();Bucket bucket=buckets.compute(key,(k,current)->current==null||current.minute!=minute?new Bucket(minute):current);if(bucket.count.incrementAndGet()>limit)throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"LOGIN_RATE_LIMITED","登录请求过于频繁，请稍后再试。");if(buckets.size()>10000)buckets.entrySet().removeIf(e->e.getValue().minute<minute-2);}
  private static final class Bucket{private final long minute;private final AtomicInteger count=new AtomicInteger();private Bucket(long minute){this.minute=minute;}}
}
