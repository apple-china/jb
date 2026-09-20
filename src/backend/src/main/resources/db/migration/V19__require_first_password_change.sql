-- Accounts which have never completed a password login must replace the
-- administrator-assigned initial password. DingTalk logins are intentionally
-- ignored because they authenticate independently.
UPDATE app_user u
SET must_change_password = true,
    credential_version = credential_version + 1,
    version = version + 1,
    updated_at = now()
WHERE u.role <> 'SUPER_ADMIN'
  AND u.username IS NOT NULL
  AND u.password_hash IS NOT NULL
  AND u.must_change_password = false
  AND NOT EXISTS (
    SELECT 1
    FROM audit_log a
    WHERE a.entity_type = 'AUTH'
      AND a.entity_id = u.id
      AND a.action = 'LOGIN_SUCCESS'
      AND a.after_data ->> 'method' = 'PASSWORD'
  );
