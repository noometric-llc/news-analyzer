# Story INFRA-1.2: Define and Document the Reasoning Service API Contract

## Status

Complete

## Story

**As** a developer maintaining both the public `news-analyzer` and private `noometric-intelligence` repos,
**I want** a formal, documented API contract for the reasoning service,
**so that** the two repos have a stable interface definition that either side can reference independently of the other's internals.

## Context

Currently, NewsAnalyzer calls the reasoning service as an internal Docker service. The "contract" is implicit — nginx routes requests to it and the application just knows what URLs to call. When the service moves to `noometric-intelligence` and becomes an external HTTP API, that implicit contract needs to be made explicit.

This story produces an API contract document. It does not change any code — INFRA-1.3 does the implementation. The contract document will live in both repos (as a copy) so each repo has the agreed interface without needing to reference the other.

The reasoning service exposes 9 router groups:
- `/entities` — entity extraction
- `/reasoning` — OWL reasoning
- `/fallacies` — fallacy detection
- `/government-orgs` — government organization data
- `/eval/facts` — fact evaluation
- `/eval/articles` — article evaluation
- `/eval/batches` — batch evaluation
- `/eval/extract` — extraction evaluation
- `/eval/bias` — bias detection and ontology

The contract should document which of these endpoints NewsAnalyzer actually calls (the public surface) vs. which are internal to the reasoning service.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | All endpoints actually called by NewsAnalyzer frontend, backend, or eval harness are identified and documented |
| AC2 | For each endpoint: HTTP method, path, request schema, response schema, and purpose are documented |
| AC3 | Endpoints are classified as: **Public API** (called by NewsAnalyzer) vs. **Internal** (used only within reasoning service or eval runs) |
| AC4 | API contract document exists at `docs/api/reasoning-service-contract.md` in `news-analyzer` |
| AC5 | Same document is copied to `docs/api/reasoning-service-contract.md` in `noometric-intelligence` |
| AC6 | Authentication strategy is defined (even if the answer is "none for now — network-level only") |
| AC7 | Rate limiting and timeout expectations are noted |

## Tasks / Subtasks

- [x] Task 1: Inventory all reasoning service endpoint callers (AC1, AC3)
  - [x] Search `news-analyzer` backend (Java) for calls to reasoning service:
    - Look for HTTP client calls, `reasoning-service`, or port 8000 references
  - [x] Search `news-analyzer` frontend (Next.js) for direct reasoning service calls:
    - Check API route handlers in `frontend/src/app/api/`
  - [x] Search `news-analyzer` eval harness for reasoning service calls:
    - Check `eval/providers/` and `eval/scripts/`
  - [x] Search nginx config for routing rules:
    - `nginx.conf` or equivalent — which paths route to reasoning service
  - [x] Document the full list of callers and which endpoints they use

- [x] Task 2: Document each endpoint in the Public API surface (AC2)
  - [x] For each endpoint called by NewsAnalyzer, document:
    - HTTP method and path
    - Request body / query params (with field names and types)
    - Response body (with field names and types)
    - One-line purpose description
    - Which NewsAnalyzer component calls it
  - [x] Used source router files directly (entities.py, extraction.py, bias.py) as source of truth for schemas

- [x] Task 3: Classify internal-only endpoints (AC3)
  - [x] List endpoints that exist in the reasoning service but are NOT called by NewsAnalyzer directly
  - [x] These are "internal" — NewsAnalyzer does not need to know about them; they are implementation details of `noometric-intelligence`

- [x] Task 4: Define authentication and security approach (AC6)
  - [x] Decision: **Option B — API key in `X-Noometric-API-Key` header**
  - [x] Key stored as `NOOMETRIC_API_KEY` env var in both services
  - [x] Documented in contract with security upgrade note for production

- [x] Task 5: Note rate limiting and timeout expectations (AC7)
  - [x] Documented response times for all 3 public endpoints
  - [x] Noted LLM vs. fast endpoints
  - [x] Defined recommended timeout env vars for INFRA-1.3 implementation
  - [x] Noted retry recommendation for LLM endpoints

