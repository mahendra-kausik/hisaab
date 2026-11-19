-- U10: monthly budgets per parent expense category, workspace-owned and protected by RLS.
-- A row says "from this period on, the budget is X"; the one in effect for a period is the
-- latest row with effective_from on or before the period's start. amount_paise = 0 means
-- "no budget from here on", so history stays intact.

CREATE TABLE budgets (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  category_id    uuid        NOT NULL,
  amount_paise   bigint      NOT NULL CHECK (amount_paise >= 0),
  -- The start date of the period this amount first applies to.
  effective_from date        NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now(),
  -- Deleting a category takes its budgets with it, so budgets never block a delete.
  FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id) ON DELETE CASCADE,
  UNIQUE (workspace_id, category_id, effective_from)
);

ALTER TABLE budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE budgets FORCE  ROW LEVEL SECURITY;

CREATE POLICY budgets_member_access ON budgets FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));
