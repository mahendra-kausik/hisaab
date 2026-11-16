-- U2: refresh tokens for login sessions. Runs as hisaab_owner; V1's default
-- privileges already grant hisaab_app CRUD on this table.
--
-- No RLS, for the same reason as `users`: a refresh request arrives before any
-- user is known (the token itself is the credential), and the table belongs to
-- a user, not a workspace. The raw token is never stored, only its SHA-256 hash.
--
-- A "family" is every token descended from one login. Each refresh revokes the
-- old token and issues a new one in the same family. Presenting an already
-- revoked token means it was stolen or replayed, so the whole family is revoked.
CREATE TABLE refresh_tokens (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash text        NOT NULL UNIQUE,
  family_id  uuid        NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_family_id_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
