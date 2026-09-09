package com.jiabei.cloud.integration;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local","test"}) @Component
public class MockDingTalkDirectoryGateway implements DingTalkDirectoryGateway {
  private static final List<Employee> EMPLOYEES=List.of(
      new Employee("admin01","Admin"),new Employee("operator01","运营一号"),
      new Employee("observer01","观察员一号"),new Employee("makeup01","小贝老师"),
      new Employee("streamer01","玲玲"),new Employee("streamer02","小狼"),
      new Employee("streamer03","米粒"),new Employee("user123456","张三"),
      new Employee("user654321","李四"),new Employee("user889900","王五"),
      new Employee("user776655","赵六"));
  @Override public List<Employee> employees(String query){
    String needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);
    return EMPLOYEES.stream().filter(e->needle.isEmpty()||e.username().toLowerCase(Locale.ROOT).contains(needle)||e.userId().toLowerCase(Locale.ROOT).contains(needle)).toList();
  }
}
