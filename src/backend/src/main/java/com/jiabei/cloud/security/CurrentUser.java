package com.jiabei.cloud.security;

import java.util.UUID;

public record CurrentUser(
    UUID id,
    String loginId,
    String dingTalkUserId,
    String nickname,
    Role role,
    UUID makeupArtistId,
    boolean canModifyAppointments,
    boolean canCancelAppointments,
    boolean canCreateAppointments,
    boolean mustChangePassword,
    String csrfToken) {
  public enum Role { SUPER_ADMIN, OPERATOR, OBSERVER, MAKEUP, STREAMER }
  public boolean isAdministrator(){return role==Role.SUPER_ADMIN||role==Role.OPERATOR;}
  public boolean isStreamer(){return role==Role.STREAMER;}
  public boolean mayModifyAppointments(){return role==Role.SUPER_ADMIN||(role==Role.OPERATOR&&canModifyAppointments);}
  public boolean mayCancelAppointments(){return role==Role.SUPER_ADMIN||(role==Role.OPERATOR&&canCancelAppointments);}
  /** 主播自助预约不受此开关影响；运营和化妆师必须显式具备代预约权限。 */
  public boolean mayCreateAppointments(){
    return switch(role){
      case SUPER_ADMIN,STREAMER->true;
      case OPERATOR,MAKEUP->canCreateAppointments;
      case OBSERVER->false;
    };
  }
}
