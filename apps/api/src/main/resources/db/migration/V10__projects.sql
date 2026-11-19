-- U15: projects (properties or sites), linked from transactions and recurring rules.

CREATE TABLE projects (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
  notes        text        CHECK (char_length(notes) <= 500),
  archived_at  timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  -- Target of the composite foreign keys below, so a link can never cross workspaces.
  UNIQUE (id, workspace_id)
);
-- Names are unique per workspace, ignoring case. Also serves workspace_id lookups.
CREATE UNIQUE INDEX projects_workspace_name_uq ON projects (workspace_id, lower(name));

ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE  ROW LEVEL SECURITY;

CREATE POLICY projects_member_access ON projects FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));

-- Projects are archived, never deleted, so these foreign keys need no ON DELETE action.
ALTER TABLE transactions
  ADD COLUMN project_id uuid,
  ADD FOREIGN KEY (project_id, workspace_id) REFERENCES projects (id, workspace_id);
CREATE INDEX transactions_project_idx ON transactions (workspace_id, project_id)
  WHERE project_id IS NOT NULL;

ALTER TABLE recurring_rules
  ADD COLUMN project_id uuid,
  ADD FOREIGN KEY (project_id, workspace_id) REFERENCES projects (id, workspace_id);
