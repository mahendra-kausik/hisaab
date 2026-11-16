# Run from the repository root, in any terminal (cmd, PowerShell, Git Bash).
# The recipes below use POSIX shell syntax (./mvnw, [ -d ... ]). On Windows,
# make would otherwise use cmd.exe and fail, so use the sh that comes with
# Git for Windows (8.3 path avoids the space in "Program Files").
NPM := npm
ifeq ($(OS),Windows_NT)
  # npm's bash wrapper would pick WSL's bash.exe from PATH; npm.cmd avoids it.
  NPM := npm.cmd
  GIT_SH := $(wildcard C:/PROGRA~1/Git/usr/bin/sh.exe)
  ifneq ($(GIT_SH),)
    SHELL := $(GIT_SH)
    # mvnw needs uname, dirname, ... from Git's usr/bin. Appended (not
    # prepended) so it never shadows tools you already have.
    export PATH := $(PATH);C:/PROGRA~1/Git/usr/bin
  endif
endif

.PHONY: db-up db-down dev dev-api dev-web test-api fmt gen-api check

# Postgres in Docker (waits until it is healthy).
db-up:
	docker compose up -d --wait postgres

db-down:
	docker compose down

# API and web together. Ctrl+C stops both. Run `make db-up` first.
dev:
	$(MAKE) -j2 dev-api dev-web

# The dev profile also reads apps/api/.env (copy .env.example to .env).
dev-api:
	cd apps/api && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

dev-web:
	cd apps/web && $(NPM) run dev

# API tests start their own Postgres through Testcontainers (Docker must run).
test-api:
	cd apps/api && ./mvnw -B test

fmt:
	cd apps/api && ./mvnw -B spotless:apply

# Needs the API running (`make dev-api`): reads /v3/api-docs.
gen-api:
	cd apps/web && $(NPM) run gen:api

# Everything CI runs: API format check + compile (warnings are errors) + tests,
# then web lint, typecheck, tests and production build.
check:
	cd apps/api && ./mvnw -B spotless:check verify
	cd apps/web && { [ -d node_modules ] || $(NPM) ci; } && $(NPM) run lint && $(NPM) run typecheck && $(NPM) run test && $(NPM) run build
