package com.jiabei.cloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final JdbcTemplate jdbc;private final ObjectMapper json;
  public GlobalExceptionHandler(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> business(BusinessException e, HttpServletRequest request){
    if(e.status()==HttpStatus.FORBIDDEN)auditPermissionDenied(e,request);
    return ResponseEntity.status(e.status()).body(ApiResponse.fail(e.code(),e.getMessage(),e.details(),Trace.id(request)));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> validation(MethodArgumentNotValidException e,HttpServletRequest request){
    String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getDefaultMessage()).orElse("请求参数不正确。");
    return ResponseEntity.unprocessableEntity().body(ApiResponse.fail("VALIDATION_FAILED",message,null,Trace.id(request)));
  }
  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> notFound(NoResourceFoundException e,HttpServletRequest request){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND","请求的接口不存在。",null,Trace.id(request)));}
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> dataConflict(DataIntegrityViolationException e,HttpServletRequest request){return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("DATA_CONFLICT","数据已存在或仍被其他记录使用，请检查后重试。",null,Trace.id(request)));}
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Map<String,Object>>> unexpected(Exception e,HttpServletRequest request){
    String trace=Trace.id(request);log.error("Unhandled API error traceId={} type={}",trace,e.getClass().getName(),e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("INTERNAL_ERROR","服务暂时不可用，请稍后重试。",null,trace));
  }
  private void auditPermissionDenied(BusinessException e,HttpServletRequest request){
    Object current=request.getAttribute("currentUser");if(!(current instanceof CurrentUser user))return;
    try{Map<String,Object> data=new LinkedHashMap<>();data.put("method",request.getMethod());data.put("path",request.getRequestURI());data.put("code",e.code());jdbc.update("INSERT INTO audit_log(id,entity_type,entity_key,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,after_data,trace_id) VALUES (?,'AUTH',?,'PERMISSION_DENIED',?,?,?,CAST(? AS jsonb),?)",UUID.randomUUID(),request.getRequestURI(),user.id(),user.loginId(),user.nickname(),json.writeValueAsString(data),Trace.id(request));}catch(Exception auditError){log.warn("Permission denial audit failed traceId={}",Trace.id(request),auditError);}
  }
}
