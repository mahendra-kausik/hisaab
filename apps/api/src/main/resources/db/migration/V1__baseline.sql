-- Baseline: runs as hisaab_owner (Flyway's user). Creates no tables.
-- gen_random_uuid() is built into Postgres 13+, so no pgcrypto extension is needed.

-- The runtime role may see the schema...
GRANT USAGE ON SCHEMA public TO hisaab_app;

-- ...and gets CRUD on every table the owner creates from now on. Default
-- privileges apply per creating role, hence FOR ROLE hisaab_owner. Tables are
-- never owned by hisaab_app, and it has no BYPASSRLS, so row-level security
-- policies added in later migrations always apply to it.
ALTER DEFAULT PRIVILEGES FOR ROLE hisaab_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hisaab_app;
