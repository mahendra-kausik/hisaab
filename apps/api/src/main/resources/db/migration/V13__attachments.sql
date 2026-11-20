-- U19: attachments (photos / PDFs of receipts, cheques, agreements) on a transaction or a due.
-- The file itself lives in object storage (R2); this table keeps only its metadata and key.

CREATE TABLE attachments (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  transaction_id uuid,
  due_id         uuid,
  object_key     text        NOT NULL,
  file_name      text        NOT NULL CHECK (char_length(file_name) BETWEEN 1 AND 200),
  mime_type      text        NOT NULL,
  -- 10 MiB, the same limit the API enforces; the database keeps it even if the API check is lost.
  size_bytes     bigint      NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
  created_by     uuid        NOT NULL REFERENCES users (id),
  created_at     timestamptz NOT NULL DEFAULT now(),
  -- Belongs to exactly one parent.
  CHECK (num_nonnulls(transaction_id, due_id) = 1),
  -- Composite foreign keys so an attachment can never point at another workspace's row.
  FOREIGN KEY (transaction_id, workspace_id) REFERENCES transactions (id, workspace_id),
  FOREIGN KEY (due_id, workspace_id)         REFERENCES dues         (id, workspace_id)
);
CREATE INDEX attachments_transaction_idx ON attachments (workspace_id, transaction_id)
  WHERE transaction_id IS NOT NULL;
CREATE INDEX attachments_due_idx ON attachments (workspace_id, due_id)
  WHERE due_id IS NOT NULL;

ALTER TABLE attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE attachments FORCE  ROW LEVEL SECURITY;

CREATE POLICY attachments_member_access ON attachments FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));
