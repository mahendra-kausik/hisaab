# Architecture Context

## Stack

| Layer           | Technology                                                        | Role                                                         |
| --------------- | ----------------------------------------------------------------- | ------------------------------------------------------------ |
| Language        | Java 25 (LTS); fall back to 21 only if a tool below lacks support | Backend language                                             |
| API framework   | Spring Boot (latest stable), Spring Web MVC                       | HTTP API; owns all business logic and validation             |
| Build           | Maven, via the Maven Wrapper (`./mvnw`)                           | Dependencies, build, tests                                   |
| Data access     | Spring Data JPA (Hibernate) + `JdbcTemplate` for reports and RLS  | Entities, repositories, hand-written SQL where clearer       |
| Migrations      | Flyway (plain SQL files)                                          | Schema, RLS policies, grants                                 |
| Connection pool | HikariCP (Spring Boot default) + PostgreSQL JDBC driver           | Database connections                                         |
| Validation      | Jakarta Bean Validation (`spring-boot-starter-validation`)        | Request validation on DTOs                                   |
| Config          | `application.yml` + environment variables, `@ConfigurationProperties` | Typed configuration                                      |
| Security        | Spring Security, BCrypt passwords, JWT via Spring Security OAuth2 Resource Server (Nimbus) | Authentication, rotating refresh tokens     |
| API docs        | springdoc-openapi                                                 | OpenAPI JSON at `/v3/api-docs`, Swagger UI in dev            |
| Database        | PostgreSQL 16 (local: Docker; hosted: Neon)                       | All structured data; row-level security                      |
| Background jobs | Postgres-backed `jobs` table, polled by a `@Scheduled` worker inside the same Spring Boot app | Recurring entries, dues digests, exports, later imports |
| Rate limiting   | Bucket4j (in-memory)                                              | Login and signup throttling                                  |
| Outbound email  | Spring Mail (SMTP); provider to be chosen (see `progress-tracker.md`) | Daily dues digest to the user; later verification and resets |
| Documents       | Apache PDFBox (PDF statements), Apache POI (Excel export)         | Phase 1.5 exports                                            |
| File storage    | Cloudflare R2 via AWS SDK for Java v2 (S3 client)                 | Attachments, exports, database backups (Phase 1.5)           |
| Web framework   | Next.js (App Router) + TypeScript (strict)                        | Web UI and PWA; proxies `/api` to the API                    |
| UI              | Tailwind CSS + shadcn/ui, Lucide icons                            | Components and styling                                       |
| Web data        | TanStack Query, openapi-typescript, Zod, react-hook-form          | Server state, generated API types, form validation           |
| Charts          | Recharts                                                          | Reports                                                      |
| Hosting (plan)  | Web: Vercel. API: Render (Docker). DB: Neon                       | Free tiers; worker runs inside the API, so one service. Free instances have 512 MB RAM, so cap the JVM heap (about `-Xmx300m`) |
| Testing         | JUnit 5, Spring Boot Test, MockMvc, AssertJ, Testcontainers (real Postgres); Vitest for web helpers | Automated verification |
| Code quality    | Spotless (google-java-format), compiler warnings as errors        | Formatting and hygiene                                       |

Not used, on purpose: Lombok (so the code stays explicit
for a developer new to Java; use records and plain
classes instead)
and Hibernate schema generation (`ddl-auto` is
`validate`; Flyway owns the schema).

## System Boundaries

The API is organised **by feature**. Each feature
package holds its own controller, service, repository,
entities, and DTOs. Base package: `in.hisaab`.

- `apps/api/src/main/java/in/hisaab/<feature>/` — one
  package per feature (`auth`, `users`, `workspaces`,
  `accounts`, `categories`, `transactions`, `budgets`,
  `recurring`, `reports`, `parties`, `projects`, `dues`,
  `attachments`, `exports`). Inside each:
  - `XController` — HTTP layer only: maps the request,
    validates the DTO, calls one service method, returns
    a DTO. No business logic or SQL.
  - `XService` — business logic and transaction
    boundaries (`@Transactional`). Owns rules such as
    "outstanding is computed" and "overpayment is
    rejected".
  - `XRepository` — Spring Data JPA repository, or a
    small `JdbcTemplate` query class for hand-written SQL.
  - `X` — JPA entity.
  - `dto/` — Java records for request and response
    bodies. Entities are never returned from controllers.
