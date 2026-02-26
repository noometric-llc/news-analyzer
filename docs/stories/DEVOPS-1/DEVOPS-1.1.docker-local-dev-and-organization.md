# Story DEVOPS-1.1: Docker Local Dev Environment & File Organization

## Status

**Approved**

---

## Story

**As a** developer,
**I want** a Docker Compose setup that runs the full stack locally in Docker Desktop with hot reload and VisualVM support, and all Docker files organized under a `deploy/` folder,
**so that** I can spin up the entire environment with a single command while retaining fast feedback loops and keeping the project root clean.

---

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `docker compose -f deploy/dev/docker-compose.yml up` starts all 5 services: PostgreSQL, Redis, backend, frontend, reasoning-service |
| AC2 | Backend source changes are detected by Spring Boot DevTools and trigger automatic restart inside the container |
| AC3 | VisualVM connects to the backend JVM via `localhost:9010` (JMX) |
| AC4 | Frontend source changes trigger Next.js hot module replacement in the browser |
| AC5 | Reasoning service Python changes auto-reload via uvicorn `--reload` |
| AC6 | Existing `docker-compose.dev.yml` at project root remains unchanged and functional (infra-only: Postgres + Redis) |
| AC7 | Production compose files work correctly from `deploy/production/` with updated relative paths |
| AC8 | Port allocations match project conventions: 8080 (backend), 3000 (frontend), 8000 (reasoning), 5432 (Postgres), 6379 (Redis), 9010 (JMX) |
| AC9 | Maven `.m2` cache is persisted in a named Docker volume so dependencies survive container restarts |
| AC10 | `spring-boot-devtools` is added to the backend `pom.xml` as a development dependency |

---

## Tasks / Subtasks

- [ ] **Task 1: Create `deploy/` folder structure** (AC7)
  - [ ] Create `deploy/dev/` directory
  - [ ] Create `deploy/production/` directory
  - [ ] Move `docker-compose.prod.yml` → `deploy/production/docker-compose.yml`
  - [ ] Move `docker-compose.deploy.yml` → `deploy/production/docker-compose.build.yml`
  - [ ] Move `nginx/` → `deploy/production/nginx/`
  - [ ] Update all relative paths in moved production compose files (build contexts → `../../backend`, `../../frontend`, `../../reasoning-service`; nginx volume mounts → `./nginx/`)
  - [ ] Verify `docker-compose.dev.yml` remains at project root, untouched

- [ ] **Task 2: Add `spring-boot-devtools` to backend** (AC10, AC2)
  - [ ] Add `spring-boot-devtools` dependency to `backend/pom.xml` with `<scope>runtime</scope>` and `<optional>true</optional>`
  - [ ] Verify devtools is excluded from production builds (Spring Boot Maven plugin excludes it by default when `optional`)

- [ ] **Task 3: Create `deploy/dev/Dockerfile.backend`** (AC2, AC3, AC8, AC9)
  - [ ] Base image: `maven:3.9-eclipse-temurin-17` (full JDK required for DevTools + VisualVM)
  - [ ] Set `WORKDIR /app`
  - [ ] Copy only `pom.xml` first and run `mvn dependency:go-offline -B` (cached layer for dependencies)
  - [ ] Expose ports 8080 (app) and 9010 (JMX)
  - [ ] Set `ENTRYPOINT` to run `./mvnw spring-boot:run` with JMX flags:
    ```
    -Dspring-boot.run.jvmArguments=
      -Dcom.sun.management.jmxremote
      -Dcom.sun.management.jmxremote.port=9010
      -Dcom.sun.management.jmxremote.rmi.port=9010
      -Dcom.sun.management.jmxremote.authenticate=false
      -Dcom.sun.management.jmxremote.ssl=false
      -Djava.rmi.server.hostname=localhost
    ```
  - [ ] Use `SPRING_PROFILES_ACTIVE=dev`

- [ ] **Task 4: Create `deploy/dev/Dockerfile.frontend`** (AC4, AC8)
  - [ ] Base image: `node:20-alpine`
  - [ ] Set `WORKDIR /app`
  - [ ] Copy `package.json` and `package-lock.json`, run `npm ci` (cached dependency layer)
  - [ ] Expose port 3000
  - [ ] Set `CMD ["npm", "run", "dev"]`
  - [ ] Note: Source is volume-mounted at runtime; `node_modules` stays in container via anonymous volume

