# AI Workflow Rules

## Approach

Build this project incrementally using a spec-driven
workflow. The context files define what to build
(`project-overview.md`), how it is structured
(`architecture.md`, `code-standards.md`,
`ui-context.md`), and the current state of progress
(`progress-tracker.md`). Always implement against these
specs — do not infer or invent behavior from scratch.
Work through the units in `progress-tracker.md` in
order unless told otherwise.

Claude writes all the code. The developer is new to Java
and Spring Boot, so every unit ends with an explanation
(below) that lets them follow and own what was built.

## Scoping Rules

- Work on one feature unit at a time
- Prefer small, verifiable increments over large
  speculative changes
- Do not combine unrelated system boundaries in a
  single implementation step
- An API unit and the web UI that consumes it are
  separate steps: build and test the API first, then
  the UI

## When to Split Work

Split an implementation step if it combines:

- API changes and web UI changes
- A schema migration and unrelated feature logic
- Background job changes and request-path changes
- Multiple unrelated resources or API routes
- Behavior not clearly defined in the context files

If a change cannot be verified end to end quickly,
the scope is too broad — split it.

## Handling Missing Requirements

- Do not invent product behavior not defined in the
  context files
- If a requirement is ambiguous, propose a resolution,
  get confirmation, and record it in the relevant
  context file before implementing
- If a requirement is missing, add it as an open question
  in `progress-tracker.md` before continuing
- Small technical choices inside a unit (helper names,
  internal structure) do not need confirmation if they
  follow `code-standards.md`

## Explaining Your Work

At the end of every unit, and again at the end of every
session, write an explanation in the chat for a
developer who knows programming but is new to Java and
Spring Boot. Use these headings:

1. **What was built** — 2–4 plain sentences: what the
   app or API can do now that it couldn't before.
2. **Files** — every new or changed file with one line
   on its job, grouped by layer (controller, service,
   repository, entity, DTO, migration, config, test,
   web).
3. **How it works** — trace one real request (or job
   run) step by step through the new code, naming the
   classes and methods it passes through, from the
   Spring Security filter to the database and back.
4. **Java and Spring concepts used** — each concept that
   appears in this project for the first time (for
   example dependency injection, annotations, records,
   `@Transactional`, JPA entities and repositories,
   Flyway migrations, Spring Security filters,
   Testcontainers), explained in 2–3 sentences with
   where to see it in this codebase. Concepts already
   covered in earlier Session Notes get a one-line
   reminder at most.
5. **Try it yourself** — exact commands to run the tests
   and the app, and a sample HTTP request (curl) with
   the expected response.
6. **Decisions and deviations** — choices made and why,
   and anything that differs from the specs.
7. **Read next** — 2–3 files, in order, worth reading to
   understand the unit.

Keep it skimmable: short paragraphs, code references as
`ClassName.method()`, and short snippets only (never
whole files). If the developer asks a follow-up
question, answer it plainly and point to the exact
file and line.

Then add a 3–5 line dated entry to Session Notes in
`progress-tracker.md`: units completed, key files, and
the concepts introduced (so later explanations don't
repeat them).

## Protected Files

Do not modify the following unless explicitly instructed:

- `apps/web/components/ui/*` — generated shadcn
  components (add new ones with the CLI)
- `apps/web/lib/api/schema.d.ts` — generated; regenerate
  with `make gen-api`
- Applied Flyway migrations in
  `apps/api/src/main/resources/db/migration/` — add a
  new migration instead
- The Maven Wrapper files (`mvnw`, `mvnw.cmd`,
  `.mvn/wrapper/`) except through the wrapper itself
- `package-lock.json` except through npm
- Any third-party library internals

## Dependencies

- Use the libraries listed in `architecture.md`. Ask
  before adding any other runtime dependency to
  `pom.xml` or `package.json`.
- Let the Spring Boot parent manage versions of Spring
  libraries; pin versions only for libraries it doesn't
  manage. Use the latest stable versions.

## Keeping Docs in Sync

Update the relevant context file whenever implementation
changes:

- System architecture or boundaries → `architecture.md`
- Data model or storage decisions → `architecture.md`
- Code conventions or standards → `code-standards.md`
- Feature scope or behavior → `project-overview.md`
- UI tokens or patterns → `ui-context.md`

## Git

- One commit (or a small series) per unit, with a
  message like `feat(api): transactions CRUD (U4)`
- Never commit `.env` files or secrets

## Before Moving to the Next Unit

1. The current unit works end to end within its defined scope
2. No invariant defined in `architecture.md` was violated
3. New workspace-scoped tables or endpoints have
   cross-workspace access tests
4. `make check` passes (API: Spotless check, compile
   with warnings as errors, JUnit tests on
   Testcontainers; web: lint, typecheck, Vitest,
   `npm run build`)
5. `progress-tracker.md` reflects the completed work and
   any new decisions or open questions
6. The end-of-unit explanation has been written and the
   Session Notes entry added
