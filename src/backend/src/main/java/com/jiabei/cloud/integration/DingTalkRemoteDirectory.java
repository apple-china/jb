package com.jiabei.cloud.integration;

import java.util.List;

/** Fetches one complete DingTalk directory snapshot. A failed fetch must throw, never return a partial list. */
@FunctionalInterface
public interface DingTalkRemoteDirectory {
  List<Employee> fetchAll();

  record Employee(String userId, String name, String unionId, List<Long> departmentIds) {}
}
