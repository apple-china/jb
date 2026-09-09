package com.jiabei.cloud.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** 所有业务接口共用的运行时响应包；字段注解同时服务于 Swagger 和源码阅读。 */
public record ApiResponse<T>(
  @Schema(title="请求成功",description="业务处理成功时为 true；失败时为 false。",example="true") boolean success,
  @Schema(title="响应数据",description="接口成功数据；无返回内容或请求失败时可能为空。",nullable=true) T data,
  @Schema(title="错误信息",description="请求失败时的业务错误码和中文消息；成功时为空。",nullable=true) ApiError error,
  @Schema(title="追踪 ID",description="用于关联服务端日志的请求唯一标识。",example="d2b5233e-323e-4ecc-b3c9-f425688d685c") String traceId) {
  public record ApiError(
    @Schema(title="业务错误码",description="稳定的英文业务错误码，客户端可据此分支处理。",example="VALIDATION_FAILED") String code,
    @Schema(title="错误消息",description="面向用户或调试人员的中文错误说明。",example="请求参数不符合要求。") String message) {}
  public static <T> ApiResponse<T> ok(T data, String traceId) { return new ApiResponse<>(true, data, null, traceId); }
  public static ApiResponse<Map<String,Object>> fail(String code, String message, Object data, String traceId) {
    return new ApiResponse<>(false, data == null ? null : Map.of("details", data), new ApiError(code, message), traceId);
  }
}