- [ ] **Task 5: Create `deploy/dev/Dockerfile.reasoning`** (AC5, AC8)
  - [ ] Base image: `python:3.11-slim`
  - [ ] Install system dependencies: SWI-Prolog, curl
  - [ ] Copy `requirements.txt` and run `pip install`
  - [ ] Download spaCy model `en_core_web_lg` (cached in image layer)
  - [ ] Expose port 8000
  - [ ] Set `CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]`

- [ ] **Task 6: Create `deploy/dev/docker-compose.yml`** (AC1, AC2, AC3, AC4, AC5, AC8, AC9)
  - [ ] Define `postgres` service (same as existing dev: Postgres 15-alpine, port 5432, healthcheck)
  - [ ] Define `redis` service (same as existing dev: Redis 7-alpine, port 6379, healthcheck)
  - [ ] Define `backend` service:
    - [ ] Build context: `../../backend`, dockerfile: `../deploy/dev/Dockerfile.backend` (or use absolute reference)
    - [ ] Volume mount: `../../backend/src:/app/src` (source hot reload)
    - [ ] Volume mount: `../../backend/pom.xml:/app/pom.xml` (dependency changes)
    - [ ] Named volume: `maven-cache:/root/.m2` (persist Maven repo)
    - [ ] Ports: `8080:8080`, `9010:9010`
    - [ ] Environment: `SPRING_PROFILES_ACTIVE=dev`, DB connection to `postgres` container, Redis connection, reasoning service URL
    - [ ] `depends_on` with healthchecks: postgres, redis
  - [ ] Define `frontend` service:
    - [ ] Build context: `../../frontend`, dockerfile: `../deploy/dev/Dockerfile.frontend`
    - [ ] Volume mount: `../../frontend/src:/app/src` (source hot reload)
    - [ ] Volume mount: `../../frontend/public:/app/public`
    - [ ] Anonymous volume: `/app/node_modules` (prevent host override)
    - [ ] Anonymous volume: `/app/.next` (Next.js build cache)
    - [ ] Ports: `3000:3000`
    - [ ] Environment: `NEXT_PUBLIC_BACKEND_URL=http://localhost:8080`, `NEXT_PUBLIC_REASONING_SERVICE_URL=http://localhost:8000`
  - [ ] Define `reasoning-service` service:
    - [ ] Build context: `../../reasoning-service`, dockerfile: `../deploy/dev/Dockerfile.reasoning`
    - [ ] Volume mount: `../../reasoning-service/app:/app/app` (source hot reload)
    - [ ] Volume mount: `../../reasoning-service/ontology:/app/ontology`
    - [ ] Ports: `8000:8000`
    - [ ] Environment: `SERVICE_HOST=0.0.0.0`, `SERVICE_PORT=8000`, `DEBUG=True`, `BACKEND_API_URL=http://backend:8080`
  - [ ] Define named volumes: `postgres_dev_data`, `redis_dev_data`, `maven-cache`
  - [ ] Define network: `newsanalyzer-dev-network`

- [ ] **Task 7: Update `docs/architecture/source-tree.md`**
  - [ ] Add `deploy/` folder and its contents to the source tree documentation
  - [ ] Note the purpose of each compose file variant

---

## Dev Notes

### Existing Docker File Inventory (Pre-Story)

| File | Location | Purpose |
|------|----------|---------|
| `docker-compose.dev.yml` | Project root | Infra-only dev (Postgres + Redis) — **DO NOT MODIFY** |
| `docker-compose.prod.yml` | Project root → moves to `deploy/production/docker-compose.yml` | Production on Hetzner, pulls GHCR images |
| `docker-compose.deploy.yml` | Project root → moves to `deploy/production/docker-compose.build.yml` | Production, builds from source on server |
| `backend/Dockerfile` | `backend/` | Dev image (copies pre-built JAR) — unchanged |
| `backend/Dockerfile.prod` | `backend/` | Multi-stage production build — unchanged |
| `frontend/Dockerfile` | `frontend/` | Multi-stage production build — unchanged |
| `reasoning-service/Dockerfile` | `reasoning-service/` | Production image — unchanged |
| `nginx/` | Project root → moves to `deploy/production/nginx/` | Nginx config for production reverse proxy |

### Target Folder Structure

