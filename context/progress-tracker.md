# Progress Tracker

Update this file after every meaningful implementation
change.

## Current Phase

- Phase 1 (manual tracker) — in progress (U0–U12 done)

## Current Goal

- U13: CSV export (see Next Up).

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
- **U2 — Auth API (17 Nov 2026):** `POST /api/v1/auth/signup`,
  `login`, `refresh` (rotation + reuse detection), `logout`;
  `GET` / `PATCH /api/v1/me`. Flyway `V3` adds
  `refresh_tokens`. Spring Security with httpOnly cookies
  (also accepts a bearer header), Nimbus HS256 access JWTs,
  BCrypt strength 12, Bucket4j limits on login and signup.
  Signup creates the user and personal workspace in one
  transaction. 27 new tests (49 total); `make check` passes
  and the flow was also run by hand with curl against
  `make dev-api` (signup, me, refresh, patch, logout, 429).
  Not done here: default categories and the Cash account
  (U3 hooks into `WorkspaceService.create`).
- **U3 — Accounts & categories API (17 Nov 2026):** Flyway
  `V4` adds `accounts` and `categories`, both with RLS
  enabled and forced. Endpoints under
  `/api/v1/workspaces/{id}/`: `accounts` (GET, POST, PATCH;
  archive through PATCH, no DELETE) and `categories` (GET,
  POST, PATCH, DELETE), plus a new `POST /api/v1/workspaces`.
  `WorkspaceService.create` now seeds a Cash account and the
  default categories for the workspace kind, so signup
  already gives a usable workspace. Accounts return
  `balance_paise`, which equals the opening balance until U4
  adds transactions. 36 new tests (85 total); `make check`
  passes, the flow was run by hand against `make dev-api`, and
  `schema.d.ts` was regenerated with `make gen-api`.

- **U4 — Transactions API (17 Nov 2026):** Flyway `V5` adds
  `transactions`, `tags` and `transaction_tags`, all with RLS
  enabled and forced, plus composite foreign keys so a row
  cannot point at another workspace's account, category or
  tag. Endpoints under `/api/v1/workspaces/{id}/`:
  `transactions` (GET list, POST, GET one, PUT, DELETE,
  `POST .../{id}/restore`) and `tags` (GET names). The list
  filters by date range, type, account, category (including
  subcategories), tag and note text, with cursor pagination.
  Account `balance_paise` is now real (opening balance plus
  inflows minus outflows of live transactions). 28 new tests
  (113 total); `make check` passes, the flow was run by hand
  against `make dev-api`, and `schema.d.ts` was regenerated.
  Only expense and income are accepted; transfers are U5.
