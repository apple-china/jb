CREATE TABLE team (
  id uuid PRIMARY KEY,
  name varchar(100) NOT NULL UNIQUE,
  logo_url varchar(500),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version integer NOT NULL DEFAULT 0
);

CREATE TABLE makeup_teacher (
  id uuid PRIMARY KEY,
  name varchar(100) NOT NULL UNIQUE,
  avatar_url varchar(500),
  work_days varchar(20) NOT NULL DEFAULT '1,2,3,4,5,6,7',
  work_start time NOT NULL DEFAULT '08:00',
  work_end time NOT NULL DEFAULT '20:00',
  is_attending boolean NOT NULL DEFAULT true,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version integer NOT NULL DEFAULT 0,
  CONSTRAINT ck_teacher_work_time CHECK (work_end > work_start),
  CONSTRAINT ck_teacher_work_step CHECK (
    extract(second from work_start)=0 AND extract(second from work_end)=0
    AND mod(extract(minute from work_start)::int,10)=0
    AND mod(extract(minute from work_end)::int,10)=0
  )
);

CREATE TABLE app_user (
  id uuid PRIMARY KEY,
  username varchar(100) UNIQUE,
  password_hash varchar(255),
  dingtalk_user_id varchar(128) UNIQUE,
  display_name varchar(100) NOT NULL,
  role varchar(20) NOT NULL,
  teacher_id uuid UNIQUE REFERENCES makeup_teacher(id),
  is_active boolean NOT NULL DEFAULT true,
  is_attending boolean NOT NULL DEFAULT true,
  can_modify_appointments boolean NOT NULL DEFAULT false,
  can_cancel_appointments boolean NOT NULL DEFAULT false,
  must_change_password boolean NOT NULL DEFAULT false,
  default_team_id uuid REFERENCES team(id),
  last_teacher_id uuid REFERENCES makeup_teacher(id),
  last_start_time time,
  last_team_id uuid REFERENCES team(id),
  last_login_at timestamptz,
  credential_version integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version integer NOT NULL DEFAULT 0,
  CONSTRAINT ck_user_role CHECK (role IN ('SUPER_ADMIN','OPERATOR','OBSERVER','MAKEUP','STREAMER')),
  CONSTRAINT ck_user_login CHECK (username IS NOT NULL OR dingtalk_user_id IS NOT NULL),
  CONSTRAINT ck_makeup_teacher_link CHECK ((role='MAKEUP' AND teacher_id IS NOT NULL) OR (role<>'MAKEUP' AND teacher_id IS NULL))
);
CREATE UNIQUE INDEX uq_single_super_admin ON app_user ((role)) WHERE role='SUPER_ADMIN';

CREATE TABLE appointment (
  id uuid PRIMARY KEY,
  booking_date date NOT NULL,
  start_at timestamptz NOT NULL,
  end_at timestamptz NOT NULL,
  duration_minutes smallint NOT NULL DEFAULT 20,
  streamer_user_id uuid NOT NULL REFERENCES app_user(id),
  teacher_id uuid NOT NULL REFERENCES makeup_teacher(id),
  team_id uuid NOT NULL REFERENCES team(id),
  streamer_name_snapshot varchar(100) NOT NULL,
  teacher_name_snapshot varchar(100) NOT NULL,
  team_name_snapshot varchar(100) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  source varchar(20) NOT NULL,
  conflict_override boolean NOT NULL DEFAULT false,
  attendance_status varchar(20) NOT NULL DEFAULT 'PENDING',
  attendance_frozen boolean NOT NULL DEFAULT false,
  attendance_event_id uuid,
  attendance_evidence_at timestamptz,
  late_reminded_at timestamptz,
  version integer NOT NULL DEFAULT 0,
  created_by_user_id uuid NOT NULL REFERENCES app_user(id),
  cancelled_at timestamptz,
  cancelled_by_user_id uuid REFERENCES app_user(id),
  cancel_reason varchar(500),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_appointment_status CHECK (status IN ('ACTIVE','CANCELLED')),
  CONSTRAINT ck_appointment_source CHECK (source IN ('STREAMER','SUPER_ADMIN','OPERATOR','MAKEUP')),
  CONSTRAINT ck_appointment_attendance CHECK (attendance_status IN ('PENDING','ARRIVED','NOT_ARRIVED','LATE')),
  CONSTRAINT ck_appointment_time CHECK (end_at > start_at AND duration_minutes=20),
  CONSTRAINT ck_appointment_booking_date CHECK (booking_date = (start_at AT TIME ZONE 'Asia/Shanghai')::date)
);
CREATE UNIQUE INDEX uq_active_appointment_streamer_date ON appointment(streamer_user_id,booking_date) WHERE status='ACTIVE';
CREATE INDEX ix_active_appointment_teacher_time ON appointment(teacher_id,start_at,end_at) WHERE status='ACTIVE';

