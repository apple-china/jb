package com.jiabei.cloud.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiabei.cloud.integration.DingTalkDirectoryGateway;
import com.jiabei.cloud.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class DingTalkDirectoryControllerTest {
  @Test void fuzzyDirectoryResultsExcludeRegisteredEmployeesAndExposeRequiredLabelFields(){
    DingTalkDirectoryGateway directory=mock(DingTalkDirectoryGateway.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);
    when(directory.employees("张")).thenReturn(List.of(new DingTalkDirectoryGateway.Employee("taken","张已注册"),new DingTalkDirectoryGateway.Employee("user123456","张三")));
    when(jdbc.queryForList("SELECT dingtalk_user_id FROM app_user WHERE dingtalk_user_id IS NOT NULL",String.class)).thenReturn(List.of("taken"));
    MockHttpServletRequest request=new MockHttpServletRequest();request.setAttribute("currentUser",new CurrentUser(UUID.randomUUID(),"admin01","admin01","Admin",CurrentUser.Role.SUPER_ADMIN,null,true,true,true,false,"csrf"));
    var response=new DingTalkDirectoryController(directory,jdbc).employees("张",request);
    assertThat(response.data()).containsExactly(java.util.Map.of("dingTalkUserId","user123456","dingTalkUsername","张三","label","张三 @user123456"));
    verify(directory).employees("张");
  }
}
