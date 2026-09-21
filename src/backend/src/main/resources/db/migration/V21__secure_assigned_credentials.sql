-- Usernames are login identifiers and must be unique regardless of letter case.
CREATE UNIQUE INDEX uq_app_user_username_ci
  ON app_user (lower(username))
  WHERE username IS NOT NULL;

-- Legacy assigned credentials all used the known password 123456. Accounts that
-- still require their first password change have never replaced that credential,
-- so revoke it without affecting DingTalk login or users who already changed it.
INSERT INTO audit_log(
  id, entity_type, entity_id, action, actor_name_snapshot,
  before_data, after_data, trace_id
)
SELECT gen_random_uuid(), 'USER', id, 'LEGACY_PASSWORD_REVOKED', '系统迁移',
       jsonb_build_object('username', username), '{}'::jsonb,
       'migration-v21'
FROM app_user
WHERE role <> 'SUPER_ADMIN'
  AND must_change_password = true
  AND username IS NOT NULL
  AND password_hash IS NOT NULL;

UPDATE app_user
SET username = CASE WHEN dingtalk_user_id IS NOT NULL THEN NULL ELSE username END,
    password_hash = NULL,
    must_change_password = false,
    credential_version = credential_version + 1,
    version = version + 1,
    updated_at = now()
WHERE role <> 'SUPER_ADMIN'
  AND must_change_password = true
  AND username IS NOT NULL
  AND password_hash IS NOT NULL;
