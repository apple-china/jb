-- 仅测试环境加载：统一已有超管初始凭据，并启用强制首次改密流程。
UPDATE app_user
SET username = 'superadmin',
    password_hash = '{noop}superadmin',
    must_change_password = false,
    credential_version = credential_version + 1,
    version = version + 1,
    updated_at = now()
WHERE role = 'SUPER_ADMIN';