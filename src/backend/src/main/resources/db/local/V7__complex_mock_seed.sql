-- 仅供 local/test 空库使用；动态业务日期以中国标准时间为准。
SET LOCAL TIME ZONE 'Asia/Shanghai';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM app_user)
     OR EXISTS (SELECT 1 FROM appointment)
     OR EXISTS (SELECT 1 FROM makeup_artist)
     OR EXISTS (SELECT 1 FROM team) THEN
    RAISE EXCEPTION 'V7 complex mock seed requires an empty business schema';
  END IF;
END $$;

-- format 的宽度填充为空格；稳定 UUID 使用 lpad 补零。
INSERT INTO team(id,name,logo_url,is_active,created_at,updated_at)
SELECT ('30000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,
       CASE WHEN i=1 THEN '星河一团' ELSE '测试团队' || to_char(i,'FM00') END,
       format('/brand/team-placeholder-%s.svg',((i-1)%3)+1),
       i<>10,clock_timestamp()-(10-i)*interval '1 day',clock_timestamp()
FROM generate_series(1,10) AS s(i);

-- 停用排班仍保留最后有效时段，便于重新开启后恢复。
INSERT INTO makeup_artist(id,name,avatar_url,work_days,work_start,work_end,
                          schedule_enabled,is_attending,is_active,created_at,updated_at)
SELECT ('20000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,
       '化妆师' || to_char(i,'FM00'),
       format('/brand/makeup-artist-placeholder-%s.svg',((i-1)%3)+1),
       CASE i%3 WHEN 0 THEN '1,2,3,4,5' WHEN 1 THEN '1,2,3,4,5,6,7' ELSE '2,4,6' END,
       CASE i%3 WHEN 0 THEN time '08:00' WHEN 1 THEN time '06:00' ELSE time '10:00' END,
       CASE i%3 WHEN 0 THEN time '18:00' WHEN 1 THEN time '20:00' ELSE time '22:00' END,
       i%4<>0,i%3<>0,i<>10,clock_timestamp()-interval '10 days',clock_timestamp()
FROM generate_series(1,10) AS s(i);

-- 兼容 UUID 1/2/3/4、主播 11..14 和 superadmin 登录名。
-- 41 个钉钉身份匹配目录；50 个候选员工不注册。
WITH people(role,n,prefix,label) AS (
  SELECT 'SUPER_ADMIN',1,'admin','超管'
  UNION ALL SELECT 'OPERATOR',i,'operator','运营' FROM generate_series(1,5) s(i)
  UNION ALL SELECT 'OBSERVER',i,'observer','观察员' FROM generate_series(1,5) s(i)
  UNION ALL SELECT 'MAKEUP',i,'makeup','化妆师' FROM generate_series(1,10) s(i)
  UNION ALL SELECT 'STREAMER',i,'streamer','主播' FROM generate_series(1,30) s(i)
), identities AS (
  SELECT *,prefix || to_char(n,'FM00') AS directory_id,
         CASE WHEN role='SUPER_ADMIN' THEN label ELSE label || to_char(n,'FM00') END AS name,
         CASE role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'OPERATOR' THEN CASE WHEN n=1 THEN 2 ELSE 100+n END
           WHEN 'OBSERVER' THEN CASE WHEN n=1 THEN 3 ELSE 200+n END
           WHEN 'MAKEUP' THEN CASE WHEN n=1 THEN 4 ELSE 300+n END ELSE 10+n END AS user_no
  FROM people
)
INSERT INTO app_user(id,username,password_hash,dingtalk_user_id,dingtalk_username,nickname,role,
                     makeup_artist_id,is_active,is_attending,can_create_appointments,
                     can_modify_appointments,can_cancel_appointments,must_change_password,
                     default_team_id,last_makeup_artist_id,last_start_time,last_team_id,created_at,updated_at)
SELECT ('10000000-0000-0000-0000-' || lpad(user_no::text,12,'0'))::uuid,
       CASE WHEN role='SUPER_ADMIN' THEN 'superadmin' ELSE directory_id END,
       CASE role WHEN 'SUPER_ADMIN' THEN '{noop}Admin123!' WHEN 'OPERATOR' THEN '{noop}Operator123!'
         WHEN 'OBSERVER' THEN '{noop}Observer123!' WHEN 'MAKEUP' THEN '{noop}Makeup123!' ELSE '{noop}Streamer123!' END,
       CASE WHEN role='STREAMER' AND n>20 THEN NULL ELSE directory_id END,
       CASE WHEN role='STREAMER' AND n>20 THEN NULL ELSE name END,name,role,
       CASE WHEN role='MAKEUP' THEN ('20000000-0000-0000-0000-' || lpad(n::text,12,'0'))::uuid END,
       CASE WHEN role IN ('OPERATOR','OBSERVER') THEN n<>5 WHEN role='MAKEUP' THEN n<>10 ELSE true END,
       CASE WHEN role='MAKEUP' THEN n%3<>0 ELSE true END,
       role='SUPER_ADMIN' OR (role IN ('OPERATOR','MAKEUP') AND n%2=1),
       role='SUPER_ADMIN' OR (role IN ('OPERATOR','MAKEUP') AND n%3<>0),
       role='SUPER_ADMIN' OR (role IN ('OPERATOR','MAKEUP') AND n%2=1),role='SUPER_ADMIN',
       CASE WHEN role='STREAMER' THEN ('30000000-0000-0000-0000-' || lpad((((n-1)%10)+1)::text,12,'0'))::uuid END,
       CASE WHEN role='STREAMER' THEN ('20000000-0000-0000-0000-' || lpad((((n-1)%10)+1)::text,12,'0'))::uuid END,
       CASE WHEN role='STREAMER' THEN time '06:00'+(n-1)*interval '10 minutes' END,
       CASE WHEN role='STREAMER' THEN ('30000000-0000-0000-0000-' || lpad((((n-1)%10)+1)::text,12,'0'))::uuid END,
       clock_timestamp()-interval '10 days',clock_timestamp()
FROM identities;

-- 每位化妆师的有效时段相隔 100 分钟，不重叠；取消记录可复用槽位。
WITH spec(day_offset,total,active_count) AS (
  VALUES (0,100,30),(1,100,30),(-1,34,18),(-2,33,17),(-3,33,16)
), rows AS (
  SELECT day_offset,total,active_count,n,current_date+day_offset AS booking_date,
         ((n-1)%30)+1 AS streamer_no,((n-1)%10)+1 AS makeup_no,((n-1)%10)+1 AS team_no
  FROM spec CROSS JOIN LATERAL generate_series(1,total) AS g(n)
), resolved AS (
  SELECT r.*,u.id AS streamer_id,u.nickname,m.id AS artist_id,m.name AS artist_name,t.id AS group_id,t.name AS group_name,
         (ARRAY['STREAMER','SUPER_ADMIN','OPERATOR','MAKEUP'])[n%4+1] AS source,
         CASE n%4 WHEN 0 THEN u.id WHEN 1 THEN admin.id WHEN 2 THEN operator.id ELSE makeup.id END AS creator_id,
         (booking_date+time '06:00'+((n-1)%72)*interval '10 minutes') AT TIME ZONE 'Asia/Shanghai' AS starts,
         clock_timestamp()-interval '10 days'+(day_offset+3)*interval '1 day'+n*interval '1 second' AS created,
         clock_timestamp()-interval '1 hour'+n*interval '1 second' AS updated
  FROM rows r
  JOIN app_user u ON u.username='streamer' || to_char(streamer_no,'FM00')
  JOIN makeup_artist m ON m.id=('20000000-0000-0000-0000-' || lpad(makeup_no::text,12,'0'))::uuid
  JOIN team t ON t.id=('30000000-0000-0000-0000-' || lpad(team_no::text,12,'0'))::uuid
  JOIN app_user admin ON admin.dingtalk_user_id='admin01'
  JOIN app_user operator ON operator.username='operator' || to_char(((n-1)%5)+1,'FM00')
  JOIN app_user makeup ON makeup.makeup_artist_id=m.id AND makeup.role='MAKEUP'
)
INSERT INTO appointment(id,booking_date,start_at,end_at,duration_minutes,streamer_user_id,makeup_artist_id,team_id,
                        streamer_name_snapshot,makeup_artist_name_snapshot,team_name_snapshot,status,source,
                        conflict_override,attendance_status,attendance_frozen,created_by_user_id,
                        cancelled_at,cancelled_by_user_id,cancel_reason,created_at,updated_at)
SELECT ('40000000-0000-0000-' || lpad((day_offset+3)::text,4,'0') || '-' || lpad(n::text,12,'0'))::uuid,
       booking_date,starts,starts+interval '20 minutes',20,streamer_id,artist_id,group_id,nickname,artist_name,group_name,
       CASE WHEN n<=active_count THEN 'ACTIVE' ELSE 'CANCELLED' END,source,
       source<>'STREAMER' AND n%37=0,
       CASE WHEN day_offset>=0 THEN 'PENDING' ELSE (ARRAY['ARRIVED','LATE','NOT_ARRIVED','PENDING'])[n%4+1] END,
       n>active_count,creator_id,
       CASE WHEN n>active_count THEN updated-interval '30 minutes' END,
       CASE WHEN n>active_count THEN creator_id END,
       CASE WHEN n>active_count THEN (ARRAY['主播临时调整直播安排','团队拍摄计划变更','化妆时段重新协调'])[n%3+1] END,
       created,updated
FROM resolved;

-- 每个历史动作都有结构化快照；修改的 after 对应当前预约。
INSERT INTO audit_log(id,entity_type,entity_id,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,
                      before_data,after_data,reason,trace_id,created_at)
SELECT ('50000000-0000-0000-' || substring(a.id::text,20,4) || '-' || right(a.id::text,12))::uuid,
       'APPOINTMENT',a.id,'CREATE',u.id,coalesce(u.dingtalk_user_id,u.username),u.nickname,NULL,
       jsonb_build_object('bookingDate',a.booking_date,'startTime',to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI'),
         'makeupArtistId',a.makeup_artist_id,'teamId',a.team_id,'conflictOverride',a.conflict_override),
       CASE WHEN u.id<>a.streamer_user_id THEN '代主播预约' END,'mock-create-' || a.id,a.created_at
FROM appointment a JOIN app_user u ON u.id=a.created_by_user_id;

INSERT INTO audit_log(id,entity_type,entity_id,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,
                      before_data,after_data,reason,trace_id,created_at)
SELECT ('50000000-0000-0000-' || lpad((substring(a.id::text,20,4)::int+10)::text,4,'0') || '-' || right(a.id::text,12))::uuid,
       'APPOINTMENT',a.id,'MODIFY',u.id,u.dingtalk_user_id,u.nickname,
       jsonb_build_object('startTime',to_char((a.start_at-interval '10 minutes') AT TIME ZONE 'Asia/Shanghai','HH24:MI'),
         'makeupArtistId',('20000000-0000-0000-0000-' || lpad(((right(a.id::text,12)::int%10)+1)::text,12,'0')),
         'teamId',('30000000-0000-0000-0000-' || lpad(((right(a.id::text,12)::int%10)+1)::text,12,'0')),'conflictOverride',false),
       jsonb_build_object('startTime',to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI'),
         'makeupArtistId',a.makeup_artist_id,'teamId',a.team_id,'conflictOverride',a.conflict_override),
       '协调化妆师与团队时间','mock-modify-' || a.id,a.updated_at-interval '40 minutes'
FROM appointment a JOIN app_user u ON u.dingtalk_user_id='admin01' WHERE right(a.id::text,12)::int%7=0;

INSERT INTO audit_log(id,entity_type,entity_id,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,
                      before_data,after_data,reason,trace_id,created_at)
SELECT ('50000000-0000-0000-' || lpad((substring(a.id::text,20,4)::int+20)::text,4,'0') || '-' || right(a.id::text,12))::uuid,
       'APPOINTMENT',a.id,'CANCEL',u.id,coalesce(u.dingtalk_user_id,u.username),u.nickname,
       jsonb_build_object('status','ACTIVE'),jsonb_build_object('status','CANCELLED','cancelReason',a.cancel_reason),
       a.cancel_reason,'mock-cancel-' || a.id,a.cancelled_at
FROM appointment a JOIN app_user u ON u.id=a.cancelled_by_user_id WHERE a.status='CANCELLED';

WITH auth_scenario AS (
  SELECT i,(ARRAY['LOGIN_SUCCESS','LOGIN_FAILED','LOGIN_DENIED','PERMISSION_DENIED'])[((i-1)%4)+1] AS action,
         (ARRAY['operator01','operator01','operator05','operator02'])[((i-1)%4)+1] AS username,
         (ARRAY['登录成功','密码错误','账号已停用','当前账号没有预约权限'])[((i-1)%4)+1] AS reason
  FROM generate_series(1,12) s(i)
)
INSERT INTO audit_log(id,entity_type,entity_key,action,actor_user_id,actor_identity_snapshot,actor_name_snapshot,
                      reason,after_data,trace_id,created_at)
SELECT ('50000000-0000-0000-0040-' || lpad(s.i::text,12,'0'))::uuid,'AUTH',
       CASE WHEN s.action='PERMISSION_DENIED' THEN '/api/v1/appointments' ELSE u.username END,
       s.action,u.id,u.dingtalk_user_id,u.nickname,s.reason,
       CASE WHEN s.action='PERMISSION_DENIED'
         THEN jsonb_build_object('method','POST','path','/api/v1/appointments','code','FORBIDDEN')
         ELSE jsonb_build_object('method','PASSWORD','identity',u.username) END,
       'mock-auth-' || s.i,clock_timestamp()-s.i*interval '15 minutes'
FROM auth_scenario s JOIN app_user u ON u.username=s.username;

INSERT INTO audit_log(id,entity_type,entity_key,action,actor_name_snapshot,reason,after_data,trace_id,created_at)
SELECT ('50000000-0000-0000-0041-' || lpad(i::text,12,'0'))::uuid,
       (ARRAY['SYSTEM','TELEMETRY','ERROR'])[i%3+1],'mock-system-' || i,
       (ARRAY['SETTING_UPDATED','CLIENT_EVENT','INTEGRATION_FAILED'])[i%3+1],'本地模拟系统',
       (ARRAY['系统开放状态检查','页面交互遥测','外部服务网络超时'])[i%3+1],
       jsonb_build_object('eventName','mock-scenario','durationMs',i*17,'errorCode',CASE WHEN i%3=2 THEN 'NETWORK_TIMEOUT' END),
       'mock-system-' || i,clock_timestamp()-i*interval '10 minutes'
FROM generate_series(1,12) s(i);

-- 密码专用主播没有钉钉身份，不伪造门禁证据；已绑定身份的到达/迟到均可追溯。
INSERT INTO gate_event(id,external_event_id,org_id,device_sn,dingtalk_user_id,occurred_at,raw_payload,received_at)
SELECT ('60000000' || substring(a.id::text,9))::uuid,'mock-gate-' || a.id,'mock-org','mock-gate-01',u.dingtalk_user_id,
       a.start_at+CASE WHEN a.attendance_status='LATE' THEN interval '12 minutes' ELSE interval '-5 minutes' END,
       jsonb_build_object('appointmentId',a.id,'eventType','REC_SUCCESS','userId',u.dingtalk_user_id),a.start_at+interval '15 minutes'
FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id
WHERE a.booking_date<current_date AND a.attendance_status IN ('ARRIVED','LATE') AND u.dingtalk_user_id IS NOT NULL;

UPDATE appointment a SET attendance_event_id=g.id,attendance_evidence_at=g.occurred_at
FROM gate_event g WHERE g.external_event_id='mock-gate-' || a.id;

-- 用户级每日次数有上限；取消历史包括管理人员代操作。
INSERT INTO appointment_operation_counter(streamer_user_id,booking_date,cancel_count,modify_count)
SELECT streamer_user_id,booking_date,least(count(*) FILTER (WHERE status='CANCELLED'),2),
       least(count(*) FILTER (WHERE right(id::text,12)::int%7=0),3)
FROM appointment GROUP BY streamer_user_id,booking_date;

INSERT INTO daily_card(id,business_date,group_open_conversation_id,out_track_id,template_id,status,
                       content_version,delivered_version,first_delivered_at,last_error_code,created_at,updated_at)
SELECT ('70000000-0000-0000-0000-' || lpad((i+1)::text,12,'0'))::uuid,current_date+i,'mock-group-001',
       'mock-schedule-' || (current_date+i),'mock-schedule-template',CASE WHEN i=0 THEN 'ACTIVE' ELSE 'FAILED' END,
       3,CASE WHEN i=0 THEN 3 ELSE 0 END,CASE WHEN i=0 THEN clock_timestamp()-interval '2 hours' END,
       CASE WHEN i=1 THEN 'NETWORK_TIMEOUT' END,clock_timestamp()-interval '3 hours',clock_timestamp()-interval '1 hour'
FROM generate_series(0,1) s(i);

INSERT INTO mock_card_delivery(out_track_id,group_id,business_date,card_data,private_data,content_version,status)
SELECT c.out_track_id,c.group_open_conversation_id,c.business_date,
       jsonb_build_object('title','加贝云·化妆安排','date_text',c.business_date::text,
         'schedule_markdown',(SELECT string_agg(to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') || '  ' ||
           a.streamer_name_snapshot || '  ' || a.makeup_artist_name_snapshot || '  ' || a.team_name_snapshot,E'\n\n' ORDER BY a.start_at)
           FROM appointment a WHERE a.booking_date=c.business_date AND a.status='ACTIVE'),
         'summary','共30条预约','entry_url','http://127.0.0.1:5173/booking'),
       (SELECT jsonb_object_agg(u.dingtalk_user_id,jsonb_build_object('my_appointment',
          '我的预约 ' || to_char(a.start_at AT TIME ZONE 'Asia/Shanghai','HH24:MI') || '  ' || a.makeup_artist_name_snapshot || '  ' || a.team_name_snapshot))
        FROM app_user u JOIN appointment a ON a.streamer_user_id=u.id AND a.booking_date=c.business_date AND a.status='ACTIVE'
        WHERE u.role='STREAMER' AND u.dingtalk_user_id IS NOT NULL),
       c.delivered_version,'ACTIVE'
FROM daily_card c WHERE c.first_delivered_at IS NOT NULL;

INSERT INTO mock_card_call_log(id,out_track_id,operation,content_version,result_code,created_at)
SELECT ('71000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,'mock-schedule-' || (current_date+(i%2)),
       CASE WHEN i<=2 THEN 'CREATE' ELSE 'UPDATE' END,3,CASE WHEN i%2=0 THEN 'OK' ELSE 'NETWORK_TIMEOUT' END,
       clock_timestamp()-i*interval '20 minutes'
FROM generate_series(1,6) s(i);

INSERT INTO late_notification(appointment_id,out_track_id,dingtalk_user_id,status,attempt_count,sent_at,created_at,updated_at)
SELECT a.id,'mock-late-' || a.id,u.dingtalk_user_id,'SENT',1,a.start_at+interval '10 minutes',
       a.start_at+interval '9 minutes',a.start_at+interval '10 minutes'
FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id
WHERE a.booking_date<current_date AND a.status='ACTIVE' AND a.attendance_status='LATE' AND u.dingtalk_user_id IS NOT NULL;
UPDATE appointment a SET late_reminded_at=n.sent_at FROM late_notification n WHERE n.appointment_id=a.id;

INSERT INTO idempotency_record(actor_user_id,operation,idempotency_key,request_hash,status,response_status,response_body,expires_at)
SELECT u.id,'CREATE_APPOINTMENT','mock-idempotency-' || i,repeat(md5('mock-request-' || i),2),
       (ARRAY['PROCESSING','SUCCEEDED','FAILED_RETRYABLE'])[i%3+1],
       CASE i%3 WHEN 1 THEN 200 WHEN 2 THEN 503 END,
       CASE i%3 WHEN 1 THEN '{"success":true}'::jsonb WHEN 2 THEN '{"error":"NETWORK_TIMEOUT"}'::jsonb END,
       clock_timestamp()+interval '1 day'
FROM generate_series(1,6) s(i) JOIN app_user u ON u.dingtalk_user_id='admin01';

INSERT INTO mock_fault_setting(fault_key,enabled,remaining_count) VALUES
  ('TOKEN_EXPIRED',false,0),('NETWORK_TIMEOUT',false,0),('HTTP_5XX',false,0),
  ('CARD_RESULT_UNKNOWN',false,0),('CARD_DELETED',false,0),('OUT_OF_ORDER',false,0);

-- 三个活动任务使用不同业务键；历史成功/死信可与同一卡片共享业务键。
INSERT INTO integration_job(id,job_type,business_key,payload,status,attempt_count,max_attempts,
                            next_attempt_at,locked_at,locked_by,last_error_code,last_error_message)
SELECT ('80000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,
       CASE WHEN i=2 THEN 'LATE_REMINDER' ELSE 'CARD_REFRESH' END,
       CASE WHEN i=2 THEN (SELECT appointment_id::text FROM late_notification ORDER BY appointment_id LIMIT 1)
         ELSE 'mock-group-001|' || (current_date+CASE WHEN i IN (3,5) THEN 1 ELSE 0 END) END,
       CASE WHEN i=2 THEN jsonb_build_object('appointmentId',(SELECT appointment_id FROM late_notification ORDER BY appointment_id LIMIT 1))
         ELSE jsonb_build_object('businessDate',current_date+CASE WHEN i IN (3,5) THEN 1 ELSE 0 END,'groupId','mock-group-001') END,
       (ARRAY['PENDING','RUNNING','RETRY_WAIT','SUCCEEDED','DEAD'])[i],
       CASE WHEN i=5 THEN 5 WHEN i=1 THEN 0 ELSE 1 END,5,
       CASE WHEN i=3 THEN clock_timestamp()-interval '1 minute' ELSE clock_timestamp()+interval '1 day' END,
       CASE WHEN i=2 THEN clock_timestamp() END,CASE WHEN i=2 THEN 'mock-worker' END,
       CASE WHEN i IN (3,5) THEN 'NETWORK_TIMEOUT' END,CASE WHEN i IN (3,5) THEN '模拟卡片调用超时' END
FROM generate_series(1,5) s(i);

-- 库内合同与外部测试双重校验，任一偏差使整个迁移回滚。
DO $$
DECLARE
  actual_count integer;
  expected record;
BEGIN
  FOR expected IN SELECT * FROM (VALUES ('SUPER_ADMIN',1),('OPERATOR',5),('OBSERVER',5),('MAKEUP',10),('STREAMER',30)) AS e(role,total) LOOP
    SELECT count(*) INTO actual_count FROM app_user WHERE role=expected.role;
    IF actual_count<>expected.total THEN RAISE EXCEPTION '% user count expected %, got %',expected.role,expected.total,actual_count; END IF;
  END LOOP;
  SELECT count(*) INTO actual_count FROM makeup_artist;
  IF actual_count<>10 THEN RAISE EXCEPTION 'makeup artist count expected 10, got %',actual_count; END IF;
  SELECT count(*) INTO actual_count FROM team;
  IF actual_count<>10 THEN RAISE EXCEPTION 'team count expected 10, got %',actual_count; END IF;
  FOR expected IN SELECT * FROM (VALUES (0,100,30),(1,100,30),(-1,34,18),(-2,33,17),(-3,33,16)) AS e(day_offset,total,active_count) LOOP
    SELECT count(*) INTO actual_count FROM appointment WHERE booking_date=current_date+expected.day_offset;
    IF actual_count<>expected.total THEN RAISE EXCEPTION 'day % appointment count expected %, got %',expected.day_offset,expected.total,actual_count; END IF;
    SELECT count(*) INTO actual_count FROM appointment WHERE booking_date=current_date+expected.day_offset AND status='ACTIVE';
    IF actual_count<>expected.active_count THEN RAISE EXCEPTION 'day % active count expected %, got %',expected.day_offset,expected.active_count,actual_count; END IF;
    SELECT count(*) INTO actual_count FROM appointment WHERE booking_date=current_date+expected.day_offset AND status='CANCELLED';
    IF actual_count<>expected.total-expected.active_count THEN RAISE EXCEPTION 'day % cancelled count expected %, got %',expected.day_offset,expected.total-expected.active_count,actual_count; END IF;
  END LOOP;
  SELECT count(*) INTO actual_count FROM appointment;
  IF actual_count<>300 THEN RAISE EXCEPTION 'appointment count expected 300, got %',actual_count; END IF;
  SELECT count(*) INTO actual_count FROM appointment WHERE end_at-start_at<>interval '20 minutes' OR extract(minute FROM start_at AT TIME ZONE 'Asia/Shanghai')::int%10<>0;
  IF actual_count<>0 THEN RAISE EXCEPTION 'invalid appointment time count expected 0, got %',actual_count; END IF;
  SELECT count(*) INTO actual_count FROM (SELECT streamer_user_id,booking_date FROM appointment WHERE status='ACTIVE' GROUP BY 1,2 HAVING count(*)>1) duplicates;
  IF actual_count<>0 THEN RAISE EXCEPTION 'duplicate active streamer date count expected 0, got %',actual_count; END IF;
  SELECT count(*) INTO actual_count FROM appointment a JOIN appointment b ON a.id<b.id AND a.makeup_artist_id=b.makeup_artist_id AND a.start_at<b.end_at AND b.start_at<a.end_at WHERE a.status='ACTIVE' AND b.status='ACTIVE';
  IF actual_count<>0 THEN RAISE EXCEPTION 'overlapping active makeup appointments expected 0, got %',actual_count; END IF;
  SELECT count(*) INTO actual_count FROM appointment WHERE created_by_user_id<>streamer_user_id;
  IF actual_count=0 THEN RAISE EXCEPTION 'delegated appointment count expected positive, got %',actual_count; END IF;
  FOR expected IN SELECT * FROM (VALUES ('CREATE',300),('MODIFY',40),('CANCEL',189)) AS e(action,total) LOOP
    SELECT count(*) INTO actual_count FROM audit_log WHERE entity_type='APPOINTMENT' AND action=expected.action;
    IF actual_count<>expected.total THEN RAISE EXCEPTION '% audit count expected %, got %',expected.action,expected.total,actual_count; END IF;
  END LOOP;
  SELECT count(DISTINCT action) INTO actual_count FROM audit_log WHERE entity_type='AUTH';
  IF actual_count<>4 THEN RAISE EXCEPTION 'AUTH action count expected 4, got %',actual_count; END IF;
  SELECT count(DISTINCT status) INTO actual_count FROM integration_job;
  IF actual_count<>5 THEN RAISE EXCEPTION 'job status count expected 5, got %',actual_count; END IF;
END $$;
