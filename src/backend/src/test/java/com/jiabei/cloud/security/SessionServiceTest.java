package com.jiabei.cloud.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiabei.cloud.web.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionServiceTest {
  @Test void passwordAcceptsSixCharactersWithALetter(){
    assertThatCode(()->SessionService.validatePassword("abc123")).doesNotThrowAnyException();
  }

  @Test void passwordRejectsFiveCharactersAndDigitsOnly(){
    assertThatThrownBy(()->SessionService.validatePassword("abcd1")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(()->SessionService.validatePassword("123456")).isInstanceOf(BusinessException.class);
  }

  @Test void loginUsernameUsesTrimmedSixCharacterBoundary(){
    assertThatCode(()->SessionService.normalizeLoginUsername(" 123456 ","123456")).doesNotThrowAnyException();
    assertThatThrownBy(()->SessionService.normalizeLoginUsername(" 12345 ","123456")).isInstanceOf(BusinessException.class);
  }

  @Test void makeupCreatePermissionFollowsStoredFlag(){
    CurrentUser denied=new CurrentUser(UUID.randomUUID(),"makeup01","makeup01","化妆师",CurrentUser.Role.MAKEUP,UUID.randomUUID(),false,false,false,false,"csrf");
    CurrentUser allowed=new CurrentUser(UUID.randomUUID(),"makeup02","makeup02","化妆师",CurrentUser.Role.MAKEUP,UUID.randomUUID(),false,false,true,false,"csrf");
    org.assertj.core.api.Assertions.assertThat(denied.mayCreateAppointments()).isFalse();
    org.assertj.core.api.Assertions.assertThat(allowed.mayCreateAppointments()).isTrue();
  }

  @Test void operatorProxyBookingPermissionFollowsStoredFlag(){
    CurrentUser denied=new CurrentUser(UUID.randomUUID(),"operator01","operator01","运营",CurrentUser.Role.OPERATOR,null,true,true,false,false,"csrf");
    CurrentUser allowed=new CurrentUser(UUID.randomUUID(),"operator02","operator02","运营",CurrentUser.Role.OPERATOR,null,true,true,true,false,"csrf");
    org.assertj.core.api.Assertions.assertThat(denied.mayCreateAppointments()).isFalse();
    org.assertj.core.api.Assertions.assertThat(allowed.mayCreateAppointments()).isTrue();
    CurrentUser observer=new CurrentUser(UUID.randomUUID(),"observer01","observer01","观察员",CurrentUser.Role.OBSERVER,null,false,false,true,false,"csrf");
    org.assertj.core.api.Assertions.assertThat(observer.mayCreateAppointments()).isFalse();
  }
}