- **U5 — Transfers API (17 Nov 2026):** Flyway `V6` adds a
  unique index on `(transfer_group_id, type)`; no new table.
  Endpoints under `/api/v1/workspaces/{id}/transfers`: `POST`,
  `GET /{id}`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/restore`.
  A transfer is two `transactions` rows (out + in) written
  and changed together in one database transaction; its id is
  the shared `transfer_group_id`. Both legs show in
  `GET /transactions` with `transfer_group_id`, and balances
  move on both accounts. `PUT`, `DELETE` and restore on a
  transfer leg through `/transactions/{id}` are 422
  `transfer_leg`. 14 new tests (127 total); the flow was run
  by hand against `make dev-api`, and `schema.d.ts` was
  regenerated.
- **U6 — Web shell (18 Nov 2026):** `/login` and `/signup`
  (Zod-validated, show the API's error messages), and a
  signed-in shell for every other page: 240px sidebar on
  desktop, slide-over menu under 768px, workspace switcher
  with "New workspace", and a System / Light / Dark theme
  choice in the user menu, plus log out. Home is a placeholder
  greeting. All API calls go through `lib/api/client.ts`, which
  refreshes an expired session once, shared across concurrent
  requests. Before the web work, an API fix (commit `dad9411`):
  springdoc ignored the snake_case setting, so every generated
  type was camelCase while the JSON is snake_case; `OpenApiConfig`
  fixes that, `schema.d.ts` was regenerated. 1 new API test (128
  total), 9 new web tests (23 total); `make check` passes. The
  auth flow was run by hand with curl through the Next.js proxy
  and then in Chrome (Claude in Chrome): signup validation and
  signup, login with a wrong and a right password, log out and the
  redirect back to `/login?next=%2F`, the theme choice surviving a
  reload, the workspace switcher and new-workspace dialog (the new
  workspace stayed active after a reload), and the slide-over menu
  in a 360px-wide frame (no horizontal overflow, Esc closes it and
  focus returns to the menu button). The browser check found and
  fixed three bugs, listed under "U6 decisions".
- **U7 — Web transactions (19 Nov 2026):** the Transactions page
  (`/transactions`): entries grouped by date with a net daily
  total (income minus expense; transfers left out), "Load more"
  paging, search on notes plus type, account, category, tag and
  date-range filters kept in the URL, and a row menu with Edit and
  Delete. The quick-add dialog (sidebar Add button or the `N` key
  from any signed-in page) adds an expense, income or transfer and
  edits an existing one; Delete takes effect at once and shows an
  8-second Undo toast. Before the web work, an API fix (commit
  `8a8e509`): response fields that are never null are marked
  `@NotNull`, so the generated TypeScript types are no longer all
  optional. New: `sonner` (toasts, through the shadcn CLI) and the
  shadcn `combobox` for the category picker. 13 new web tests (36
  total), no new API tests beyond one OpenAPI assertion (128 total);
  `make check` passes. Checked by hand in Chrome against the
  running API: add, edit and delete with Undo for each type,
  balances after a transfer edit, delete and Undo of a transfer,
  every filter, 63 rows loaded across two pages, validation
  messages, dark theme, and a 360px-wide frame (no horizontal
  overflow); the console had no errors.

- **U8 — Web accounts & categories (19 Nov 2026):** two new pages
  with their own sidebar links. `/accounts` lists open accounts and
  an "Archived" section with balances (a negative one shows
  `−₹12,000`), add and edit in one dialog, Archive / Unarchive from
  the row menu, and each row links to the Transactions page filtered
  to that account. `/categories` shows Expense and Income as
  parent/subcategory lists with add, rename, add subcategory, Hide /
  Show and Delete (custom categories only, through a confirm
  dialog). Web only, no API change. 7 new web tests (43 total);
  lint, typecheck and `npm run build` pass. `make check` as a whole
  was not run (no API files changed). Checked by hand in Chrome:
  signup, add a credit card with `-12000`, archive, hide a parent
  (badge and muted children), add and delete a custom category,
  no console errors. In a second pass: the duplicate-name error
  (case-insensitive), edit with the negative opening balance
  prefilled, unarchive, the row link opening Transactions filtered
  to the account, the "in use" message when deleting a category a
  transaction uses, Esc closing a dialog, the quick-add picker
  without the hidden Food group, and no horizontal overflow at 360px
  (same-origin iframe) on both pages. Only the dark theme was seen;
  light was not compared.
- **U9 — Web settings (19 Nov 2026):** `/settings` page (sidebar
  link now active) with name, timezone (native select from
  `Intl.supportedValuesOf`), "Month starts on day" (1–28) and the
  dues digest checkbox. Saving sends only the changed fields
  (`changedSettings`, `lib/settings.ts`) through `useUpdateSettings`,
  which seeds the `me` cache from the PATCH answer. Web only, no API
  change. 2 new web tests (45 total); lint and typecheck pass.
  `make check` was not run. Checked by hand in Chrome: defaults
  shown, change all four fields and save (toast, sidebar name
  updates), values persist after reload, empty name shows "Enter a
  name", no console errors. Not verified: 360px layout (the window
  resize did not narrow the page), "Today" shifting after a timezone
  change, ticking the digest back on, light theme. Known gap: the
  browser's timezone list may hold zone names the Java API rejects
  (400 `invalid_timezone`); the form shows the error.
- **U10 — Budgets (19 Nov 2026):** `V7__budgets.sql` (RLS, composite
  FK to categories with `ON DELETE CASCADE`), `in.hisaab.budgets`
  (`Period` maths, `Budget`, `BudgetRepository.usage` native query,
  `BudgetService`, `BudgetController`). `GET /workspaces/{id}/budgets
  ?period=<any date>` returns the period (from the caller's timezone
  and period start day) with each budgeted top-level expense
  category's amount and spent paise (children roll up; income,
  transfers and deleted rows excluded). `PUT .../budgets/{categoryId}`
  `{amount_paise}` writes the row for the current period (0 removes;
  earlier periods keep their amounts, later ones inherit). Web:
  `/budgets` with prev/next period arrows (`?period=`), progress
  bars (amber from 80%, red over 100%, "Over by ₹x"), inline edit,
  "No budget yet" list; sidebar link active. 10 new API tests (138
  total, all pass) and 9 new web tests (51 total); lint and
  typecheck and `npm run build` pass. Checked by hand in Chrome at
  1280px: warning (amber, 85%) and over (red, "Over by") states, a
  subcategory expense rolling up into its parent, prev period
  read-only and empty, inline edit prefilled and saved, no console
  errors. Not verified: 360px layout, light theme.
- **U11 — Recurring (19 Nov 2026):** `V8__jobs_recurring.sql`:
  `jobs` (system table, **no RLS and no `workspace_id`**, so
  `RlsIsolationTest` still finds only workspace tables),
  `recurring_rules` (RLS, composite FKs), and
  `transactions.recurring_rule_id` / `recurrence_date` with
  `UNIQUE (recurring_rule_id, recurrence_date)`. `in.hisaab.jobs`:
  `JobQueue` (JdbcTemplate: enqueue with a dedupe key, claim with
  `FOR UPDATE SKIP LOCKED`, done, retry after attempts² minutes,
  `failed` after 5, requeue stale), `JobHandler`, `JobPoller`
  (`@Scheduled` every 5 s, runs each job inside
  `CurrentUser.runAs(job.userId)`), `JobsConfig` (`@EnableScheduling`,
  off in tests via `app.jobs.enabled=false`). `in.hisaab.recurring`:
  `Recurrence` (dates counted from the anchor, so a monthly rule on
  the 31st does not drift), `RecurringRule`, `RecurringService`
  (CRUD, pause/resume, and the `recurring.materialize` job handler),
  `RecurringController`. Endpoints under
  `/workspaces/{id}/recurring`: `GET`, `POST`, `PUT /{id}`,
  `POST /{id}/pause|resume`, `DELETE /{id}`. A past first date is
  caught up by the job; resume and a changed schedule do not
  backfill; delete keeps the transactions already made. Web:
  `/recurring` list (frequency, next dates, paused badge, edit /
  pause / resume / delete) and add/edit dialog; sidebar link active.
  9 new API tests (147 total) and 3 new web tests; `make check`
  passes. Smoke-tested the real `@Scheduled` poller against the dev
  API: a weekly rule starting 14 days ago produced 3 transactions
  within 9 s and the next dates moved forward. **Not verified:**
  the 360px layout (the browser window would not shrink below
  desktop width). Checked by hand in Chrome on the desktop layout:
  empty state, add dialog, weekly rule with a past date created 3
  transactions via the poller, row text "Every week · Next 26 Nov
  2026, then …", pause (badge + toast). Edit, resume and delete
  were not clicked through in the browser (covered by API tests).
- **U12 — Monthly report (19 Nov 2026):** `in.hisaab.reports`:
  `ReportQueries` (JdbcTemplate; four queries that all count only
  live `income`/`expense` rows dated in `[start, end)`, so transfers
  and soft-deleted rows can never enter a total), `ReportService`
  (reuses `budgets.Period` for the period and the previous one;
  zero-fills every day so the bar chart has no gaps), `ReportController`
  (`GET /workspaces/{id}/reports/monthly?period=YYYY-MM-DD`, any date
  inside the wanted period, omitted = current) and `dto/MonthlyReport`
  (totals, previous totals, `by_category`, `daily`, `top_expenses`).
  Subcategories roll up into their top-level category; expenses with
  no category appear as "Uncategorized", so `by_category` always sums
  to the expense total. Web: `/reports` page with period navigation,
  income / expense / net cards with % change vs the previous period
  (`lib/reports.ts`, tested), Recharts donut + table, daily bar chart
  and top-5 list; sidebar link active. Added dependency: `recharts`.
  5 new API tests (152 total: reconciliation with a transfer and a
  deleted row excluded, leap vs non-leap February, start day 15
  boundary, top-5 cap, other workspace 404) and 1 new web test file;
  `make check` passes. **Browser pass (Chrome, dev API + web, seeded
  Nov/Oct data):** at 1280px the cards, donut + table, daily bars and
  top 5 all matched the seeded transactions (₹80,000 / ₹49,900 /
  ₹30,100; +7% / +150% / −45% vs October); the previous-period button
  loaded October correctly with "Nothing in the previous period"
  for September; no console errors. 360px was checked in a 360px
  same-origin iframe because the browser window will not shrink: no
  horizontal overflow, cards stack, chart fits. That pass found the
  daily chart's Y-axis labels clipped, fixed by widening the axis
  (`width={76}`). Not clicked through: the donut tooltip, light theme.

## In Progress

- None yet.

## Next Up

Units that touch both the API and the web are built as
two steps (API first), per `ai-workflow-rules.md`. Build
in the order listed here. Unit numbers always run in list
order: when a unit is added or moved, renumber the later
ones and every reference to them (see "Keeping Unit
Numbers Sorted" in `ai-workflow-rules.md`).

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
- [x] **U2 — Auth API:** signup, login, logout, refresh
  (rotation + reuse detection), `GET /me`, settings
  update; signup creates the personal workspace and
  defaults; Bucket4j rate limiting on login and signup.
- [x] **U3 — Accounts & categories API:** CRUD, default
  seeding by workspace kind, computed balances (balance =
  opening balance until U4). Also added `POST /workspaces`.
- [x] **U4 — Transactions API:** create, list, filter,
  search, update, soft delete, restore; cursor
  pagination; tags. Also made account balances real.
- [x] **U5 — Transfers API:** linked-pair create,
  update, delete, restore.
- [x] **U6 — Web shell:** auth screens, desktop app
  shell (sidebar, collapsing to a slide-over menu on
  narrow screens), workspace switcher, theme toggle.
- [x] **U7 — Web transactions:** list, quick-add dialog
  (`N` shortcut), edit, delete with Undo; also all list
  filters and note search.
- [x] **U8 — Web accounts & categories management.**
- [x] **U9 — Web settings:** page for name, timezone,
  period start day and the dues digest switch (`PATCH /me`
  already exists); gives the "Settings" sidebar item its
  `href`.
- [x] **U10 — Budgets:** API, then web progress view.
- [x] **U11 — Recurring:** job queue + `@Scheduled`
  poller + recurring rules API, then recurring UI.
- [x] **U12 — Monthly report:** period-aware report API,
  then report page with charts.
- [ ] **U13 — CSV export.**

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
- [ ] **U24 — PWA:** manifest, icons, installability.

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
  always-on instance. Decide before deploying U11.
- **Project name:** keep the working name "Hisaab" or
  rename?
- **Rate limiting behind Vercel (decide before deploy):**
  login and signup limits count per client IP. With the web
  app on Vercel proxying to the API on Render, every browser
  user may reach the API from Vercel's shared egress IPs and
  share one bucket. Options: trust a Vercel-set client-IP
  header, or also limit per email.
- **Two browser tabs can still log each other out:** the
  web client serialises refreshes inside one tab, but two
  tabs refreshing at the same moment send the same refresh
  cookie twice, which the API treats as reuse. Rare (needs
  both tabs to hit an expired access token together); fix
  later with a short grace window in the API or a
  cross-tab lock.

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

- **U2 decisions (17 Nov 2026)** — Spring Boot 4.1 starters
  are `spring-boot-starter-security` and
  `spring-boot-starter-security-oauth2-resource-server`;
  Bucket4j 8.22.0 (`bucket4j_jdk17-core`) is pinned because
  Boot does not manage it. The user id is generated in Java
  so signup can bind it with `runAs` before the transaction
  starts (`User` therefore has an app-assigned id, like
  `WorkspaceMember`). `refresh_tokens` has no RLS (see
  `architecture.md`). `/me` returns the user's workspaces so
  the signup result is visible and the U6 switcher has data.
  Login runs bcrypt against a dummy hash for unknown emails
  so timing does not reveal which emails exist, and returns
  one `invalid_credentials` message for both cases. Passwords
  are limited to 72 bytes (bcrypt's limit). CORS moved from
  `WebConfig` into `SecurityConfig`. The rate-limit filter is
  not a Spring bean (Boot would register it twice). Validation
  errors now report snake_case field names (fixed in
  `GlobalExceptionHandler`; before, `periodStartDay` leaked
  through). Tests pass a fake client IP per request
  (`TestFixtures.fromNewIp`) because the limiter's counters
  are shared across the whole test run.

- **U3 decisions (17 Nov 2026)** — Confirmed with the
  developer: `POST /api/v1/workspaces` is part of U3 (no
  later unit had it, and business seeding was otherwise
  unreachable over HTTP); `is_system` is true for every
  seeded category (rename and hide allowed, delete refused
  with 409 `system_category`); names are unique ignoring case
  (accounts per workspace, categories per workspace + kind +
  parent), duplicates answer 409 `name_taken`. A composite
  foreign key lets Postgres enforce that a parent category
  has the same workspace and kind; the one-level rule is in
  `CategoryService`. `NULLS NOT DISTINCT` on the sibling-name
  index needs Postgres 15+ (local and Neon are 16). Writes
  use `saveAndFlush` so constraint violations become 409 inside
  the service instead of a 500 at commit. Categories in use
  are detected through the foreign key (`category_in_use`), so
  U4 and U10 need no change to `delete`. Lists use the shared
  `ListResponse` with `next_cursor: null`. Enums gained
  `@JsonValue` (lowercase) so requests can carry them; an
  unknown enum value is a 400 `malformed_json`. Seeding runs
  after `members.saveAndFlush`, because the seed inserts' RLS
  check needs the membership row to exist. In tests,
  MockMvc's `jsonPath(...).value(List)` does not match
  filter-expression results (`[?(...)]`); read the body with
  `JsonPath.read` and assert with AssertJ. `RlsIsolationTest`
  now finds every table with a `workspace_id` column, so a
  future table that forgets RLS fails there.

- **U4 decisions (17 Nov 2026)** — Confirmed with the
  developer: same-day order is newest entered first
  (`occurred_on DESC, created_at DESC, id DESC`, so
  `code-standards.md` changed from `id DESC`); archived
  accounts refuse new transactions and moves onto them (422
  `account_archived`) but not other edits, delete or restore;
  the category filter includes subcategories; edit is `PUT`
  with full replacement (a `PATCH` with "null = keep" cannot
  clear a category, note or tags). Decided in the plan:
  `payment_method` is nullable (quick-add does not ask for
  it); columns for parties, projects, dues and recurring
  rules wait for the units that create those tables;
  `transaction_tags` carries `workspace_id` (standard RLS
  pattern, found automatically by `RlsIsolationTest`); tags
  are sent as names and created on first use, matching
  ignoring case; transfer types are 422 `invalid_type` until
  U5. Balance sums are native queries in `AccountRepository`
  because `TransactionService` already calls
  `AccountService`, so the reverse call would be a cycle.
  Queries use `JdbcTemplate` (`TransactionQueries`,
  `TagRepository`) for the filtered, keyset-paginated list
  and the tag arrays; writes use JPA. `@Min`/`@Max` on
  `limit` answer 400 through Spring MVC's built-in method
  validation, while a bad `type` filter or `cursor` is 422
  (a business-rule error like the others). Search text has
  `%`, `_` and `\` escaped so they match literally.
  `created_at` is the database's `now()` (transaction start)
  while `updated_at` comes from the app `Clock`, so on a
  create they can differ by a few milliseconds (or more on
  the very first request after startup). Not done yet: an
  index for note search (`ILIKE '%…%'` scans the workspace's
  rows; fine at this size, revisit with a trigram index if
  lists get slow).

- **U5 decisions (17 Nov 2026)** — Confirmed with the
  developer: `PUT`, `DELETE` and restore on a transfer leg
  through `/transactions/{id}` are refused (422 `transfer_leg`
  with `details.transfer_group_id`) rather than acting on both
  rows, so each endpoint keeps one meaning; transfers carry a
  note and a payment method (copied onto both legs), but no
  category or tags. Decided in the plan: the transfer's id is
  the `transfer_group_id` (no separate table); the code lives
  in the `transactions` package (`TransferService`,
  `TransferController`) because it shares the `Transaction`
  entity and repository; the response is assembled from the
  two live legs read through `TransactionQueries` so it matches
  the list; a group that does not have exactly one out and one
  in is treated as not found; `PUT` checks "archived" only for
  a leg whose account changes (same rule as U4); `created_at`
  and `updated_at` come from the out leg. A restore of a live
  transfer is a no-op, as for transactions. Tests that need
  money to move assert balances through `GET /accounts`.
  Note for U7: the web must send edit, delete and Undo of a
  transfer row to `/transfers/{transfer_group_id}`.

- **U6 decisions (18 Nov 2026)** — Confirmed with the
  developer: the active workspace is kept in `localStorage`
  (routes stay `/transactions`, `/dues`), the switcher can create
  workspaces in U6, and the Settings page is a later unit (U9;
  the nav shows it as "Soon"). Decided in the plan: the signed-in
  check is client-side because the auth cookies' paths (`/api`,
  `/api/v1/auth`) mean a page request never carries them, so a
  Next `proxy.ts` cannot see the session; the shell shows a
  skeleton until `GET /me` answers and redirects to
  `/login?next=...` on 401. Zod validates the forms with a plain
  `<form>` and `FormData`; react-hook-form waits for U7's
  quick-add. The theme is read from the `<html>` class (set by a
  script in `<head>` from `localStorage`), so it works even when
  storage is blocked. The init-script constant lives in
  `lib/theme-init.ts` apart from the `useTheme` hook, because the
  server layout cannot import a file that uses a React hook.
  The Next dev server leaves generated types in `.next/dev/types`;
  after deleting a route, remove that folder or `typecheck`
  fails on the missing page. shadcn here is the Base UI flavour:
  triggers take `render={<Button />}` rather than `asChild`, and
  default controls are 32px high, so shell controls add `h-11`
  for the 44px target. `MeResponse` marks every field `@NotNull`
  (including the two primitives) so the generated type has no
  optional fields. Found in the browser and fixed: (1) a U0 bug,
  `--color-base` in `globals.css`, made Tailwind treat `text-base`
  (the font size) as a text color too, so inputs, the select and
  dialog titles had text in the page-background color (near-white
  on white in light mode); the token was removed, and the page
  background is `bg-background`, so never name a theme color
  `base`; (2) the sheet's close button sat on top of the
  workspace switcher's chevron, fixed with top padding in
  `AppShell`; (3) Next's dev-only instant-navigation check
  logged an error for every page in `(app)` (they wait for
  `GET /me`, so they cannot be in the static shell), silenced for
  the whole app with `instantInsights.validationLevel:
  "manual-warning"` in `next.config.ts` (a layout-level
  `instant = false` did not cover pages). The Next dev "Insight"
  badge can cover the user menu in a short window; it is dev
  only. Not done: a redirect away from `/login` when
  already signed in; the Add button (arrives with quick-add in
  U7).

- **U7 decisions (19 Nov 2026)** — Confirmed with the developer:
  all list filters and note search are part of U7; `sonner` is
  the toast library; Delete has no confirm dialog, only the Undo
  toast (confirm dialogs stay for deletes that cannot be undone);
  the daily total is net income minus expense. Decided in the plan:
  a transfer's two legs are shown as one row (`toRows`), and its
  edit, delete and Undo go to `/transfers/{transfer_group_id}`;
  the type cannot be changed while editing (an expense cannot
  become a transfer; delete and add instead), which also keeps
  edit to one endpoint; the type filter offers "Transfer out" and
  "Transfer in" because the API filters one leg at a time, so an
  account or type filter can leave a transfer with only one leg
  in the list, and opening that row fetches the whole transfer
  first (`useTransfer`). Filters live in the URL (`?type=…&q=…`)
  and the search box is debounced 300 ms; the category picker is a
  searchable combobox (recently used first, taken from the newest
  page of transactions), the other selects are native `<select>`s.
  No react-hook-form: one controlled form with Zod is enough (add
  it if forms multiply). `payment_method` is not in the form but is
  sent back on edit, because `PUT` replaces the whole entry. The
  generated `components/ui/sonner.tsx` was edited by hand to use
  our own `useTheme` (`lib/theme.ts`) instead of `next-themes`,
  which does not know our theme choice; `next-themes` was
  removed. The generated `cn` import in `components/ui/*` comes
  from the `cn` package (U0), not `@/lib/utils`; feature code uses
  `@/lib/utils`. Response DTOs now mark never-null fields
  `@NotNull` (see `code-standards.md`); Jackson sends `null`, not
  a missing key, for nullable fields, so check with `??` or
  `!= null`. The window-resize tool did not change the page's
  viewport; a 360px check was done in a same-origin iframe, which
  evaluates media queries at its own width. Pressing `N` before
  the shell has loaded does nothing (the listener mounts with the
  shell). Not done: field-level error marking in the dialog (the
  message shows under the form with `role="alert"`, but the
  input is not set `aria-invalid`); the search box is not
  re-synced if the URL changes while the user is typing; no
  automated component tests (only helper tests), per the standards
  that ask for Vitest on helpers.

- **U8 decisions (19 Nov 2026)** — Confirmed with the developer:
  Accounts and Categories each get their own sidebar link (after
  Transactions), and an opening balance may be negative (money
  owed, e.g. a credit card). Decided in the plan: web only, because
  the U3 endpoints already cover everything; `signedRupeesToPaise`
  (new, `lib/format.ts`) reads one leading `-` or `−`, and
  `paiseToRupeesInput` now handles negatives; hiding a parent also
  hides its subcategories in pickers (`categoryItems`), the API
  still stores `hidden_at` on the parent only; archive and hide have
  no confirm dialog because both reverse, while a category delete
  does; dialogs are mounted only while open, so their form state
  starts fresh without a `session` key; the category icon is drawn
  with `createElement` because the React lint rule rejects `<Icon>`
  when the component comes from a function call (`categoryIcon`).
  `"- 5"` (a space after the minus) is accepted, since the space is
  trimmed. Not done: reordering categories (no API), moving a
  category to another parent (no API), a total-balance headline.

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
- **17 Nov 2026 — CI fix.** First GitHub Actions run failed at
  web typecheck: `LayoutProps` is a Next 16 generated type
  (`.next/types`) that doesn't exist on a fresh checkout.
  `apps/web` `typecheck` script is now `next typegen && tsc
  --noEmit`. Lesson: scripts that run before `build` can't rely
  on `.next/` existing.
- **17 Nov 2026 — Session 2, U2 done.** Key files: `V3__refresh_tokens.sql`,
  `auth/{AuthController,AuthService,TokenService,AuthCookies}`,
  `users/{User,UserService,UserController}`,
  `common/security/{SecurityConfig,CookieBearerTokenResolver,
  AuthRateLimitFilter,CurrentUser}`, `AuthFlowTest`. Concepts
  introduced: Spring Security filter chain and `SecurityFilterChain`
  bean, stateless JWT auth with the OAuth2 resource server, cookies
  vs headers, bcrypt, token rotation and reuse detection,
  `TransactionTemplate` (programmatic transactions), pessimistic
  locking (`@Lock`), `@Modifying` queries, `@CookieValue`,
  Bean Validation on PATCH bodies, Bucket4j token buckets.
- **17 Nov 2026 — Session 2 (continued), U3 done.** Key files:
  `V4__accounts_categories.sql`,
  `accounts/{Account,AccountService,AccountController}`,
  `categories/{Category,CategoryService,DefaultCategories,
  CategoryController}`, `workspaces/{WorkspaceService,
  WorkspaceController}`, `AccountsApiTest`, `CategoriesApiTest`,
  `RlsIsolationTest`. Concepts introduced: a second RLS policy
  (`FOR ALL` with `USING` and `WITH CHECK`), composite foreign
  keys, unique indexes on `lower(name)`, Spring Data derived
  query methods, `saveAndFlush` and translating
  `DataIntegrityViolationException` into a 409, `@JsonValue`
  on enums, `@ResponseStatus`, `@PathVariable`, nested
  workspace-scoped routes, seeding data inside an existing
  transaction.
- **17 Nov 2026 — Session 2 (continued), U4 done.** Key files:
  `V5__transactions_tags.sql`,
  `transactions/{Transaction,TransactionService,
  TransactionController,TransactionQueries,TagRepository,
  TransactionCursor,TagController}`, `AccountRepository`
  (balance queries), `TransactionsApiTest`, `RlsIsolationTest`.
  Concepts introduced: `PUT` full replace vs `PATCH`, keyset
  (cursor) pagination with a row comparison, `JdbcTemplate`
  with a hand-built dynamic query and bound parameters,
  Postgres `ON CONFLICT DO NOTHING` and `array(subquery)`,
  native `@Query` with an interface projection, `@RequestParam`
  validation with `@Min`/`@Max`, composite foreign keys that
  carry `workspace_id` for defense in depth, `-Xlint`
  "this-escape" (don't call overridable methods from a
  constructor), soft delete and restore.
- **17 Nov 2026 — Session 2 (continued), U5 done.** Key files:
  `V6__transfer_legs.sql`,
  `transactions/{TransferService,TransferController,Transaction,
  TransactionQueries,TransactionService}`,
  `transactions/dto/{TransferRequest,TransferResponse}`,
  `TransfersApiTest`, `TransactionsApiTest`. Concepts introduced:
  one business operation writing two rows atomically in a single
  `@Transactional` method, a partial unique index
  (`WHERE ... IS NOT NULL`) as a structural guard, a static
  factory method on an entity, a private `record` used as a
  local helper type, guarding an older endpoint against rows
  a newer feature owns (`transfer_leg`).
- **18 Nov 2026 — Session 3, U6 done.** Key files:
  `common/config/OpenApiConfig`, `OpenApiDocsTest`,
  `lib/api/{client,schema.d}.ts`, `lib/{workspace.tsx,theme.ts,
  theme-init.ts,safe-redirect.ts}`, `lib/queries/{me,auth,
  workspaces}.ts`, `components/auth/auth-form.tsx`,
  `components/shell/{app-shell,sidebar,workspace-switcher,
  new-workspace-dialog,user-menu}.tsx`, `app/(auth)/*`,
  `app/(app)/*`. Concepts introduced: a springdoc `ModelResolver`
  bean (how the OpenAPI document gets field names), `@NotNull` to
  mark response fields required; on the web: App Router route
  groups and layouts, client vs server components
  (`"use client"`), `<Suspense>` around `useSearchParams`,
  TanStack Query mutations and cache seeding, React context,
  `useSyncExternalStore`, a shared in-flight promise to serialise
  token refreshes, Base UI / shadcn menus, dialogs and sheets.
- **18 Nov 2026 — Units renumbered.** Numbers now run in list
  order: Settings U24→U9, Budgets U9→U10, Recurring U10→U11,
  Monthly report U11→U12, CSV export U12→U13, PWA U13→U24
  (U0–U8 and U14–U23 unchanged). References in the context
  files were updated; old commit messages keep the numbers
  that were true when they were written. The comment
  in `V5__transactions_tags.sql` still says U10 for recurring
  (now U11); it is an applied migration, so it was left as is.
- **19 Nov 2026 — Session 3 (continued), U7 done.** Key files:
  `lib/{dates,transactions,category-icons}.ts` (+ tests),
  `lib/queries/{accounts,categories,tags,transactions}.ts`,
  `components/transactions/{quick-add-provider,quick-add-dialog,
  transaction-filters,transaction-list,transactions-view,
  native-select}.tsx`, `app/(app)/transactions/page.tsx`,
  `components/shell/{app-shell,sidebar}.tsx`. Concepts introduced:
  `useInfiniteQuery` and cursor paging, mutation hooks that
  invalidate several query keys, a React context that owns one
  global dialog plus a `keydown` listener, filters kept in the URL
  with `useSearchParams` and `router.replace`, a debounce with a
  timer ref, the "adjust state while rendering" pattern, Base UI
  combobox, native radio inputs styled as a segmented control,
  `@NotNull` on response records so OpenAPI marks fields required.
- **19 Nov 2026 — Session 3 (continued), U8 done.** Key files:
  `lib/{categories,format}.ts` (+ tests), `lib/queries/{accounts,
  categories}.ts`, `components/accounts/{accounts-view,account-dialog,
  account-types}`, `components/categories/{categories-view,
  category-dialog,delete-category-dialog}.tsx`, `app/(app)/{accounts,
  categories}/page.tsx`, `components/shell/sidebar.tsx`. Concepts
  introduced: a PATCH mutation hook per resource, one dialog serving
  both add and edit (mounted only while open), a confirm dialog that
  shows the API's 409 message, a two-level tree built from a flat
  list, signed money input, and the React lint rule against
  components created during render.
- **19 Nov 2026 — U9 done (web settings).** `/settings` page: name,
  timezone (native select from `Intl.supportedValuesOf`), period start
  day (1–28), dues digest checkbox. `useUpdateSettings` PATCHes `/me`
  and seeds the `me` cache with the answer; `lib/settings.ts`
  `changedSettings` sends only changed fields (tested). Sidebar
  Settings now links to `/settings`. No API change.
- **19 Nov 2026 — U10 done (budgets).** API: `V7__budgets.sql`,
  `budgets/{Period,Budget,BudgetRepository,BudgetService,
  BudgetController}` + `dto/`; tests `PeriodTest`, `BudgetsApiTest`.
  Web: `lib/budgets.ts`, `lib/queries/budgets.ts`,
  `components/budgets/budgets-view.tsx`, `app/(app)/budgets/page.tsx`.
  Concepts: Spring Data interface projections for a native query,
  `JOIN LATERAL` for "latest row at or before", a derived value
  (usage) computed per request, `Clock.withZone` for the user's today,
  `@RequestParam` + `@DateTimeFormat`, and `useSearchParams` behind
  `<Suspense>`. Validation: bean validation (`@Min`) answers 400,
  domain `ValidationException` answers 422.
- **19 Nov 2026 — U11 done (recurring + job queue).** API:
  `V8__jobs_recurring.sql`, `jobs/{JobQueue,JobHandler,JobPoller,
  JobsConfig}`, `recurring/{Frequency,Recurrence,RecurringRule,
  RecurringRuleRepository,RecurringService,RecurringController}` +
  `dto/`; tests `RecurrenceTest`, `RecurringApiTest`. Web:
  `lib/recurring.ts`, `lib/queries/recurring.ts`,
  `components/recurring/{recurring-view,rule-dialog}.tsx`,
  `app/(app)/recurring/page.tsx`. Concepts: `@Scheduled` +
  `@EnableScheduling`, `FOR UPDATE SKIP LOCKED` claiming, a job chain
  (each run queues the next), idempotency from a unique constraint plus
  `ON CONFLICT DO NOTHING`, `CurrentUser.runAs` in a background thread
  so RLS still applies, a partial unique index as a dedupe key,
  `@ConditionalOnProperty`, `ON DELETE SET NULL (column)`. Lesson: the
  JVM and database clocks can disagree (2.5 s in Docker here), so "run
  now" jobs are stamped with the database's `now()`.
- **19 Nov 2026 — U12 done (monthly report).** API: `reports/
  {ReportQueries,ReportService,ReportController}` + `dto/MonthlyReport`;
  test `ReportsApiTest`. Web: `lib/reports.ts`, `lib/queries/reports.ts`,
  `components/reports/report-view.tsx`, `app/(app)/reports/page.tsx`.
  Concepts: aggregate SQL with `SUM(...) FILTER (WHERE ...)`, grouping
  a child category under its parent with `COALESCE(parent.id, id)`,
  `LEFT JOIN` for the optional category, filling gaps in a series in
  Java rather than SQL, nested records as one response DTO, Recharts'
  `ResponsiveContainer` (needs a parent with a fixed height). Lesson:
  OpenAPI marks a primitive `long` as optional unless it has
  `@NotNull`, which made every money field `number | undefined` in the
  web types. Types for this unit were generated from a second API
  instance on port 8081 because the dev API on 8080 was still running
  the old build.
