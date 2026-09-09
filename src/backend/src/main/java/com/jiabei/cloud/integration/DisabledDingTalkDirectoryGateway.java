package com.jiabei.cloud.integration;

import com.jiabei.cloud.web.BusinessException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Profile("production") @Component
public class DisabledDingTalkDirectoryGateway implements DingTalkDirectoryGateway {
  @Override public List<Employee> employees(String query){throw new BusinessException(HttpStatus.BAD_GATEWAY,"DINGTALK_DIRECTORY_UNAVAILABLE","钉钉员工目录尚未配置，请联系管理员。");}
}
