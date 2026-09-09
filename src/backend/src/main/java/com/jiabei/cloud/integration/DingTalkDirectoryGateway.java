package com.jiabei.cloud.integration;

import java.util.List;

public interface DingTalkDirectoryGateway {
  List<Employee> employees(String query);
  record Employee(String userId,String username){}
}
