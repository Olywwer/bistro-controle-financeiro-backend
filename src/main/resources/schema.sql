CREATE TABLE IF NOT EXISTS bistro_financial_data (
  id smallint PRIMARY KEY CHECK (id = 1),
  data jsonb NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bistro_users (
  id bigserial PRIMARY KEY,
  name varchar(120) NOT NULL,
  username varchar(32),
  phone varchar(16) UNIQUE,
  password_hash varchar(100) NOT NULL,
  phone_verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE bistro_users ADD COLUMN IF NOT EXISTS username varchar(32);
ALTER TABLE bistro_users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE bistro_users ALTER COLUMN phone_verified_at DROP NOT NULL;

UPDATE bistro_users
SET username = CASE
  WHEN phone IS NOT NULL THEN regexp_replace(phone, '[^0-9]', '', 'g')
  ELSE 'usuario_' || id::text
END
WHERE username IS NULL;

ALTER TABLE bistro_users ALTER COLUMN username SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS bistro_users_username_idx
  ON bistro_users (username);

CREATE TABLE IF NOT EXISTS bistro_auth_sessions (
  token_hash char(64) PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES bistro_users(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS bistro_auth_sessions_user_id_idx
  ON bistro_auth_sessions (user_id);
