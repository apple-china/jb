package com.jiabei.cloud.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiabei.cloud.web.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
  @Test void rejectsRequestsBeyondMinuteLimit(){HttpServletRequest request=mock(HttpServletRequest.class);when(request.getRemoteAddr()).thenReturn("127.0.0.1");LoginRateLimiter limiter=new LoginRateLimiter(Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"),ZoneOffset.UTC),2);limiter.check(request);limiter.check(request);assertThatThrownBy(()->limiter.check(request)).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).code()).isEqualTo("LOGIN_RATE_LIMITED");}
}
