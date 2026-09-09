package com.jiabei.cloud.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local","test"}) @Component
public class MockDingTalkDirectoryGateway implements DingTalkDirectoryGateway {
  private static final List<Employee> EMPLOYEES = buildEmployees();

  private static List<Employee> buildEmployees() {
    List<Employee> rows = new ArrayList<>();
    // 前 41 人与 V7 数据匹配，后 50 人故意不注册。
    rows.add(new Employee("admin01", "超管"));
    IntStream.rangeClosed(1, 5).forEach(i -> rows.add(
        new Employee("operator%02d".formatted(i), "运营%02d".formatted(i))));
    IntStream.rangeClosed(1, 5).forEach(i -> rows.add(
        new Employee("observer%02d".formatted(i), "观察员%02d".formatted(i))));
    IntStream.rangeClosed(1, 10).forEach(i -> rows.add(
        new Employee("makeup%02d".formatted(i), "化妆师%02d".formatted(i))));
    IntStream.rangeClosed(1, 20).forEach(i -> rows.add(
        new Employee("streamer%02d".formatted(i), "主播%02d".formatted(i))));
    rows.addAll(List.of(
        new Employee("user123456", "张三"),
        new Employee("user654321", "李四"),
        new Employee("user889900", "王五"),
        new Employee("user776655", "赵六")));
    IntStream.rangeClosed(5, 50).forEach(i -> rows.add(
        new Employee("candidate%03d".formatted(i), "候选同事%03d".formatted(i))));
    return List.copyOf(rows);
  }

  @Override public List<Employee> employees(String query){
    String needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);
    return EMPLOYEES.stream().filter(e->needle.isEmpty()||e.username().toLowerCase(Locale.ROOT).contains(needle)||e.userId().toLowerCase(Locale.ROOT).contains(needle)).toList();
  }
}
