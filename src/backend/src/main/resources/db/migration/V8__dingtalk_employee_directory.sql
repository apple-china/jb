CREATE TABLE dingtalk_employee (
  user_id varchar(128) PRIMARY KEY,
  name varchar(100) NOT NULL,
  union_id varchar(128),
  department_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz,
  last_synced_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_dingtalk_employee_deleted CHECK (
    (is_active AND deleted_at IS NULL) OR (NOT is_active AND deleted_at IS NOT NULL)
  )
);

CREATE INDEX ix_dingtalk_employee_active_name
  ON dingtalk_employee (is_active, lower(name));
