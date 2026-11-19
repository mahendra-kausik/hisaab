-- U16: dues (receivable / payable per party). Payments are ordinary transactions that point at the
-- due through transactions.due_id; the origin transaction (money handed over) points the other way.

CREATE TABLE dues (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id          uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  party_id              uuid        NOT NULL,
  direction             text        NOT NULL CHECK (direction IN ('receivable', 'payable')),
  amount_paise          bigint      NOT NULL CHECK (amount_paise > 0),
  reason                text        NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 200),
  due_date              date,
  project_id            uuid,
  category_id           uuid,
  origin_transaction_id uuid,
  cancelled_at          timestamptz,
  created_by            uuid        NOT NULL REFERENCES users (id),
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  deleted_at            timestamptz,
  -- Target of transactions.due_id's composite foreign key, so a link can never cross workspaces.
  UNIQUE (id, workspace_id),
  FOREIGN KEY (party_id, workspace_id)    REFERENCES parties    (id, workspace_id),
  FOREIGN KEY (project_id, workspace_id)  REFERENCES projects   (id, workspace_id),
  FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id),
  FOREIGN KEY (origin_transaction_id, workspace_id) REFERENCES transactions (id, workspace_id)
);
CREATE INDEX dues_party_idx ON dues (workspace_id, party_id);

ALTER TABLE dues ENABLE ROW LEVEL SECURITY;
ALTER TABLE dues FORCE  ROW LEVEL SECURITY;

CREATE POLICY dues_member_access ON dues FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));

-- A payment is a transaction with due_id set. Dues are cancelled, never hard-deleted by the API.
ALTER TABLE transactions
  ADD COLUMN due_id uuid,
  ADD FOREIGN KEY (due_id, workspace_id) REFERENCES dues (id, workspace_id);
CREATE INDEX transactions_due_idx ON transactions (workspace_id, due_id)
  WHERE due_id IS NOT NULL;
