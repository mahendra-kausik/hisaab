-- LOCAL DEVELOPMENT AND TEST ONLY. These passwords are not secrets: they
-- protect a throwaway Postgres container holding fake data. Production roles
-- and passwords are created separately and reach the API only through
-- environment variables.
--
-- Postgres runs this file once, the first time the container starts with an
-- empty data volume (also used by the Testcontainers tests). To re-run it:
-- `make db-down` and then remove the volume (`docker compose down -v`).

-- hisaab_owner owns the schema and runs Flyway migrations.
CREATE ROLE hisaab_owner LOGIN PASSWORD 'hisaab_dev_owner';

-- hisaab_app is what the running API connects as. It owns no tables and is
-- explicitly not allowed to bypass row-level security (RLS).
CREATE ROLE hisaab_app LOGIN PASSWORD 'hisaab_dev_app' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

CREATE DATABASE hisaab OWNER hisaab_owner;

-- Nobody but the two roles (and the superuser) may connect.
REVOKE ALL ON DATABASE hisaab FROM PUBLIC;
GRANT CONNECT ON DATABASE hisaab TO hisaab_app;

\connect hisaab

-- Make sure the app role can never create objects in the public schema,
-- whatever the Postgres defaults are.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
