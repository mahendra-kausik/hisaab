-- U3: accounts and categories, both workspace-owned and protected by RLS.
-- Runs as hisaab_owner; V1's default privileges already grant hisaab_app CRUD.

CREATE TABLE accounts (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id          uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  name                  text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
  type                  text        NOT NULL CHECK (type IN ('cash', 'bank', 'credit_card', 'wallet')),
  -- May be negative (e.g. an unpaid credit card).
  opening_balance_paise bigint      NOT NULL DEFAULT 0,
  archived_at           timestamptz,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now()
);
-- Names are unique per workspace, ignoring case. Also serves workspace_id lookups.
CREATE UNIQUE INDEX accounts_workspace_name_uq ON accounts (workspace_id, lower(name));

CREATE TABLE categories (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid    NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  parent_id    uuid,
  name         text    NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
  kind         text    NOT NULL CHECK (kind IN ('expense', 'income')),
  -- Seeded defaults: can be renamed or hidden, never deleted.
  is_system    boolean NOT NULL DEFAULT false,
  hidden_at    timestamptz,
  sort_order   int     NOT NULL,
  -- Lets the composite foreign key below point at (id, workspace_id, kind).
  UNIQUE (id, workspace_id, kind),
  -- A parent must be in the same workspace and of the same kind, enforced by the
  -- database. No ON DELETE action: a parent that still has children cannot be deleted.
  FOREIGN KEY (parent_id, workspace_id, kind) REFERENCES categories (id, workspace_id, kind)
);
-- Sibling names are unique ignoring case. NULLS NOT DISTINCT makes top-level categories
-- (parent_id NULL) clash with each other too. Needs Postgres 15+.
CREATE UNIQUE INDEX categories_sibling_name_uq
  ON categories (workspace_id, kind, parent_id, lower(name)) NULLS NOT DISTINCT;

ALTER TABLE accounts   ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts   FORCE  ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories FORCE  ROW LEVEL SECURITY;

-- The standard pattern for workspace-owned tables: only rows in workspaces the
-- current user belongs to, for reads and for writes (USING and WITH CHECK).
CREATE POLICY accounts_member_access ON accounts FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));

CREATE POLICY categories_member_access ON categories FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));
