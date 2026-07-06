# Technical Debt Tracking

This document tracks known technical debt items that need to be addressed.

## Active Items

### TD-001: Remove Legacy Admin Factbase Pages

**Status:** PENDING
**Created:** 2026-01-06
**Priority:** LOW (cleanup after migration)
**Owner:** TBD

**Description:**
The admin section has been reorganized from `/admin/factbase/` to `/admin/knowledge-base/government/` to mirror the public Knowledge Base structure. The legacy factbase pages should be removed once the new admin pages have full CRUD functionality.

**Location:**
- `frontend/src/app/admin/factbase/` - Entire directory and subdirectories
- `frontend/src/components/admin/AdminSidebar.tsx` - Remove "Factbase (Legacy)" menu section

**Legacy Routes to Remove:**
```
/admin/factbase/executive/agencies
/admin/factbase/executive/positions
/admin/factbase/executive/govman
/admin/factbase/legislative/members
/admin/factbase/legislative/members/search
/admin/factbase/legislative/legislators-repo
/admin/factbase/legislative/committees
/admin/factbase/judicial/courts
/admin/factbase/regulations/federal-register
/admin/factbase/regulations/search
/admin/factbase/regulations/us-code
```

**New Structure (Placeholder Pages):**
```
/admin/knowledge-base/government/
├── executive/
│   ├── president/
│   ├── vice-president/
│   ├── eop/
│   ├── cabinet/
│   ├── independent-agencies/
│   └── corporations/
├── legislative/
│   ├── senate/
│   ├── house/
│   ├── support-services/
│   └── committees/
├── judicial/
│   ├── supreme-court/
│   ├── courts-of-appeals/
│   ├── district-courts/
│   └── specialized-courts/
└── us-code/
```

**Why Keep Legacy Pages:**
- Reference for implementing CRUD functionality in new pages
- Some features (search, imports) may be reusable
- Avoid breaking existing workflows during transition

**When to Delete:**
Delete legacy factbase pages when ALL of these conditions are met:
1. All new admin KB pages have full CRUD functionality
2. All data import features are migrated (GOVMAN, Congress.gov search, etc.)
3. All tests updated to use new routes
4. Product Owner approval

**Related:**
- UI-6 Epic (Public KB Executive Branch)
- AdminSidebar.tsx (marked "Legacy" in menu label)

---

### TD-002: Missing application-prod.yml — production Spring profile has no config file

**Status:** PENDING
**Created:** 2026-07-04
**Priority:** HIGH (silently affects secret/config injection in production)
**Owner:** TBD

**Description:**
`deploy/production/docker-compose.yml` sets `SPRING_PROFILES_ACTIVE=prod`, but no `application-prod.yml` exists — only `application.yml` (base) and `application-dev.yml` (dev profile). Spring Boot silently skips a profile-specific file that doesn't exist rather than erroring, so in production today, none of `application-dev.yml`'s settings load at all. This includes `congress.api.key: ${CONGRESS_API_KEY:}` — meaning `CONGRESS_API_KEY` injection (already wired into `docker-compose.yml`'s environment section) currently has no effect in production, silently falling back to whatever hardcoded default exists in `CongressApiConfig`. Discovered while implementing Story ES-1.3 (Entity Extraction Integration), which needed to add a new `reasoning-service` config section and had to route around this gap by placing it in base `application.yml` instead of a (nonexistent) prod profile file.

**Location:**
- `backend/src/main/resources/` — missing `application-prod.yml`
- `backend/src/main/java/org/newsanalyzer/config/CongressApiConfig.java` — silently falls back to its Java-level default `baseUrl`, and gets no API key at all, in production
- `deploy/production/docker-compose.yml` — sets `SPRING_PROFILES_ACTIVE=prod` and passes `CONGRESS_API_KEY`, `REASONING_SERVICE_URL`, etc. as environment variables that currently have nowhere to land

**Resolution Plan:**
Create `application-prod.yml` and migrate every dev-profile setting that's actually meant to apply in production (API keys, external service URLs, etc.) into it, auditing each `application-dev.yml` entry to decide whether it's dev-only (keep in dev) or should be duplicated/moved to prod.

**When to Address:**
Before any production feature relies on an environment-variable-injected secret or URL that isn't already independently defaulted at the Java config-class level. Should be prioritized soon given it affects real, already-wired secrets, not just a future concern.

---

## Resolved Items

*(None yet)*

---

## How to Add New Items

Use this template:
```markdown
### TD-XXX: [Short Title]

**Status:** PENDING | IN_PROGRESS | RESOLVED
**Created:** YYYY-MM-DD
**Priority:** LOW | MEDIUM | HIGH | CRITICAL
**Owner:** [Name or TBD]

**Description:**
[What is the technical debt and why does it exist?]

**Location:**
[File paths or areas of codebase affected]

**Resolution Plan:**
[Steps to resolve this debt]

**When to Address:**
[Conditions that trigger cleanup]
```
