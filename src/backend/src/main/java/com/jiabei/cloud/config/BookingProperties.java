package com.jiabei.cloud.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jiabei.booking")
public record BookingProperties(
    ZoneId zoneId,
    int timeStepMinutes,
    int serviceDurationMinutes,
    int streamerLeadMinutes,
    int adminLeadMinutes,
    int attendanceWindowMinutes,
    int lateGraceMinutes,
    String groupId,
    String cardTemplateId,
    String lateCardTemplateId,
    String appEntryUrl,
    int outboxMaxAttempts) {
  public BookingProperties {
    if (zoneId == null) zoneId = ZoneId.of("Asia/Shanghai");
    if (!ZoneId.of("Asia/Shanghai").equals(zoneId)) throw new IllegalArgumentException("业务时区必须为 Asia/Shanghai");
    if (timeStepMinutes <= 0 || serviceDurationMinutes <= 0 || streamerLeadMinutes < 0
        || adminLeadMinutes < 0 || attendanceWindowMinutes <= 0 || lateGraceMinutes < 0) {
      throw new IllegalArgumentException("预约固定参数无效");
    }
  }
}