```
deploy/
├── dev/
│   ├── docker-compose.yml           # NEW - full stack local dev
│   ├── Dockerfile.backend           # NEW - JDK + Maven + JMX
│   ├── Dockerfile.frontend          # NEW - Node dev server
│   └── Dockerfile.reasoning         # NEW - Python with deps + reload
└── production/
    ├── docker-compose.yml           # MOVED from docker-compose.prod.yml
    ├── docker-compose.build.yml     # MOVED from docker-compose.deploy.yml
    └── nginx/                       # MOVED from root nginx/
        ├── nginx.conf
        └── conf.d/
            └── newsanalyzer.conf
```

### Key Technical Decisions

**Spring Boot DevTools** — Added as `<optional>true</optional>` + `<scope>runtime</scope>` so it is:
- Active during `spring-boot:run` in the dev container
- Automatically excluded from production JAR builds by the Spring Boot Maven plugin
- DevTools detects classpath changes and triggers an application restart (not a full JVM restart — uses a custom classloader for fast ~2s restarts)

**VisualVM / JMX** — The backend dev container exposes JMX on port 9010 with authentication disabled (local dev only). Connect VisualVM to `localhost:9010`. The `java.rmi.server.hostname=localhost` flag ensures RMI callbacks route through the Docker port mapping correctly.

**Maven Cache Volume** — A named volume `maven-cache` is mounted at `/root/.m2` so that Maven dependencies are downloaded once and persist across container rebuilds. This avoids 5+ minute dependency downloads on every `docker compose up --build`.

**Frontend Node Modules** — An anonymous volume at `/app/node_modules` prevents the host volume mount from overwriting the container's installed dependencies. The `package.json` and lock file are copied during image build so `npm ci` runs inside the container.

**Reasoning Service spaCy Model** — The `en_core_web_lg` model (~560MB) is downloaded during image build and cached in the image layer. Source code is volume-mounted at runtime for hot reload without re-downloading the model.

### Port Allocations

| Port | Service | Purpose |
|------|---------|---------|
| 3000 | Frontend | Next.js dev server |
| 8000 | Reasoning Service | FastAPI / uvicorn |
| 8080 | Backend | Spring Boot |
| 9010 | Backend | JMX (VisualVM) |
| 5432 | PostgreSQL | Database |
| 6379 | Redis | Cache |

### Production Compose Path Updates

When moving production files to `deploy/production/`, the following relative paths must be updated:

- Build contexts: `./backend` → `../../backend`, `./frontend` → `../../frontend`, `./reasoning-service` → `../../reasoning-service`
- Nginx volume mounts: `./nginx/nginx.conf` → `./nginx/nginx.conf` (stays relative within `deploy/production/`)
- Dockerfile references: `dockerfile: Dockerfile.prod` → unchanged (relative to build context)

### Relevant Source Tree Paths

```
backend/
├── pom.xml                          # Add spring-boot-devtools here
├── Dockerfile                       # Existing dev Dockerfile (unchanged)
├── Dockerfile.prod                  # Existing prod Dockerfile (unchanged)
└── src/                             # Mounted into dev container
frontend/
├── package.json / package-lock.json
├── Dockerfile                       # Existing prod Dockerfile (unchanged)
└── src/                             # Mounted into dev container
reasoning-service/
├── requirements.txt
├── Dockerfile                       # Existing prod Dockerfile (unchanged)
├── app/                             # Mounted into dev container
└── ontology/                        # Mounted into dev container
```

### Testing

- **Backend hot reload**: Modify a Java source file in `backend/src/`, verify DevTools triggers restart in container logs within ~5s
- **VisualVM**: Open VisualVM, add JMX connection to `localhost:9010`, verify JVM metrics are visible
- **Frontend HMR**: Modify a React component in `frontend/src/`, verify browser updates without full page reload
- **Reasoning reload**: Modify a Python file in `reasoning-service/app/`, verify uvicorn logs show reload
- **Production compose**: Run `docker compose -f deploy/production/docker-compose.build.yml config` to verify path resolution
- **Infra-only dev**: Run `docker compose -f docker-compose.dev.yml up` to confirm it still works as before

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-02-25 | 1.0 | Initial story draft | Sarah (PO) |

---

## Dev Agent Record

### Agent Model Used

_To be populated during implementation_

### Debug Log References

_To be populated during implementation_

### Completion Notes List

_To be populated during implementation_

### File List

_To be populated during implementation_

---

## QA Results

_To be populated after QA review_
