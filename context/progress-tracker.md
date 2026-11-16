# Progress Tracker

Update this file after every meaningful implementation
change.

## Current Phase

- Phase 1 (manual tracker) — in progress (U0, U1 done)

## Current Goal

- U2: auth API — signup, login, logout, refresh, `GET /me`,
  settings update (see Next Up)

## Completed

- **U0 — Repo scaffold (16 Nov 2026):** monorepo; Docker
  Postgres 16 with `hisaab_owner` / `hisaab_app` roles;
  Spring Boot 4.1.2 API with `GET /api/v1/health` (real DB
  round trip), Flyway baseline, error handler, Testcontainers
  tests (9 passing); Next.js 16 + Tailwind + shadcn with theme
  tokens, `/api` proxy, TanStack Query, money helpers (15
  Vitest tests); Makefile, `.env.example` files, CI workflow.
  `make db-up && make dev` and `make check` both verified.
- **U1 — Core schema & RLS foundation (16 Nov 2026):**
  Flyway `V2` creates `users`, `workspaces`,
  `workspace_members` with RLS (enabled and forced on the
  two workspace tables), `app_current_user_id()` and
  `app_is_workspace_creator()`. Java: `CurrentUser`
  (`ScopedValue`), `RlsTransactionManager`,
  `WorkspaceService.create`, `WorkspaceAccess.require`,
  `LowercaseEnumConverter`. 13 new tests (22 total) incl.
  two users proving isolation by raw SQL. `make check`
  passes. No endpoints yet. Not run by hand: `make dev`
  (the tests boot the full app against the same roles and
  migrations).

## In Progress

- None yet.

## Next Up

Units that touch both the API and the web are built as
two steps (API first), per `ai-workflow-rules.md`. Unit
IDs are stable labels; build in the order listed here.

### Phase 1 — Manual tracker

- [x] **U0 — Repo scaffold:** monorepo layout; Docker
  Postgres with owner/app roles; Spring Boot skeleton
  (Maven Wrapper) with `/api/v1/health` that checks the
  DB; Flyway baseline; error handler; Testcontainers test
  setup; Next.js + Tailwind + shadcn with theme tokens;
  `/api` rewrite proxy; money formatting helpers (date
  helpers come in U7); `make` targets; `.env.example` files; GitHub
  Actions running `make check`.
- [x] **U1 — Core schema & RLS foundation:** users,
  workspaces, workspace_members; `CurrentUser`,
  `RlsTransactionManager`, `WorkspaceAccess`; test
  fixtures with two users proving RLS isolation.
- [ ] **U2 — Auth API:** signup, login, logout, refresh
  (rotation + reuse detection), `GET /me`, settings
  update; signup creates the personal workspace and
  defaults; Bucket4j rate limiting on login and signup.
- [ ] **U3 — Accounts & categories API:** CRUD, default
  seeding by workspace kind, computed balances.
- [ ] **U4 — Transactions API:** create, list, filter,
  search, update, soft delete, restore; cursor
  pagination; tags.
- [ ] **U5 — Transfers API:** linked-pair create,
  update, delete.
- [ ] **U6 — Web shell:** auth screens, desktop app
  shell (sidebar, collapsing to a slide-over menu on
  narrow screens), workspace switcher, theme toggle.
- [ ] **U7 — Web transactions:** list, quick-add dialog
  (`N` shortcut), edit, delete with Undo.
- [ ] **U8 — Web accounts & categories management.**
- [ ] **U9 — Budgets:** API, then web progress view.
- [ ] **U10 — Recurring:** job queue + `@Scheduled`
  poller + recurring rules API, then recurring UI.
- [ ] **U11 — Monthly report:** period-aware report API,
  then report page with charts.
- [ ] **U12 — CSV export.**

### Phase 1.5 — People & dues

- [ ] **U14 — Parties:** API, then web.
- [ ] **U15 — Projects (properties/sites):** API, then
  web; project filter on transactions.
- [ ] **U16 — Dues API:** create, update, cancel;
  optional origin transaction for lending/borrowing;
  payments linked to dues; computed outstanding and
  state.
- [ ] **U17 — Web dues:** Dues screen (overdue / today /
  upcoming / settled), party ledger page, and the "record
  money handed over" option when creating a due.