- [x] Task 6: Write the contract document (AC4)
  - [x] Created `docs/api/` directory in `news-analyzer`
  - [x] Created `docs/api/reasoning-service-contract.md`

- [x] Task 7: Copy contract to noometric-intelligence (AC5)
  - [x] Copied to `D:/VSCProjects/noometric-intelligence/docs/api/reasoning-service-contract.md`
  - [x] Committed and pushed to both repos

## Dev Notes

### Suggested Contract Document Structure

```markdown
# Noometric Intelligence — Reasoning Service API Contract

**Version:** 1.0
**Date:** [date]
**Maintained by:** Noometric LLC

This document defines the stable API surface between the public `news-analyzer`
application and the private `noometric-intelligence` reasoning service.

## Base URL

| Environment | Base URL |
|---|---|
| Local dev | `http://localhost:8000` |
| Production | TBD (set via REASONING_SERVICE_URL env var) |

## Authentication

[Document chosen approach from Task 4]

## Public API Endpoints

These endpoints are called by the news-analyzer application.

### [Endpoint Group Name]
#### `METHOD /path`
**Purpose:** [one-line description]
**Called by:** [NewsAnalyzer component]
**Request:**
  [schema]
**Response:**
  [schema]

---

## Internal Endpoints

These endpoints exist in the reasoning service but are not part of the
news-analyzer → noometric-intelligence interface. They are implementation
details of noometric-intelligence.

[list of internal endpoints]

## Timeout and Performance Notes

[Document from Task 5]
```

### Key Files to Search for Callers

```
news-analyzer/
├── backend/src/main/java/           # Spring Boot — look for RestTemplate/WebClient calls
├── frontend/src/app/api/            # Next.js API routes — look for fetch/axios to :8000
├── eval/providers/                  # Promptfoo providers — likely call /eval/* endpoints
├── eval/scripts/                    # Ad hoc scripts — may call reasoning service directly
└── nginx.conf (or similar)          # Routing rules — definitive list of proxied paths
```

### Known Endpoints from main.py

The reasoning service registers these routers (from `reasoning-service/app/main.py`):

| Prefix | Tag | Likely caller |
|--------|-----|---------------|
| `/entities` | entities | Backend Java service |
| `/reasoning` | reasoning | Backend Java service |
| `/fallacies` | fallacies | Backend Java service |
| `/government-orgs` | government-organizations | Backend Java service |
| `/eval/facts` | eval-facts | Eval harness |
| `/eval/articles` | eval-articles | Eval harness |
| `/eval/batches` | eval-batches | Eval harness |
| `/eval/extract` | eval-extraction | Eval harness (Promptfoo) |
| `/eval/bias` | eval-bias | Eval harness (Promptfoo) |

Verify which are actually called vs. stubbed or unused.

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### File List

| File | Action | Description |
|------|--------|-------------|
| `docs/api/reasoning-service-contract.md` | NEW | API contract document (in news-analyzer) |
| `noometric-intelligence/docs/api/reasoning-service-contract.md` | NEW | Same document (in noometric-intelligence) |

### Completion Notes

- Identified 3 public endpoints: `POST /entities/extract`, `POST /eval/extract/llm`, `POST /eval/bias/detect`
- Java backend does NOT call reasoning service — the relationship is reverse for batch eval (reasoning-service → backend)
- Frontend has no direct reasoning service calls
- Auth strategy: Option B selected — `X-Noometric-API-Key` header, `NOOMETRIC_API_KEY` env var
- Schemas sourced directly from router Python files (entities.py, extraction.py, bias.py)
- Timeouts: 30s for spaCy, 60s for LLM endpoints; retry recommended for LLM calls

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-04-14 | 1.0 | Initial story draft | Sarah (PO) |
| 2026-04-14 | 1.1 | Implementation complete | James (Dev) |
