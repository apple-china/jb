package com.jiabei.cloud.web;

import com.jiabei.cloud.integration.DingTalkDirectoryGateway;
import com.jiabei.cloud.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/admin/dingtalk-employees")
public class DingTalkDirectoryController {
  private final DingTalkDirectoryGateway directory;private final JdbcTemplate jdbc;
  public DingTalkDirectoryController(DingTalkDirectoryGateway directory,JdbcTemplate jdbc){this.directory=directory;this.jdbc=jdbc;}
  @GetMapping ApiResponse<List<Map<String,String>>> employees(@RequestParam(defaultValue="") String query,HttpServletRequest request){
    CurrentUser actor=AuthController.current(request);if(!actor.isAdministrator())throw BusinessException.forbidden();
    var registered=new HashSet<>(jdbc.queryForList("SELECT dingtalk_user_id FROM app_user WHERE dingtalk_user_id IS NOT NULL",String.class));
    var result=directory.employees(query).stream().filter(employee->!registered.contains(employee.userId())).map(employee->Map.of("dingTalkUserId",employee.userId(),"dingTalkUsername",employee.username(),"label",employee.username()+" @"+employee.userId())).toList();
    return ApiResponse.ok(result,Trace.id(request));
  }
}
