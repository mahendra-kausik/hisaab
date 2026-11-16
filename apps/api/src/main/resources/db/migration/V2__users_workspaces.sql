-- U1: users, workspaces, workspace_members, and row-level security (RLS).
-- Runs as hisaab_owner. V1's default privileges already grant hisaab_app
-- SELECT/INSERT/UPDATE/DELETE on every table created here, and Postgres lets
-- everyone execute functions by default, so no explicit GRANTs are needed.
-- RLS then narrows what hisaab_app may actually touch.

-- Who is making this request? The API sets `app.user_id` at the start of every
-- transaction (RlsTransactionManager). The setting is transaction-local, but a
-- pooled connection then reports '' (not NULL) in later transactions, so
-- nullif turns the empty string back into NULL. NULL matches no row, so a
-- request with no user sees nothing.
CREATE FUNCTION app_current_user_id() RETURNS uuid
  LANGUAGE sql STABLE
  AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

-- Users are not owned by a workspace, and signup/login must read and write
-- them before any user is known, so this table has no RLS. The API only ever
-- looks users up by id or email.
CREATE TABLE users (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email               text        NOT NULL UNIQUE CHECK (email = lower(email)),
  password_hash       text        NOT NULL,
  name                text        NOT NULL,
  timezone            text        NOT NULL DEFAULT 'Asia/Kolkata',
  period_start_day    int         NOT NULL DEFAULT 1 CHECK (period_start_day BETWEEN 1 AND 28),
  dues_digest_enabled boolean     NOT NULL DEFAULT true,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE workspaces (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name       text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
  kind       text        NOT NULL CHECK (kind IN ('personal', 'business')),
  created_by uuid        NOT NULL REFERENCES users (id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE workspace_members (
  workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  role         text        NOT NULL CHECK (role IN ('owner', 'editor', 'viewer')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX workspace_members_user_id_idx ON workspace_members (user_id);

-- ENABLE turns RLS on; FORCE makes it apply to the table owner too. Either way
-- hisaab_app is not the owner, so the policies below always apply to it.
ALTER TABLE workspaces        ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspaces        FORCE  ROW LEVEL SECURITY;
ALTER TABLE workspace_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspace_members FORCE  ROW LEVEL SECURITY;

-- True when the current user created workspace `ws`. Used only by the
-- membership insert policy. It lives in a function so that policy does not
-- query `workspaces` directly while `workspaces`' own policy queries
-- `workspace_members` (two policies pointing at each other).
CREATE FUNCTION app_is_workspace_creator(ws uuid) RETURNS boolean
  LANGUAGE sql STABLE
  AS $$ SELECT EXISTS (
          SELECT 1 FROM workspaces WHERE id = ws AND created_by = app_current_user_id()
        ) $$;

-- Only SELECT and INSERT policies exist. With RLS on and no UPDATE or DELETE
-- policy, those statements match no rows. Nothing in scope renames or deletes
-- a workspace yet; add the policy in the unit that needs it.

-- A creator always sees their workspace, so the insert can return the new id
-- and the owner membership can be added in the same transaction.
CREATE POLICY workspaces_select ON workspaces FOR SELECT
  USING (
    created_by = app_current_user_id()
    OR id IN (SELECT workspace_id FROM workspace_members WHERE user_id = app_current_user_id())
  );
CREATE POLICY workspaces_insert ON workspaces FOR INSERT
  WITH CHECK (created_by = app_current_user_id());

-- Users see only their own membership rows, and may only make themselves the
-- owner of a workspace they created.
--
-- Pattern for every later workspace-owned table (accounts, transactions, ...):
--   USING (workspace_id IN (SELECT workspace_id FROM workspace_members
--                           WHERE user_id = app_current_user_id()))
CREATE POLICY workspace_members_select ON workspace_members FOR SELECT
  USING (user_id = app_current_user_id());
CREATE POLICY workspace_members_insert ON workspace_members FOR INSERT
  WITH CHECK (
    user_id = app_current_user_id()
    AND role = 'owner'
    AND app_is_workspace_creator(workspace_id)
  );
