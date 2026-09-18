ALTER TABLE auth_session
  ADD COLUMN password_login boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN auth_session.password_login IS
  'True only for username/password sessions; DingTalk and local mock sessions remain false.';