- [ ] **U18 — Due reminders for the user:** Home banner
  and Dues badge first; then the daily email digest job
  (blocked on the email provider decision).
- [ ] **U19 — Attachments (R2)** on transactions and
  dues.
- [ ] **U20 — Per-party PDF statement** (PDFBox).
- [ ] **U21 — Excel export** for a workspace (Apache POI).
- [ ] **U22 — Nightly database backup** to R2.

### Phase 1.6 — Phone

- [ ] **U23 — Phone layout:** bottom tab bar, bottom-sheet
  quick add, phone-sized lists and reports.
- [ ] **U13 — PWA:** manifest, icons, installability.

## Open Questions

- **Email provider** for the daily dues digest (U18),
  and later verification and password reset; also
  whether Google sign-in should come first.
- **Scheduled jobs on free hosting:** the worker runs
  inside the API, but Render's free instance sleeps
  after 15 minutes without traffic, so `@Scheduled` jobs
  pause while it sleeps. Options: a free external pinger
  that wakes it on a schedule, catch-up on wake
  (recurring entries already catch up from
  `next_run_on`; digests would arrive late), or a paid
  always-on instance. Decide before deploying U10.
- **Project name:** keep the working name "Hisaab" or
  rename?

## Architecture Decisions

- **Java + Spring Boot backend (13 Nov 2026)** — chosen
  to build Java and Spring experience; replaces the
  earlier FastAPI plan. Next.js stays for the web app,
  and an API-first backend still makes a mobile app just
  another client. Phase 3 ML may use a small separate
  Python service.
- **Feature packages, no Lombok, constructor injection
  (13 Nov 2026)** — keeps each feature's code together
  and the Java explicit, so it is easy to read for
  someone new to Spring.
- **Flyway with plain SQL migrations** — RLS policies
  and grants are SQL anyway; Hibernate only validates
  the schema.
- **Testcontainers for API tests** — tests run against
  the same Postgres version and role setup as
  production, so RLS is actually tested.
- **RLS context set by a custom transaction manager** —
  every transaction gets `app.user_id` automatically, so
  a forgotten call can't silently skip RLS.
- **Worker inside the API process (`@Scheduled`
  poller)** — one deployable service on free hosting;
  the `jobs` table and `SKIP LOCKED` claiming are
  unchanged, so a separate worker can be split out later.
- **Workspace-based ownership instead of per-user
  rows** — supports personal and business workspaces
  now, and shared access (e.g. an assistant) later,
  without a schema rewrite.
- **Postgres RLS with forced policies and a non-owner
  runtime role** — defense in depth for multi-user
  financial data; a missed `WHERE` clause cannot leak
  another user's data.
- **Money as BIGINT paise (`long` in Java)** — avoids
  floating-point errors.
- **Derived values computed, not stored** — balances
  and due outstanding amounts cannot drift out of sync.
- **Postgres-backed job queue instead of Redis** — one
  fewer service, fits free tiers, and avoids per-command
  Redis billing from polling workers.
- **Same-origin `/api` proxy with httpOnly cookies** —
  avoids third-party cookie problems and keeps tokens
  out of JavaScript; bearer tokens are reserved for
  mobile.
- **`source`, `raw_description`, and `merchant` on
  transactions from day one** — the Phase 2 import
  pipeline plugs in without changing core semantics.
- **Auth built into the API with Spring Security** — no
  vendor lock-in, and auth design is a useful interview
  topic; scope kept to email + password for Phase 1.
- **Desktop-first, phone later (4 Nov 2026)** — the first
  business user works on a computer. Phone layout and
  PWA moved to Phase 1.6; pages must still work at 360px
  so nothing needs rebuilding later.
- **Reminders go only to the user (4 Nov 2026)** — the
  business user does not want parties contacted. The
  WhatsApp reminder unit was replaced by in-app
  reminders and a daily email digest to the user.
- **Lending counts as expense, borrowing as income (4 Nov
  2026)** — matches how the user thinks about cash
  leaving and entering. The due tracks what is still
  owed, and `dues.origin_transaction_id` links the money
  handed over without counting it as a payment.
- **Parties and projects both kept (4 Nov 2026)** — no
  preference stated between organising by person or by
  property, so the design supports both.

