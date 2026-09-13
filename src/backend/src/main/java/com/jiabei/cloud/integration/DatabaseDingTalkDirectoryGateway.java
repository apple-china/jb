package com.jiabei.cloud.integration;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Account pickers read the last complete local snapshot, so they do not depend on live API latency. */
@Profile("dingtalk-test")
@Component
public class DatabaseDingTalkDirectoryGateway implements DingTalkDirectoryGateway {
  private final JdbcTemplate jdbc;
  public DatabaseDingTalkDirectoryGateway(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  public List<Employee> employees(String query) {
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    return jdbc.query("""
        SELECT user_id,name FROM dingtalk_employee
        WHERE is_active AND (?='' OR lower(name) LIKE '%'||?||'%' OR lower(user_id) LIKE '%'||?||'%')
        ORDER BY name,user_id LIMIT 200
        """, (rs, row) -> new Employee(rs.getString(1), rs.getString(2)), needle, needle, needle);
  }
}
