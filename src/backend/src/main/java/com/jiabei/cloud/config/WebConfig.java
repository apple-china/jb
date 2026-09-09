package com.jiabei.cloud.config;

import com.jiabei.cloud.security.ApiSecurityInterceptor;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final ApiSecurityInterceptor security;private final String uploadDirectory;
  public WebConfig(ApiSecurityInterceptor security,@Value("${jiabei.upload-directory:uploads}") String uploadDirectory){this.security=security;this.uploadDirectory=uploadDirectory;}
  @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(security).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/auth/mock-login","/api/v1/auth/password-login","/api/v1/auth/dingtalk-login","/api/v1/integrations/**");}
  @Override public void addResourceHandlers(ResourceHandlerRegistry registry){String location=Path.of(uploadDirectory).toAbsolutePath().normalize().toUri().toString();if(!location.endsWith("/"))location+="/";registry.addResourceHandler("/uploads/**").addResourceLocations(location);}
  /**
   * 为全部响应设置基础安全头。Swagger UI 依赖运行时生成的内联样式，因此只对
   * /swagger-ui/ 静态文档页开放 style-src 'unsafe-inline'；业务 API 继续使用严格策略。
   */
  @Bean
  Filter securityHeaders(){
    return (request,response,chain)->{
      HttpServletRequest httpRequest=(HttpServletRequest)request;
      HttpServletResponse httpResponse=(HttpServletResponse)response;
      httpResponse.setHeader("X-Content-Type-Options","nosniff");
      httpResponse.setHeader("Referrer-Policy","no-referrer");
      httpResponse.setHeader("X-Frame-Options","DENY");
      String stylePolicy=httpRequest.getRequestURI().startsWith("/swagger-ui/")
        ?"style-src 'self' 'unsafe-inline'; "
        :"style-src 'self'; ";
      httpResponse.setHeader("Content-Security-Policy","default-src 'self'; "+stylePolicy+"img-src 'self' data:; object-src 'none'; frame-ancestors 'none'");
      chain.doFilter(request,response);
    };
  }
}
