-- U4: transactions, tags and the transaction-tag link, all workspace-owned and protected by RLS.
-- Runs as hisaab_owner; V1's default privileges already grant hisaab_app CRUD.
-- party_id, project_id, due_id, recurring_rule_id and recurrence_date are added by the units
-- that create the tables they point to (U10, U14-U16).

-- Targets for the composite foreign keys below. Foreign key checks ignore RLS, so pointing at
-- (id, workspace_id) is what lets the database refuse a row that links another workspace's data.
ALTER TABLE accounts   ADD CONSTRAINT accounts_id_workspace_uq   UNIQUE (id, workspace_id);
ALTER TABLE categories ADD CONSTRAINT categories_id_workspace_uq UNIQUE (id, workspace_id);

CREATE TABLE transactions (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id      uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  account_id        uuid        NOT NULL,
  type              text        NOT NULL
                    CHECK (type IN ('expense', 'income', 'transfer_out', 'transfer_in')),
  -- Always positive; the direction comes from the type.
  amount_paise      bigint      NOT NULL CHECK (amount_paise > 0),
  -- The user's local calendar date, not a UTC instant.
  occurred_on       date        NOT NULL,
  category_id       uuid,
  payment_method    text
                    CHECK (payment_method IN ('cash', 'upi', 'card', 'bank_transfer', 'cheque', 'other')),
  note              text        CHECK (char_length(note) <= 500),
  -- Set on both rows of a transfer (U5).
  transfer_group_id uuid,
  source            text        NOT NULL DEFAULT 'manual'
                    CHECK (source IN ('manual', 'statement', 'email', 'sms', 'shortcut')),
  -- Reserved for the Phase 2 import pipeline.
  raw_description   text,
  merchant          text,
  created_by        uuid        NOT NULL REFERENCES users (id),
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  deleted_at        timestamptz,
  UNIQUE (id, workspace_id),
  FOREIGN KEY (account_id, workspace_id)  REFERENCES accounts (id, workspace_id),
  -- A NULL category_id skips this check (default MATCH SIMPLE), so category stays optional.
  FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id),
  CHECK ((type IN ('transfer_out', 'transfer_in')) = (transfer_group_id IS NOT NULL)),
  CHECK (type NOT IN ('transfer_out', 'transfer_in') OR category_id IS NULL)
);

-- The list query: one workspace, newest first, live rows only. Matches the sort and the cursor.
CREATE INDEX transactions_list_idx
  ON transactions (workspace_id, occurred_on DESC, created_at DESC, id DESC)
  WHERE deleted_at IS NULL;
-- Account balances sum over one account's live rows.
CREATE INDEX transactions_account_idx ON transactions (account_id) WHERE deleted_at IS NULL;
-- Category filters, and the foreign key check when a category is deleted.
CREATE INDEX transactions_category_idx ON transactions (category_id);

CREATE TABLE tags (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 50),
  created_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, workspace_id)
);
-- Tag names are unique per workspace, ignoring case. Also serves workspace_id lookups.
CREATE UNIQUE INDEX tags_workspace_name_uq ON tags (workspace_id, lower(name));

-- workspace_id is repeated here so this table follows the standard RLS pattern, and the
-- composite foreign keys keep a transaction and its tags in the same workspace.
CREATE TABLE transaction_tags (
  workspace_id   uuid NOT NULL,
  transaction_id uuid NOT NULL,
  tag_id         uuid NOT NULL,
  PRIMARY KEY (transaction_id, tag_id),
  FOREIGN KEY (transaction_id, workspace_id) REFERENCES transactions (id, workspace_id)
    ON DELETE CASCADE,
  FOREIGN KEY (tag_id, workspace_id) REFERENCES tags (id, workspace_id) ON DELETE CASCADE
);
CREATE INDEX transaction_tags_tag_idx ON transaction_tags (tag_id);

ALTER TABLE transactions     ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions     FORCE  ROW LEVEL SECURITY;
ALTER TABLE tags             ENABLE ROW LEVEL SECURITY;
ALTER TABLE tags             FORCE  ROW LEVEL SECURITY;
ALTER TABLE transaction_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction_tags FORCE  ROW LEVEL SECURITY;

CREATE POLICY transactions_member_access ON transactions FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));

CREATE POLICY tags_member_access ON tags FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));

CREATE POLICY transaction_tags_member_access ON transaction_tags FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));