- **Versions and U0 decisions (16 Nov 2026)** — Java 25,
  Spring Boot 4.1.2, springdoc-openapi 3.2.0, Testcontainers
  2.0.5 (managed by Boot), Spotless plugin 3.11.0 with
  google-java-format pinned to 2.0.0 (1.37.0 failed inside
  Spotless 3.11.0, 2.0.0 is Spotless's own default). Local role passwords
  in `init-roles.sql` are dev-only and labelled as such;
  production credentials come only from environment
  variables. `gen_random_uuid()` is built in, so no
  `pgcrypto`. Light `--text-muted` darkened to `#5B6472`
  for WCAG AA on subtle surfaces. Date formatting helpers
  move from U0 to U7.
- **U0 implementation notes (16 Nov 2026)** — Docker volume
  is named `hisaab_api_pgdata` because an older project's
  `hisaab_pgdata` volume already existed and made Postgres
  skip `init-roles.sql` (left untouched). shadcn's current
  `base-nova` style brought its own dependencies
  (`@base-ui/react`, `class-variance-authority`, `cn`,
  `tw-animate-css`, `shadcn`). `@types/node` is `^24` because
  Vitest 5 needs 22+. `.gitattributes` forces LF so `mvnw`
  and the Dockerfile work on Linux. Design tokens use CSS
  `light-dark()`, so each token is defined once and follows
  the system theme or a `light`/`dark` class on `<html>`.
  shadcn's `accent-foreground` is mapped to the primary text
  color because our `--accent-foreground` token means "text
  on the accent". On Windows the Makefile switches `SHELL`
  to Git's `sh.exe` and adds Git's `usr/bin` to PATH, and
  calls `npm.cmd` (npm's bash wrapper would pick WSL's
  `bash.exe`); verified from cmd.exe. `ErrorHandlingTest`'s unit tests extend the
  Spring base class; split them out if startup time matters.

- **U1 decisions (16 Nov 2026)** — `users` has no RLS (not
  workspace-owned; signup/login need it before a user is
  known). A workspace is visible to its creator, so the
  insert and the owner membership fit in one transaction.
  Only SELECT/INSERT policies exist on the workspace tables
  until a unit needs more. `CurrentUser` uses Java 25
  `ScopedValue` (`call`/`run`, not `get`, run code with a
  binding). The `User` entity waits for U2.
  `RlsTransactionManager` marks its `CurrentUser` field
  `transient` because `JpaTransactionManager` is
  `Serializable` and `-Xlint:all -Werror` flags the field.
  `Workspace.id` uses `GenerationType.IDENTITY` with a
  database-generated UUID and `WorkspaceMemberId` is a
  record `@IdClass`; both worked with Hibernate as planned.

## Session Notes

After each session, add a dated entry (3–5 lines): units
completed, key files, and the Java/Spring concepts
introduced, so later explanations don't repeat them.

- **16 Nov 2026 — Session 1, U0 done.** Key files:
  `apps/api/pom.xml`, `HisaabApplication`, `health/*`,
  `common/web/GlobalExceptionHandler`, `V1__baseline.sql`,
  `PostgresTestBase`; `docker/postgres/init-roles.sql`;
  `apps/web/lib/format.ts`, `app/globals.css`,
  `next.config.ts`. Concepts introduced: Spring Boot startup
  and component scanning, stereotype annotations
  (`@RestController`, `@Service`, `@Configuration`),
  constructor injection, Maven / `pom.xml` / wrapper,
  profiles + environment variables, `@ConfigurationProperties`,
  `@RestControllerAdvice`, Flyway, two database roles,
  Testcontainers.

- **16 Nov 2026 — Session 1 (continued), U1 done.** Key
  files: `V2__users_workspaces.sql`, `CurrentUser`,
  `RlsTransactionManager`, `WorkspaceAccess`,
  `WorkspaceService`, `TestFixtures`, `RlsIsolationTest`.
  Concepts introduced: Postgres RLS policies, JPA entities
  and Spring Data repositories, `@Transactional`
  (including joining a caller's transaction), custom
  `TransactionManager` bean override, JPA attribute
  converters, `@IdClass` composite keys, Java `ScopedValue`,
  `@TestComponent`.