- `.../common/` — shared code: `web/` (error handler,
  error response shape, pagination), `security/`
  (Spring Security config, JWT, current-user access),
  `db/` (RLS context), `money/` (paise helpers),
  `config/` (typed configuration).
- `.../jobs/` — job queue (enqueue, claim, complete,
  retry), the `@Scheduled` poller, and the
  `JobHandler` interface that feature handlers implement.
- `apps/api/src/main/resources/db/migration/` — Flyway
  SQL migrations (`V1__baseline.sql`, `V2__...`),
  including RLS policies and grants.
- `apps/api/src/main/resources/application.yml` —
  configuration (profiles: `dev`, `test`, `prod`).
- `apps/api/src/test/java/in/hisaab/` — tests, mirroring
  the main package structure.
- `apps/web/app/` — Next.js routes and layouts.
- `apps/web/components/` — Feature components;
  `components/ui/` is shadcn-generated.
- `apps/web/lib/` — API client, generated types
  (`lib/api/schema.d.ts`), query hooks, formatting
  helpers.
- `context/` — Specs; the source of truth for what to
  build.

Dependency direction: controller → service → repository.
A feature may call another feature's **service**, never
its repository.

## Request Flow

Browser → Next.js (same origin) → `/api/*` rewrite →
Spring Boot (Spring Security filter chain → controller →
service → repository) → Postgres.

The browser never calls the API origin directly, so auth
cookies stay first-party. Future mobile clients call the
API directly with a bearer token.

## Storage Model

- **PostgreSQL**: everything structured — users,
  workspaces, memberships, accounts, categories, tags,
  transactions, budgets, recurring rules, parties,
  projects, dues, attachment metadata, jobs, refresh
  tokens, audit log.
- **R2 (object storage)**: attachment files, generated
  exports (CSV, Excel, PDF), nightly database dumps. The
  database stores only the object key, MIME type, size,
  and owning workspace.
- **Browser**: nothing sensitive beyond httpOnly
  cookies. No tokens in localStorage.

## Data Model (Phase 1 and 1.5)

All IDs are UUIDs (`gen_random_uuid()`). All timestamps
are `timestamptz` in UTC. All money is `BIGINT` paise.
Transaction and due amounts are always positive; the
direction comes from the type.

- `users`: id, email (unique, stored lowercased),
  password_hash, name, timezone (default
  `Asia/Kolkata`), period_start_day (1–28, default 1),
  dues_digest_enabled (default true), created_at,
  updated_at.
- `refresh_tokens`: id, user_id, token_hash, family_id,
  expires_at, revoked_at, created_at.
- `workspaces`: id, name, kind (`personal` |
  `business`), created_by, created_at, updated_at.
- `workspace_members`: workspace_id, user_id, role
  (`owner` | `editor` | `viewer`), created_at. Primary
  key (workspace_id, user_id).
- `accounts`: id, workspace_id, name, type (`cash` |
  `bank` | `credit_card` | `wallet`),
  opening_balance_paise (may be negative, e.g. credit
  card dues), archived_at, created_at, updated_at. Names
  are unique per workspace, ignoring case (unique index
  on `workspace_id, lower(name)`). Accounts are archived,
  never deleted.
- `categories`: id, workspace_id, parent_id (nullable;
  one level of nesting only), name, kind (`expense` |
  `income`), is_system, hidden_at, sort_order.
  `is_system` is true for every seeded default: it can be
  renamed or hidden but never deleted. User-added
  categories can be deleted while nothing references them
  (a foreign key violation becomes 409 `category_in_use`).
  A composite foreign key `(parent_id, workspace_id, kind)
  → (id, workspace_id, kind)` makes the database
  guarantee a parent is in the same workspace and of the
  same kind; the one-level rule is checked in the service.
  Sibling names are unique ignoring case (unique index on
  `workspace_id, kind, parent_id, lower(name)` with
  `NULLS NOT DISTINCT`, so Postgres 15+).
