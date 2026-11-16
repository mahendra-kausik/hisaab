# Run from the repository root. On Windows use Git Bash (which provides make).
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
	cd apps/web && npm run dev

# API tests start their own Postgres through Testcontainers (Docker must run).
test-api:
	cd apps/api && ./mvnw -B test

fmt:
	cd apps/api && ./mvnw -B spotless:apply

# Needs the API running (`make dev-api`): reads /v3/api-docs.
gen-api:
	cd apps/web && npm run gen:api

# Everything CI runs: API format check + compile (warnings are errors) + tests,
# then web lint, typecheck, tests and production build.
check:
	cd apps/api && ./mvnw -B spotless:check verify
	cd apps/web && { [ -d node_modules ] || npm ci; } && npm run lint && npm run typecheck && npm run test && npm run build
