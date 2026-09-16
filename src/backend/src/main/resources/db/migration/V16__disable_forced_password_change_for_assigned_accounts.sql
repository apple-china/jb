UPDATE app_user
SET must_change_password = false,
    credential_version = credential_version + 1,
    version = version + 1,
    updated_at = now()
WHERE role <> 'SUPER_ADMIN'
  AND username IS NOT NULL
  AND must_change_password = true;
