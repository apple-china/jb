package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.web.BusinessException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AppointmentAnalyticsServiceTest {
  @Test void observerCannotReadAnalytics(){
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    CurrentUser observer=new CurrentUser(UUID.randomUUID(),"observer01","observer01","观察员",CurrentUser.Role.OBSERVER,null,false,false,false,false,"csrf");
    assertThatThrownBy(()->new AppointmentAnalyticsService(jdbc).analytics(observer,LocalDate.now(),LocalDate.now())).isInstanceOf(BusinessException.class);
    verifyNoInteractions(jdbc);
  }
}
