# Story INFRA-1.3: Refactor NewsAnalyzer to Call External Reasoning API

## Status

Ready for Development

## Story

**As** a developer maintaining the public `news-analyzer` application,
**I want** the reasoning service removed from the `news-analyzer` repo and all calls to it routed through a configurable `REASONING_SERVICE_URL` environment variable,
**so that** the application cleanly calls Noometric's private reasoning service as an external API rather than running it locally.

## Context

This is the story that completes the IP extraction. After this story:
- `reasoning-service/` no longer exists in `news-analyzer`
- The application still works identically from a user perspective
- Developers who want to run the full stack locally will start the reasoning service from their `noometric-intelligence` clone and point `REASONING_SERVICE_URL` at it
- The public `news-analyzer` repo has no proprietary Noometric IP in it

**Prerequisite:** INFRA-1.2 must be complete so the API contract is defined before code is changed.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `reasoning-service/` directory is removed from `news-analyzer` (not present in the repo, not just gitignored) |
| AC2 | `REASONING_SERVICE_URL` environment variable is introduced and used wherever the reasoning service URL was previously hardcoded |
| AC3 | `docker-compose.yml` no longer builds or runs the reasoning service container |
| AC4 | `docker-compose.yml` accepts `REASONING_SERVICE_URL` to point to an external reasoning service |
| AC5 | nginx configuration routes to `REASONING_SERVICE_URL` instead of a local container name |
| AC6 | A `.env.example` (or equivalent) documents `REASONING_SERVICE_URL` with a sensible default for local dev |
| AC7 | Application runs successfully when a reasoning service is available at `REASONING_SERVICE_URL` |
| AC8 | README is updated with instructions for local dev (clone `noometric-intelligence`, start reasoning service, set env var) |
| AC9 | All existing tests pass (no regressions) |

## Tasks / Subtasks

- [ ] Task 1: Introduce `REASONING_SERVICE_URL` environment variable (AC2)
  - [ ] Identify all places in the codebase that reference the reasoning service hostname/URL:
    - `docker-compose.yml` service references
    - nginx config upstream or proxy_pass directives
    - Any hardcoded `http://reasoning-service:8000` or similar in backend Java or frontend code
  - [ ] Replace all hardcoded references with `REASONING_SERVICE_URL` (or the nginx-appropriate equivalent)
  - [ ] Default value for local dev: `http://localhost:8000`

- [ ] Task 2: Update `docker-compose.yml` (AC3, AC4)
  - [ ] Remove the `reasoning-service` service block entirely
  - [ ] Remove any `depends_on: reasoning-service` references in other services
  - [ ] Remove any `build: ./reasoning-service` or image references for the reasoning service
  - [ ] Add `REASONING_SERVICE_URL` as an environment variable that is passed through to the services that need it (backend, nginx)
  - [ ] Example approach:
    ```yaml
    # In .env or docker-compose.yml environment section:
    REASONING_SERVICE_URL: ${REASONING_SERVICE_URL:-http://localhost:8000}
    ```

- [ ] Task 3: Update nginx configuration (AC5)
  - [ ] Find the nginx config (likely `nginx/nginx.conf` or `docker/nginx.conf`)
  - [ ] Update the upstream block that proxies to the reasoning service:
    - Before: `server reasoning-service:8000;`
    - After: Use environment variable substitution or a configurable upstream
  - [ ] Note: nginx doesn't natively support env vars in config — options:
    - **Option A**: Use `envsubst` in the nginx container entrypoint to substitute `$REASONING_SERVICE_URL` at container start
    - **Option B**: Use `nginx.conf.template` with `envsubst` (standard pattern)
    - Document chosen approach in Dev Notes

- [ ] Task 4: Remove `reasoning-service/` from the repo (AC1)
  - [ ] Verify INFRA-1.1 is complete (all content safely in `noometric-intelligence`)
  - [ ] Remove directory: `git rm -r reasoning-service/`
  - [ ] Also remove any reasoning-service-specific files in the root: `.pytest_cache` if present, any reasoning-service test configs
  - [ ] Check `.gitignore` for any reasoning-service entries that are now irrelevant

- [ ] Task 5: Update `.env.example` or local dev documentation (AC6)
  - [ ] Create or update `.env.example`:
    ```
    # Noometric Intelligence reasoning service URL
    # For local dev: clone noometric-intelligence and start the reasoning service
    # See README for instructions
    REASONING_SERVICE_URL=http://localhost:8000

    # Anthropic API key (required for LLM-based analysis)
    ANTHROPIC_API_KEY=your-key-here
    ```

