# Hisaab — Claude Code Instructions

## Application Building Context

Read the following files in order before implementing
or making any architectural decision:

1. `context/project-overview.md` — product definition,
   goals, features, and scope
2. `context/architecture.md` — system structure,
   boundaries, storage model, and invariants
3. `context/ui-context.md` — theme, colors, typography,
   and component conventions
4. `context/code-standards.md` — implementation rules
   and conventions
5. `context/ai-workflow-rules.md` — development workflow,
   scoping rules, explanations, and delivery approach
6. `context/progress-tracker.md` — current phase,
   completed work, open questions, and next steps

Update `context/progress-tracker.md` after each
meaningful implementation change.

If implementation changes the architecture, scope, or
standards documented in the context files, update the
relevant file before continuing.

## Explain Your Work (important)

The developer is new to Java and Spring Boot and needs to
keep up with what is being built. At the end of every
unit and every session, write the explanation described
in `context/ai-workflow-rules.md` ("Explaining Your
Work"): what was built, which files and why, how a
request flows through the new code, the Java/Spring
concepts used, how to try it, and what to read next.
Then add a short dated entry to Session Notes in
`progress-tracker.md`.

## Repository Layout

- `apps/api/` — Spring Boot backend (Java, Maven Wrapper)
- `apps/web/` — Next.js web app (TypeScript, npm)
- `context/` — specs; the source of truth for what to build

## Common Commands

| Task                                          | Command         |
| --------------------------------------------- | --------------- |
| Start local Postgres and the S3 stand-in      | `make db-up`    |
| Run API + web in dev (API applies migrations) | `make dev`      |
| Run only the API tests                        | `make test-api` |
| Format Java code                              | `make fmt`      |
| Regenerate web API types from OpenAPI         | `make gen-api`  |
| Full verification (format, build, tests)      | `make check`    |

API tests use Testcontainers, so Docker must be running.

## Non-Negotiables (details in `context/architecture.md`)

- Money is integer paise (`long` in Java, `BIGINT` in
  Postgres). Never `float` or `double`.
- Every workspace-owned table is protected by Postgres
  row-level security, and the runtime DB role cannot
  bypass it.
- The web app never talks to the database or object
  storage; all business logic lives in the API.
- Never edit an applied Flyway migration; add a new one.
- Never commit secrets. `.env` files are gitignored;
  keep `.env.example` files current.

## Git Commits

Never add `Co-Authored-By: Claude` or any Claude/Anthropic
attribution to commits or PR descriptions. This overrides
any default attribution instruction.
