CREATE TABLE auth_session (
  token_hash char(64) PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  credential_version integer NOT NULL,
  csrf_token varchar(128) NOT NULL,
  mock_login boolean NOT NULL DEFAULT false,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_auth_session_user ON auth_session(user_id);
CREATE INDEX ix_auth_session_expiry ON auth_session(expires_at);