- [ ] Task 6: Run the application and verify it works (AC7, AC9)
  - [ ] Start reasoning service from `noometric-intelligence`:
    ```bash
    cd D:/VSCProjects/noometric-intelligence/reasoning-service
    docker build -t noometric-reasoning .
    docker run -p 8000:8000 -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY noometric-reasoning
    ```
  - [ ] Set `REASONING_SERVICE_URL=http://localhost:8000` in local env
  - [ ] Start `news-analyzer` application: `docker-compose up`
  - [ ] Verify: entity extraction works, bias detection works, eval harness can reach `/eval/extract` endpoints
  - [ ] Run any existing test suite

- [ ] Task 7: Update README (AC8)
  - [ ] Add a "Local Development Setup" section explaining the two-repo workflow:
    1. Clone `noometric-intelligence` (requires access — contact Noometric LLC)
    2. Start reasoning service from `noometric-intelligence/reasoning-service/`
    3. Set `REASONING_SERVICE_URL=http://localhost:8000` in your environment
    4. Start `news-analyzer` with `docker-compose up`
  - [ ] Note: public contributors without access to `noometric-intelligence` will not be able to run the full reasoning stack locally. Consider documenting whether a stub/mock mode is planned.

- [ ] Task 8: Commit (AC1–AC9)
  - [ ] Commit message:
    ```
    feat(INFRA-1.3): remove reasoning-service, add REASONING_SERVICE_URL external API config

    Extracts proprietary reasoning service from news-analyzer. Application now calls
    the Noometric Intelligence reasoning service as an external HTTP API via the
    REASONING_SERVICE_URL environment variable.

    - Removed: reasoning-service/ directory (migrated to noometric-intelligence in INFRA-1.1)
    - Updated: docker-compose.yml, nginx config, .env.example
    - Updated: README with two-repo local dev instructions
    ```

## Dev Notes

### nginx Environment Variable Pattern

nginx doesn't natively interpolate environment variables in config files. The standard pattern is `envsubst`:

**`nginx/nginx.conf.template`:**
```nginx
upstream reasoning_service {
    server ${REASONING_SERVICE_HOST}:${REASONING_SERVICE_PORT};
}
```

**`nginx/docker-entrypoint.sh`:**
```bash
#!/bin/sh
# Parse REASONING_SERVICE_URL into host and port for nginx
export REASONING_SERVICE_HOST=$(echo $REASONING_SERVICE_URL | sed 's|http://||' | cut -d: -f1)
export REASONING_SERVICE_PORT=$(echo $REASONING_SERVICE_URL | sed 's|http://||' | cut -d: -f2)
envsubst '${REASONING_SERVICE_HOST} ${REASONING_SERVICE_PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
exec nginx -g 'daemon off;'
```

Alternatively, if the reasoning service URL is always a simple hostname:port, it may be simpler to pass just `REASONING_SERVICE_HOST` and `REASONING_SERVICE_PORT` as separate env vars.

### What If a Developer Doesn't Have noometric-intelligence Access?

NewsAnalyzer's public README should be honest: full functionality requires the Noometric reasoning service. Options to consider for the community (not required in this story, but worth noting):
- A public "stub" reasoning service that returns mock responses
- A documented `REASONING_SERVICE_URL` that points to a Noometric-hosted public demo endpoint (implemented in INFRA-2)

For now, documenting the gap is sufficient.

### Verifying the Git Removal

After `git rm -r reasoning-service/`, confirm it's truly gone:
```bash
git ls-files reasoning-service/
# Should return nothing
```

The content will still exist in git history, which is fine — it's MIT-licensed content from before the restructure.

## Dev Agent Record

### Agent Model Used

_To be filled in after completion_

### File List

| File | Action | Description |
|------|--------|-------------|
| `reasoning-service/` | DELETED | Removed from news-analyzer (migrated to noometric-intelligence) |
| `docker-compose.yml` | MODIFIED | Remove reasoning-service block; add REASONING_SERVICE_URL |
| `nginx/nginx.conf` (or template) | MODIFIED | Route to REASONING_SERVICE_URL instead of local container |
| `.env.example` | MODIFIED/NEW | Document REASONING_SERVICE_URL |
| `README.md` | MODIFIED | Two-repo local dev instructions |

### Completion Notes

_To be filled in after completion_

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-04-14 | 1.0 | Initial story draft | Sarah (PO) |