CREATE TABLE appointment_operation_counter (
  streamer_user_id uuid NOT NULL REFERENCES app_user(id),
  booking_date date NOT NULL,
  cancel_count smallint NOT NULL DEFAULT 0,
  modify_count smallint NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(streamer_user_id,booking_date),
  CONSTRAINT ck_operation_counts CHECK (cancel_count BETWEEN 0 AND 2 AND modify_count BETWEEN 0 AND 3)
);

CREATE TABLE gate_event (
  id uuid PRIMARY KEY,
  external_event_id varchar(200) NOT NULL UNIQUE,
  org_id varchar(128),
  device_sn varchar(128),
  dingtalk_user_id varchar(128) NOT NULL,
  occurred_at timestamptz NOT NULL,
  event_type varchar(40) NOT NULL DEFAULT 'REC_SUCCESS',
  raw_payload jsonb NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_gate_event_identity_time ON gate_event(dingtalk_user_id,occurred_at);
ALTER TABLE appointment ADD CONSTRAINT fk_appointment_gate_event FOREIGN KEY(attendance_event_id) REFERENCES gate_event(id);

CREATE TABLE audit_log (
  id uuid PRIMARY KEY,
  entity_type varchar(40) NOT NULL,
  entity_id uuid,
  entity_key varchar(200),
  action varchar(40) NOT NULL,
  actor_user_id uuid REFERENCES app_user(id),
  actor_identity_snapshot varchar(128),
  actor_name_snapshot varchar(100) NOT NULL,
  reason varchar(500),
  before_data jsonb,
  after_data jsonb,
  trace_id varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log(entity_type,entity_id,created_at);

CREATE TABLE idempotency_record (
  actor_user_id uuid NOT NULL REFERENCES app_user(id),
  operation varchar(50) NOT NULL,
  idempotency_key varchar(100) NOT NULL,
  request_hash char(64) NOT NULL,
  status varchar(20) NOT NULL,
  response_status integer,
  response_body jsonb,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(actor_user_id,operation,idempotency_key),
  CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING','SUCCEEDED','FAILED_RETRYABLE'))
);

CREATE TABLE system_setting (
  key varchar(80) PRIMARY KEY,
  boolean_value boolean NOT NULL,
  updated_by_user_id uuid REFERENCES app_user(id),
  version integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE daily_card (
  id uuid PRIMARY KEY,
  business_date date NOT NULL,
  group_open_conversation_id varchar(128) NOT NULL,
  out_track_id varchar(128) NOT NULL UNIQUE,
  template_id varchar(128) NOT NULL,
  status varchar(20) NOT NULL,
  content_version bigint NOT NULL DEFAULT 0,
  delivered_version bigint NOT NULL DEFAULT 0,
  last_error_code varchar(100),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_daily_card_group_date UNIQUE(group_open_conversation_id,business_date),
  CONSTRAINT ck_daily_card_status CHECK(status IN ('PENDING','ACTIVE','INVALID','FAILED'))
);

CREATE TABLE late_notification (
  appointment_id uuid PRIMARY KEY REFERENCES appointment(id),
  out_track_id varchar(128) NOT NULL UNIQUE,
  dingtalk_user_id varchar(128) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'PENDING',
  attempt_count smallint NOT NULL DEFAULT 0,
  last_error_code varchar(100),
  created_at timestamptz NOT NULL DEFAULT now(),
  sent_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_late_notification_status CHECK(status IN ('PENDING','SENT','FAILED'))
);

CREATE TABLE integration_job (
  id uuid PRIMARY KEY,
  job_type varchar(40) NOT NULL,
  business_key varchar(200) NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  status varchar(20) NOT NULL,
  attempt_count smallint NOT NULL DEFAULT 0,
  max_attempts smallint NOT NULL DEFAULT 5,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  locked_at timestamptz,
  locked_by varchar(100),
  last_error_code varchar(100),
  last_error_message varchar(300),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_integration_job_status CHECK(status IN ('PENDING','RUNNING','RETRY_WAIT','SUCCEEDED','DEAD'))
);
CREATE UNIQUE INDEX uq_active_integration_job ON integration_job(job_type,business_key) WHERE status IN ('PENDING','RUNNING','RETRY_WAIT');
CREATE INDEX ix_job_claim ON integration_job(status,next_attempt_at);

CREATE TABLE mock_card_delivery (
  out_track_id varchar(128) PRIMARY KEY,
  group_id varchar(128) NOT NULL,
  business_date date NOT NULL,
  card_data jsonb NOT NULL,
  private_data jsonb NOT NULL,
  content_version bigint NOT NULL,
  status varchar(20) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE mock_card_call_log (
  id uuid PRIMARY KEY,
  out_track_id varchar(128) NOT NULL,
  operation varchar(20) NOT NULL,
  content_version bigint NOT NULL,
  result_code varchar(100) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE mock_fault_setting (
  fault_key varchar(80) PRIMARY KEY,
  enabled boolean NOT NULL DEFAULT false,
  remaining_count integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO system_setting(key,boolean_value) VALUES('SYSTEM_ENABLED',true);
