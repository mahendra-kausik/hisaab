-- U14: parties (the people money moves with), linked from transactions and recurring rules.

CREATE TABLE parties (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
  name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
  phone        text        CHECK (char_length(phone) <= 20),
  role         text        NOT NULL CHECK (role IN ('buyer', 'seller', 'contractor', 'broker',
                                                    'supplier', 'tenant', 'friend_family', 'other')),
  notes        text        CHECK (char_length(notes) <= 500),
  archived_at  timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  -- Target of the composite foreign keys below, so a link can never cross workspaces.
  UNIQUE (id, workspace_id)
);
-- Names are unique per workspace, ignoring case. Also serves workspace_id lookups.
CREATE UNIQUE INDEX parties_workspace_name_uq ON parties (workspace_id, lower(name));

ALTER TABLE parties ENABLE ROW LEVEL SECURITY;
ALTER TABLE parties FORCE  ROW LEVEL SECURITY;

CREATE POLICY parties_member_access ON parties FOR ALL
  USING (workspace_id IN (SELECT workspace_id FROM workspace_members
                          WHERE user_id = app_current_user_id()))
  WITH CHECK (workspace_id IN (SELECT workspace_id FROM workspace_members
                               WHERE user_id = app_current_user_id()));

-- Parties are archived, never deleted, so these foreign keys need no ON DELETE action.
ALTER TABLE transactions
  ADD COLUMN party_id uuid,
  ADD FOREIGN KEY (party_id, workspace_id) REFERENCES parties (id, workspace_id);
CREATE INDEX transactions_party_idx ON transactions (workspace_id, party_id)
  WHERE party_id IS NOT NULL;

ALTER TABLE recurring_rules
  ADD COLUMN party_id uuid,
  ADD FOREIGN KEY (party_id, workspace_id) REFERENCES parties (id, workspace_id);
