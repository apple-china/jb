package com.jiabei.cloud.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiabei.cloud.web.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionServiceTest {
  @Test void passwordAcceptsAnySixToTwelveCharacters(){
    assertThatCode(()->SessionService.validatePassword("abc123")).doesNotThrowAnyException();
    assertThatCode(()->SessionService.validatePassword("123456")).doesNotThrowAnyException();
    assertThatCode(()->SessionService.validatePassword("abcdefghijkl")).doesNotThrowAnyException();
  }

  @Test void passwordRejectsOutsideSixToTwelveCharacters(){
    assertThatThrownBy(()->SessionService.validatePassword("abcd1")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(()->SessionService.validatePassword("abcdefghijklm")).isInstanceOf(BusinessException.class);
  }

  @Test void loginAcceptsTrimmedAlphanumericUsernameFromSixToTwelveCharacters(){
    assertThatCode(()->SessionService.normalizeLoginUsername(" Abc123 ","123456")).doesNotThrowAnyException();
    assertThatThrownBy(()->SessionService.normalizeLoginUsername(" 12345 ","123456")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(()->SessionService.normalizeLoginUsername("abc_123","123456")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(()->SessionService.normalizeLoginUsername("abcdefghijklm","123456")).isInstanceOf(BusinessException.class);
  }

  @Test void superAdminUsesRecoveryFlagSemantics(){
    assertThat(SessionService.requiresPasswordChange(CurrentUser.Role.SUPER_ADMIN,false)).isTrue();
    assertThat(SessionService.requiresPasswordChange(CurrentUser.Role.SUPER_ADMIN,true)).isFalse();
  }

  @Test void recoveryLoginOnlyAcceptsTheSuperAdminAccountNameWhileFlagIsFalse(){
    assertThat(SessionService.isSuperAdminRecoveryLogin(CurrentUser.Role.SUPER_ADMIN,false,"superadmin","superadmin")).isTrue();
    assertThat(SessionService.isSuperAdminRecoveryLogin(CurrentUser.Role.SUPER_ADMIN,true,"superadmin","superadmin")).isFalse();
    assertThat(SessionService.isSuperAdminRecoveryLogin(CurrentUser.Role.STREAMER,false,"streamer01","streamer01")).isFalse();
  }

  @Test void regularAccountKeepsExistingPasswordChangeMeaning(){
    assertThat(SessionService.requiresPasswordChange(CurrentUser.Role.STREAMER,true)).isTrue();
    assertThat(SessionService.requiresPasswordChange(CurrentUser.Role.STREAMER,false)).isFalse();
  }

  @Test void makeupCreatePermissionFollowsStoredFlag(){
    CurrentUser denied=new CurrentUser(UUID.randomUUID(),"makeup01","makeup01","化妆师",CurrentUser.Role.MAKEUP,UUID.randomUUID(),false,false,false,false,"csrf");
    CurrentUser allowed=new CurrentUser(UUID.randomUUID(),"makeup02","makeup02","化妆师",CurrentUser.Role.MAKEUP,UUID.randomUUID(),false,false,true,false,"csrf");
    assertThat(denied.mayCreateAppointments()).isFalse();
    assertThat(allowed.mayCreateAppointments()).isTrue();
  }

  @Test void operatorProxyBookingPermissionFollowsStoredFlag(){
    CurrentUser denied=new CurrentUser(UUID.randomUUID(),"operator01","operator01","运营",CurrentUser.Role.OPERATOR,null,true,true,false,false,"csrf");
    CurrentUser allowed=new CurrentUser(UUID.randomUUID(),"operator02","operator02","运营",CurrentUser.Role.OPERATOR,null,true,true,true,false,"csrf");
    assertThat(denied.mayCreateAppointments()).isFalse();
    assertThat(allowed.mayCreateAppointments()).isTrue();
    CurrentUser observer=new CurrentUser(UUID.randomUUID(),"observer01","observer01","观察员",CurrentUser.Role.OBSERVER,null,false,false,true,false,"csrf");
    assertThat(observer.mayCreateAppointments()).isFalse();
  }
}