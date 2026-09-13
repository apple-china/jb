package com.jiabei.cloud.security;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Profile({"production","dingtalk-test"}) @Component
public class SuperAdminBootstrap implements ApplicationRunner {
  private final JdbcTemplate jdbc;private final PasswordService passwords;private final String username;private final String password;private final String dingTalkUserId;private final String nickname;
  public SuperAdminBootstrap(JdbcTemplate jdbc,PasswordService passwords,@Value("${jiabei.bootstrap.super-admin.username:}") String username,@Value("${jiabei.bootstrap.super-admin.password:}") String password,@Value("${jiabei.bootstrap.super-admin.dingtalk-user-id:}") String dingTalkUserId,@Value("${jiabei.bootstrap.super-admin.nickname:Admin}") String nickname){this.jdbc=jdbc;this.passwords=passwords;this.username=username;this.password=password;this.dingTalkUserId=dingTalkUserId;this.nickname=nickname;}
  @Override public void run(ApplicationArguments args){Integer count=jdbc.queryForObject("SELECT count(*) FROM app_user WHERE role='SUPER_ADMIN'",Integer.class);if(count!=null&&count>0)return;if(username.isBlank()||password.isBlank())throw new IllegalStateException("首次启动必须配置唯一超管账号和密码");SessionService.validatePassword(password);String ding=dingTalkUserId==null||dingTalkUserId.isBlank()?null:dingTalkUserId.trim();jdbc.update("INSERT INTO app_user(id,username,password_hash,dingtalk_user_id,dingtalk_username,nickname,role,is_active,is_attending,can_modify_appointments,can_cancel_appointments,must_change_password) VALUES (?,?,?,?,?,?,'SUPER_ADMIN',true,true,true,true,false)",UUID.randomUUID(),username.trim(),passwords.encode(password),ding,ding==null?null:nickname.trim(),nickname.trim());}
}