- `tags`: id, workspace_id, name (1–50 characters, unique
  per workspace ignoring case), created_at.
  `transaction_tags`: workspace_id, transaction_id, tag_id.
  `transaction_tags` repeats `workspace_id` so it follows the
  standard RLS pattern, and composite foreign keys
  `(transaction_id, workspace_id)` and `(tag_id,
  workspace_id)` keep a transaction and its tags in one
  workspace. Requests and responses carry tags as plain
  names; a tag is created the first time it is used, and
  names match ignoring case (the first spelling wins).
  `GET .../tags` lists names for autocomplete.
- `parties`: id, workspace_id, name, phone, role, notes,
  archived_at, created_at, updated_at.
- `projects`: id, workspace_id, name, notes,
  archived_at, created_at, updated_at.
- `transactions`: id, workspace_id, account_id, type
  (`expense` | `income` | `transfer_out` |
  `transfer_in`), amount_paise (> 0), occurred_on (date,
  in the user's local calendar), category_id (nullable),
  party_id (nullable), project_id (nullable), due_id
  (nullable), payment_method (nullable; `cash` | `upi` |
  `card` | `bank_transfer` | `cheque` | `other`), note
  (up to 500 characters), transfer_group_id
  (set on both rows of a transfer), recurring_rule_id and
  recurrence_date (nullable; unique together), source
  (`manual` now; `statement` | `email` | `sms` |
  `shortcut` later), raw_description and merchant
  (nullable; reserved for Phase 2), created_by,
  created_at, updated_at, deleted_at.
  - U4 (`V5`) creates the table with `transfer_group_id`,
    `source`, `raw_description` and `merchant`. `party_id`,
    `project_id`, `due_id`, `recurring_rule_id` and
    `recurrence_date` are added by the units that create
    the tables they point to (U11, U14–U16).
  - Composite foreign keys `(account_id, workspace_id)` and
    `(category_id, workspace_id)` make the database refuse
    a transaction that points at another workspace's
    account or category (foreign key checks ignore RLS).
    `accounts` and `categories` gained
    `UNIQUE (id, workspace_id)` as their targets.
  - CHECK constraints: a transfer type if and only if
    `transfer_group_id` is set; transfers have no category.
  - Category rules in the API: an expense uses an expense
    category and an income uses an income category (422
    `invalid_category`); hidden categories are allowed. A
    category used by any transaction, including a
    soft-deleted one, cannot be deleted.
  - Archived accounts: creating a transaction on one, or
    moving one onto it, is 422 `account_archived`. Editing
    a transaction that already lives there, delete and
    restore still work.
  - List order is `occurred_on DESC, created_at DESC, id
    DESC` (newest entered first within a day); the cursor
    is the opaque encoding of those three values. Filters:
    from/to (inclusive), type, account_id, category_id
    (the category and its children), tag (ignoring case),
    and `q`, a case-insensitive search of the note.
  - Edit is `PUT` with the same body as create; optional
    fields left out are cleared. `/transactions` accepts only
    `expense` and `income` (transfer types are 422
    `invalid_type`); transfers go through `/transfers`.
  - Transfers (U5, `V6`): `POST /transfers` writes a
    `transfer_out` row on `from_account_id` and a
    `transfer_in` row on `to_account_id` in one database
    transaction, sharing a new `transfer_group_id` and the
    same amount, date, note and payment method (no category,
    no tags). The transfer's id in every `/transfers` path
    and response is that group id. `GET`, `PUT` (full
    replace, like transactions), `DELETE` (soft-deletes both
    legs) and `POST .../{id}/restore` always act on both
    rows. Both accounts must be different (422
    `same_account`), in the workspace and not archived (the
    same archived rule as transactions: unchanged accounts
    on edit, delete and restore are fine). `V6` adds a unique
    index on `(transfer_group_id, type)`, so a group can
    never have more than one leg of each type.
  - Both legs appear in `GET /transactions` (with
    `transfer_group_id` set) so balances and the list agree.
    `PUT`, `DELETE` and restore on `/transactions/{id}` for
    a transfer leg are refused with 422 `transfer_leg`
    (`details.transfer_group_id` names the transfer to use
    instead), because changing one leg would make money
    appear or vanish.
  - Account balance = opening balance + sum of `income`
    and `transfer_in` − sum of `expense` and
    `transfer_out`, over rows with `deleted_at IS NULL`.
    The sums are native queries in `AccountRepository`
    (not a call into the transactions package, which
    already depends on `AccountService`).
- `budgets`: id, workspace_id, category_id,
  amount_paise, effective_from (a period start date),
  created_at. The budget in effect for a period is the
  latest one with `effective_from` on or before it.
  - U10 (`V7`): `UNIQUE (workspace_id, category_id,
    effective_from)`, composite FK `(category_id,
    workspace_id)` with `ON DELETE CASCADE` (so budgets never
    block a category delete), RLS like the other tables.
    `amount_paise = 0` means "no budget from this period on".
  - `GET /workspaces/{id}/budgets?period=YYYY-MM-DD` (any date
    in the wanted period; default today in the caller's
    timezone) lists budgeted visible top-level expense
    categories with `amount_paise` and `spent_paise`
    (children roll up; only live `expense` rows). `PUT
    /workspaces/{id}/budgets/{categoryId}` sets the amount
    from the current period on (204). Periods come from the
    caller's `period_start_day` via `Period.containing`.
- `recurring_rules`: id, workspace_id, account_id, type
  (`expense` | `income`), amount_paise, category_id,
  party_id, project_id, note, frequency (`weekly` |
  `monthly` | `yearly`), interval, anchor_date,
  next_run_on, ends_on, paused_at.
- `dues`: id, workspace_id, party_id, direction
  (`receivable` | `payable`), amount_paise, reason,
  due_date (nullable), project_id, category_id,
  origin_transaction_id (nullable; the money handed over
  when the due was created, e.g. a loan given — an
  expense for a receivable, income for a payable),
  cancelled_at, created_by, created_at, updated_at,
  deleted_at.
- `dues_digests`: user_id, digest_date, sent_at. Unique
  (user_id, digest_date) so each user gets at most one
  digest per day. User-scoped and written only by the
  worker.
- `attachments`: id, workspace_id, transaction_id or
  due_id, object_key, mime_type, size_bytes, created_by,
  created_at.
- `jobs`: id, workspace_id (nullable for system jobs),
  kind, payload (jsonb), run_at, status (`queued` |
  `running` | `done` | `failed`), attempts, last_error,
  locked_at, created_at. Claimed with
  `SELECT ... FOR UPDATE SKIP LOCKED`.
- `audit_log`: id, workspace_id, user_id, action,
  entity_type, entity_id, at.

## Auth and Access Model

- Users sign up and log in with email + password, hashed
  with Spring Security's `BCryptPasswordEncoder`
  (strength 12).
- Login issues a short-lived access JWT (about 15
  minutes) and a rotating refresh token (about 30 days).
  Refresh tokens are stored hashed; reuse of an
  already-rotated token revokes its whole family.
- Web: both tokens are `httpOnly`, `Secure`,
  `SameSite=Lax` cookies set through the same-origin
  proxy. Mutating requests must be JSON
  (`Content-Type: application/json`). CORS on the API
  allows only the web origin. Spring Security's CSRF
  token protection is turned off because of these three
  measures; record this reasoning in a comment in the
  security config.
- Access JWTs are signed with HMAC-SHA256 (`JWT_SECRET`
  environment variable, at least 32 characters) and
  verified by Spring Security's OAuth2 Resource Server. A
  custom `BearerTokenResolver` (`CookieBearerTokenResolver`)
  reads the token from the access cookie first, then the
  `Authorization` header, and ignores tokens on
  `/api/v1/auth/**` so an expired cookie can never block
  login or refresh.
- Cookies: `hisaab_access` (`Path=/api`, 15 min) and
  `hisaab_refresh` (`Path=/api/v1/auth`, 30 days), so the
  refresh token is only ever sent to auth endpoints. Tokens
  are never returned in a response body; a mobile client
  that needs them gets that added when it exists.
- Endpoints: `POST /api/v1/auth/{signup,login,refresh,logout}`
  (public), `GET` and `PATCH /api/v1/me` (settings; `/me`
  also lists the user's workspaces), `POST /api/v1/workspaces`
  (`{name, kind}`; creates another workspace with its
  default Cash account and categories). Every other route
  requires authentication.
- Every new workspace, including the one signup creates,
  is seeded inside `WorkspaceService.create`'s transaction
  with a Cash account and the default categories for its
  kind (`AccountService.createDefaults`,
  `CategoryService.createDefaults`). The owner membership
  is flushed first because the seed inserts' RLS policy
  checks membership.
- `refresh_tokens` has **no RLS**: refresh arrives before
  any user is known (the token is the credential), and the
  table is user-owned, not workspace-owned. Only the
  SHA-256 hash of a token is stored. Each refresh revokes
  the old token and issues a new one in the same
  `family_id`; presenting a revoked token revokes the whole
  family. Expired rows are not purged yet.
- Signup generates the user id in Java, then runs
  `CurrentUser.runAs(id, ...)` around one transaction that
  creates the user, calls `WorkspaceService.create` for the
  personal workspace, and issues the first tokens. Binding
  the id before the transaction begins lets
  `RlsTransactionManager` tag it normally.
- `CurrentUser.id()` returns the id bound by `runAs` if
  there is one, otherwise the subject of the request's
  `JwtAuthenticationToken`.
- Rate limiting: `AuthRateLimitFilter` (Bucket4j, in
  memory, per client IP, LRU-capped at 10,000 entries)
  allows 10 logins per minute and 5 signups per 10 minutes,
  then answers 429 with `Retry-After`. `server.forward-
  headers-strategy=native` makes the client IP honour
  `X-Forwarded-For` only from trusted proxies.
- CORS is configured in `SecurityConfig` (the earlier
  `WebConfig` was removed) so it runs inside the security
  filter chain.
- Future mobile: the same endpoints; the API also
  accepts `Authorization: Bearer <access token>`.
- Web client (U6): every API call goes through
  `lib/api/client.ts` (`apiFetch`), which turns the error
  body into an `ApiError`. On a 401 from a non-auth path it
  calls `POST /auth/refresh` and retries once; all requests
  share **one** in-flight refresh, because rotation treats
  two refreshes with the same token as reuse and revokes the
  session. The signed-in check is client-side
  (`AppShell` calls `GET /me`, redirects to
  `/login?next=...` on 401): the cookies' paths are `/api` and
  `/api/v1/auth`, so a page request to Next.js never carries
  them and a Next `proxy.ts` could not see the session.
  `?next=` goes through `safeNext` so it can only be a path on
  this site. The active workspace id is kept in
  `localStorage` (`hisaab.workspace`) and read through
  `useWorkspace()`; if it is not one of the user's workspaces
  the personal one is used. It is not a secret, only a
  preference.
- OpenAPI names (U6): springdoc builds the document with its
  own Jackson mapper, which ignores
  `spring.jackson.property-naming-strategy`, so
  `OpenApiConfig` supplies a `ModelResolver` with
  `SNAKE_CASE`. `OpenApiDocsTest` guards it. Response DTO
  fields that are always present carry `@NotNull`, which
  makes the generated TypeScript fields required.
- Data belongs to a workspace, not a user. Users reach a
  workspace through `workspace_members`. Signup creates
  a personal workspace with the user as owner.
- Workspace-scoped routes live under
  `/api/v1/workspaces/{workspaceId}/...`. Services call
  `WorkspaceAccess.require(workspaceId, role)` first;
  non-members get 404 so workspace existence is not
  revealed.
- Phase 1 uses only the `owner` role; `editor` and
  `viewer` exist in the schema for later sharing.
- Postgres RLS is the second line of defense:
  - The API's datasource connects as `hisaab_app`, a role
    that owns no tables and lacks `BYPASSRLS`. Flyway
    runs migrations on startup as `hisaab_owner`
    (separate `spring.flyway.user` / `password`).
  - Every workspace-scoped table has RLS enabled **and
    forced**, with policies that allow only rows whose
    `workspace_id` is in the current user's memberships:
    `workspace_id IN (SELECT workspace_id FROM
    workspace_members WHERE user_id =
    app_current_user_id())`. Copy this pattern for each new
    table.
  - `app_current_user_id()` (SQL function, V2) reads the
    `app.user_id` setting and returns NULL when it is unset
    or empty, so no user means no rows.
  - Exceptions in the bootstrap tables (V2): `users` has
    **no RLS** (it is not workspace-owned, and signup and
    login read it before any user is known; the API only
    queries it by id or email). `workspaces` rows are also
    visible to their `created_by` user, so a workspace can
    be inserted and its owner membership added in the same
    transaction. `workspace_members` shows a user only
    their own rows, and a user may insert only an `owner`
    row for themselves in a workspace they created
    (`app_is_workspace_creator()`). Only SELECT and INSERT
    policies exist on both tables; add UPDATE/DELETE
    policies in the unit that needs them.
  - A custom transaction manager (`RlsTransactionManager`
    in `common/db`, extending `JpaTransactionManager`)
    runs `SELECT set_config('app.user_id', ?, true)`
    right after every transaction begins, using the
    current user from `CurrentUser` (in `common/security`,
    backed by a Java `ScopedValue`, bound with
    `runAs(userId, ...)`). The bean is named
    `transactionManager` so Spring Data uses it. The setting is
    transaction-local, so it is safe with connection
    pooling. With no current user it sets nothing, and
    RLS returns no rows.
  - All database access happens inside service
    transactions. `spring.jpa.open-in-view` is `false`.
  - The worker runs each job inside
    `CurrentUser.runAs(userId, ...)` so the same
    mechanism applies; it never connects as a superuser
    or table owner.

## Invariants

1. Money is stored and transmitted as integer paise
   (`BIGINT` / JSON integer). No floats anywhere in
   storage, the API, or calculations. Conversion to
   rupees happens only for display in the UI.
2. Every workspace-scoped table has a non-null
   `workspace_id`, RLS enabled and forced, and a
   cross-workspace access test.
3. The runtime database role never owns tables and never
   bypasses RLS.
4. Derived values are computed, never stored as editable
   fields: account balances, due outstanding amounts,
   due states, budget usage, report totals. A cache may
   be added later only if it can be rebuilt from source
   rows.
5. A transfer is always a linked pair (`transfer_out` +
   `transfer_in`, same `transfer_group_id`, amount, and
   date) that is created, edited, and deleted together,
   and never counted as income or expense.
6. Request handlers do not run long work (more than about
   1 second) or scheduled work; they enqueue a job.
7. Jobs are idempotent: running a job twice gives the
   same result, enforced with unique constraints (e.g.
   `(recurring_rule_id, recurrence_date)`).
8. The web app never accesses the database or object
   storage directly. The API is the only gateway, so a
   mobile client can be added without backend changes.
9. Transactions and dues are soft-deleted (`deleted_at`).
   Soft-deleted rows are excluded from lists, balances,
   and reports. Hard deletion happens only through
   account deletion.
10. A payment linked to a due must match its direction
    (receivable → income, payable → expense) and its
    party, and can never push the outstanding amount
    below zero. A due's origin transaction (money handed
    over when lending or borrowing) has the opposite
    type, is not a payment, and never reduces the
    outstanding amount.
11. Reminders are only ever sent to the user who owns
    the workspace data. The system never contacts a
    party.
12. Applied migrations are never edited. Every schema
    change is a new Flyway migration.
13. Secrets never appear in code, logs, or the repo.
    Bank statement passwords (Phase 2) are never
    persisted.
