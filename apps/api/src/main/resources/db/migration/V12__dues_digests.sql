-- U18: the daily dues digest email.

-- One row per user per day once the digest has been claimed. The primary key is what makes the
-- job safe to run twice: the second insert does nothing, so nothing is sent twice. User-scoped
-- (no workspace), so the policy compares user_id; the job runs as that user.
CREATE TABLE dues_digests (
  user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  digest_date date        NOT NULL,
  sent_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, digest_date)
);

ALTER TABLE dues_digests ENABLE ROW LEVEL SECURITY;
ALTER TABLE dues_digests FORCE  ROW LEVEL SECURITY;

CREATE POLICY dues_digests_own ON dues_digests FOR ALL
  USING (user_id = app_current_user_id())
  WITH CHECK (user_id = app_current_user_id());

-- Start the daily chain for users that already exist (signup does it for new ones). The job
-- re-queues itself for the next 08:00 in the user's timezone each time it runs.
INSERT INTO jobs (user_id, kind, payload, dedupe_key)
SELECT id, 'dues.digest', to_jsonb(id::text), 'dues.digest:' || id FROM users;
