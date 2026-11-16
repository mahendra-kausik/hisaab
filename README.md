# Hisaab

A money tracker for individuals and small businesses in India: expenses,
income, budgets, reports, and a people-and-dues ledger. The specs live in
[`context/`](context/) and are the source of truth for what to build.

| Part | Folder | Stack |
| --- | --- | --- |
| API | `apps/api` | Java 25, Spring Boot 4, Maven Wrapper, PostgreSQL 16, Flyway |
| Web | `apps/web` | Next.js (App Router), TypeScript, Tailwind, shadcn/ui |

## Prerequisites

- **JDK 25** (e.g. Temurin). Check with `java -version`.
- **Docker** (Docker Desktop on Windows/Mac). Used for local Postgres and for
  the API tests (Testcontainers), so it must be running.
- **Node.js 24** and npm.
- **make** and **Git for Windows** (on Windows). The Makefile uses Git's `sh`
  automatically, so `make` works from cmd, PowerShell, or Git Bash. Git must be
  installed in `C:\Program Files\Git`.

You do not need Maven installed: the project uses the Maven Wrapper
(`apps/api/mvnw`).

## First-time setup

```bash
cp apps/api/.env.example apps/api/.env
cp apps/web/.env.example apps/web/.env.local
cd apps/web && npm ci && cd ../..
```

The `.env` files are gitignored. The example values are local-only and match
`docker/postgres/init-roles.sql`.

## Run it

```bash
make db-up   # start Postgres (port 5432) with the two database roles
make dev     # API on :8080 and web on :3000, together (Ctrl+C stops both)
```

Open <http://localhost:3000>. The home page calls `/api/v1/health` through the
Next.js `/api` proxy and shows the result. The API applies database migrations
on startup. Swagger UI is at <http://localhost:8080/swagger-ui.html> in dev.

Stop the database with `make db-down`. To wipe it and re-run the role setup
script, run `docker compose down -v` first.

## Everyday commands

| Command | What it does |
| --- | --- |
| `make db-up` / `make db-down` | Start / stop local Postgres |
| `make dev` | Run API and web together (`dev-api`, `dev-web` run one each) |
| `make test-api` | API tests (real Postgres through Testcontainers) |
| `make fmt` | Format the Java code (Spotless + google-java-format) |
| `make gen-api` | Regenerate `apps/web/lib/api/schema.d.ts` from the running API |
| `make check` | Everything CI runs: format check, build, tests, lint, typecheck |

`make gen-api` needs the API running (`make dev-api`) because it reads
`/v3/api-docs`.

## Database roles

Two Postgres roles are created by `docker/postgres/init-roles.sql`:

- `hisaab_owner` owns the schema and runs Flyway migrations.
- `hisaab_app` is what the running API connects as. It owns no tables and
  cannot bypass row-level security.

## Project rules

Read [`CLAUDE.md`](CLAUDE.md) and the files in [`context/`](context/) before
changing anything. Money is always integer paise, never floats.
