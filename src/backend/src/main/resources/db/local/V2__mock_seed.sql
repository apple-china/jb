INSERT INTO team(id,name,logo_url,is_active) VALUES
('30000000-0000-0000-0000-000000000001','星河一团','/brand/team-placeholder-a.svg',true),
('30000000-0000-0000-0000-000000000002','晨光二团','/brand/team-placeholder-b.svg',true),
('30000000-0000-0000-0000-000000000003','风尚三团','/brand/team-placeholder-c.svg',true),
('30000000-0000-0000-0000-000000000004','停用测试团',null,false);

INSERT INTO makeup_teacher(id,name,avatar_url,work_days,work_start,work_end,is_attending,is_active) VALUES
('20000000-0000-0000-0000-000000000001','小贝老师','/brand/teacher-placeholder-a.svg','1,2,3,4,5,6,7','08:00','20:00',true,true),
('20000000-0000-0000-0000-000000000002','小美老师','/brand/teacher-placeholder-b.svg','1,2,3,4,5,6,7','08:00','20:00',true,true),
('20000000-0000-0000-0000-000000000003','休息老师',null,'1,2,3,4,5','08:00','20:00',false,true),
('20000000-0000-0000-0000-000000000004','停用老师',null,'1,2,3,4,5,6,7','08:00','20:00',true,false);

-- 仅 local/test 使用。正式部署由 SuperAdminBootstrap 安全初始化唯一超管。
INSERT INTO app_user(id,username,password_hash,dingtalk_user_id,display_name,role,teacher_id,is_active,is_attending,can_modify_appointments,can_cancel_appointments,must_change_password,last_teacher_id,last_start_time,last_team_id) VALUES
('10000000-0000-0000-0000-000000000001','superadmin','{noop}Admin123!','admin01','Admin','SUPER_ADMIN',null,true,true,true,true,true,null,null,null),
('10000000-0000-0000-0000-000000000002','operator01','{noop}Operator123!','operator01','运营一号','OPERATOR',null,true,true,true,true,false,null,null,null),
('10000000-0000-0000-0000-000000000003','observer01','{noop}Observer123!','observer01','观察员一号','OBSERVER',null,true,true,false,false,false,null,null,null),
('10000000-0000-0000-0000-000000000004','makeup01','{noop}Makeup123!','makeup01','小贝老师','MAKEUP','20000000-0000-0000-0000-000000000001',true,true,false,false,false,null,null,null),
('10000000-0000-0000-0000-000000000011','streamer01','{noop}Streamer123!','streamer01','玲玲','STREAMER',null,true,true,false,false,false,'20000000-0000-0000-0000-000000000001','19:00','30000000-0000-0000-0000-000000000001'),
('10000000-0000-0000-0000-000000000012','streamer02','{noop}Streamer123!','streamer02','小狼','STREAMER',null,true,true,false,false,false,'20000000-0000-0000-0000-000000000001','19:20','30000000-0000-0000-0000-000000000002'),
('10000000-0000-0000-0000-000000000013','streamer03','{noop}Streamer123!','streamer03','米粒','STREAMER',null,true,true,false,false,false,'20000000-0000-0000-0000-000000000002','13:00','30000000-0000-0000-0000-000000000003'),
('10000000-0000-0000-0000-000000000014','streamer04','{noop}Streamer123!',null,'可可','STREAMER',null,true,true,false,false,false,null,null,'30000000-0000-0000-0000-000000000001'),
('10000000-0000-0000-0000-000000000021','disabled01','{noop}Disabled123!','disabled01','停用账号','OBSERVER',null,false,true,false,false,false,null,null,null);

INSERT INTO mock_fault_setting(fault_key) VALUES ('TOKEN_EXPIRED'),('NETWORK_TIMEOUT'),('HTTP_5XX'),('CARD_RESULT_UNKNOWN'),('CARD_DELETED'),('OUT_OF_ORDER');
