package com.jiabei.cloud.security;

import com.jiabei.cloud.web.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
/**
 * API 的统一会话与写请求安全边界。
 *
 * <p>会话解析会在每次请求时从数据库刷新角色和权限，因此管理员关闭化妆师代预约后，
 * 无需等待旧会话过期即可生效。写请求还必须同时通过 Origin 与 CSRF 校验。</p>
 */
public class ApiSecurityInterceptor implements HandlerInterceptor {
  private final SessionService sessions;private final JdbcTemplate jdbc;private final String[] origins;
  public ApiSecurityInterceptor(SessionService sessions,JdbcTemplate jdbc,@Value("${jiabei.security.allowed-origins}") String origins){this.sessions=sessions;this.jdbc=jdbc;this.origins=origins.split(",");}
  /**
   * 按“基础会话 → 写请求防护 → 系统入口 → 首次改密 → 角色权限”的顺序校验。
   * 顺序是有意设计的：越基础的安全错误越早返回，业务控制器无需重复防护。
   */
  @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
    if(!(handler instanceof HandlerMethod))return true;
    String path=request.getRequestURI();CurrentUser user=sessions.require(request);request.setAttribute("currentUser",user);
    boolean write=!"GET".equals(request.getMethod())&&!"HEAD".equals(request.getMethod());
    if(write){String origin=request.getHeader("Origin");if(origin!=null&&Arrays.stream(origins).map(String::trim).noneMatch(origin::equals))throw new BusinessException(HttpStatus.FORBIDDEN,"ORIGIN_NOT_ALLOWED","请求来源不受信任。");String csrf=request.getHeader("X-CSRF-Token");if(csrf==null||!csrf.equals(user.csrfToken()))throw new BusinessException(HttpStatus.FORBIDDEN,"CSRF_INVALID","安全校验失败，请刷新页面后重试。");}
    if(!user.isAdministrator()&&!path.equals("/api/v1/me")&&!path.equals("/api/v1/logout")&&!systemEnabled())throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"SYSTEM_DISABLED","预约暂未开放。");
    if(user.mustChangePassword()&&!Boolean.TRUE.equals(request.getAttribute("mockLogin"))&&!path.equals("/api/v1/me")&&!path.equals("/api/v1/logout")&&!path.equals("/api/v1/auth/change-password"))throw new BusinessException(HttpStatus.FORBIDDEN,"PASSWORD_CHANGE_REQUIRED","首次登录或密码重置后须先修改密码。");
    if(path.startsWith("/api/v1/mock/")&&!user.isAdministrator())throw BusinessException.forbidden();
    if(path.startsWith("/api/v1/admin/")&&!user.isAdministrator()&&user.role()!=CurrentUser.Role.OBSERVER&&user.role()!=CurrentUser.Role.MAKEUP)throw BusinessException.forbidden();
    if(write&&path.startsWith("/api/v1/admin/")&&!user.isAdministrator()&&user.role()!=CurrentUser.Role.MAKEUP)throw BusinessException.forbidden();
    if(user.role()==CurrentUser.Role.MAKEUP&&path.startsWith("/api/v1/admin/")){
      boolean readable=!write&&(path.startsWith("/api/v1/admin/appointments")||path.equals("/api/v1/admin/makeup-artists")||path.equals("/api/v1/admin/teams")||path.equals("/api/v1/admin/streamers"));
      boolean createRequest=write&&"POST".equals(request.getMethod())&&path.equals("/api/v1/admin/appointments");
      if(createRequest&&!user.mayCreateAppointments())throw new BusinessException(HttpStatus.FORBIDDEN,"PROXY_BOOKING_FORBIDDEN","当前账号未开启代预约权限。");
      boolean canCreate=createRequest;
      if(!readable&&!canCreate)throw BusinessException.forbidden();
    }
    return true;
  }
  private boolean systemEnabled(){Boolean enabled=jdbc.queryForObject("SELECT boolean_value FROM system_setting WHERE key='SYSTEM_ENABLED'",Boolean.class);return Boolean.TRUE.equals(enabled);}
}
