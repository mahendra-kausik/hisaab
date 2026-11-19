-- U11: the background job queue and recurring rules.

-- jobs is a system table with NO row-level security, like refresh_tokens: the poller claims a
-- job before it knows whose it is. It has no workspace_id on purpose (the rule id in the payload
-- leads to the workspace); the handler runs as user_id, so every business table it touches is
-- still protected by RLS.
CREATE TABLE jobs (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  kind       text        NOT NULL,
  payload    jsonb       NOT NULL DEFAULT '{}',
  -- At most one queued job per key, so a rule never ends up with two chains of jobs.
  dedupe_key text,
  run_at     timestamptz NOT NULL DEFAULT now(),
  status     text        NOT NULL DEFAULT 'queued'
             CHECK (status IN ('queued', 'running', 'done', 'failed')),
  attempts   int         NOT NULL DEFAULT 0,
  last_error text,
  locked_at  timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX jobs_due_idx ON jobs (run_at) WHERE status = 'queued';
CREATE UNIQUE INDEX jobs_dedupe_uq ON jobs (dedupe_key)
  WHERE status = 'queued' AND dedupe_key IS NOT NULL;

CREATE TABLE recurring_rules (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  account_id     uuid        NOT NULL,
  type           text        NOT NULL CHECK (type IN ('expense', 'income')),
  amount_paise   bigint      NOT NULL CHECK (amount_paise > 0),
  category_id    uuid,
  note           text        CHECK (char_length(note) <= 500),
  frequency      text        NOT NULL CHECK (frequency IN ('weekly', 'monthly', 'yearly')),
  -- "every N weeks/months/years"
  interval_count int         NOT NULL CHECK (interval_count BETWEEN 1 AND 52),
  -- Occurrences are anchor_date + k * interval, so a monthly rule on the 31st stays on the
  -- month's last day in short months and goes back to the 31st afterwards.
  anchor_date    date        NOT NULL,
  -- The next occurrence not yet turned into a transaction.
  next_run_on    date        NOT NULL,
  ends_on        date,
  paused_at      timestamptz,
  created_by     uuid        NOT NULL REFERENCES users (id),
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, workspace_id),
  FOREIGN KEY (account_id, workspace_id) REFERENCES accounts (id, workspace_id),
  -- Deleting a category keeps the rule, just uncategorised.
  FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id)
    ON DELETE SET NULL (category_id),
  CHECK (ends_on IS NULL OR ends_on >= anchor_date)
);
CREATE INDEX recurring_rules_workspace_idx ON recurring_rules (workspace_id);

ALTER TABLE recurring_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE recurring_rules FORCE  ROW LEVEL SECURITY;

CREATE POLICY recurring_rules_member_access ON recurring_rules FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));

-- Which rule (and which of its dates) made a transaction. The unique pair is what makes the
-- job idempotent: inserting the same occurrence twice does nothing the second time.
ALTER TABLE transactions
  ADD COLUMN recurring_rule_id uuid,
  ADD COLUMN recurrence_date   date,
  ADD CONSTRAINT transactions_recurrence_uq UNIQUE (recurring_rule_id, recurrence_date),
  -- Deleting a rule keeps the transactions it made; only the link is cleared.
  ADD FOREIGN KEY (recurring_rule_id, workspace_id) REFERENCES recurring_rules (id, workspace_id)
    ON DELETE SET NULL (recurring_rule_id);
