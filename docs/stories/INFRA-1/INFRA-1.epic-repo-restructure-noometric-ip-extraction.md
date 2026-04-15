# Epic INFRA-1: Repository Restructure & Noometric IP Extraction

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | INFRA-1 |
| **Epic Name** | Repository Restructure & Noometric IP Extraction |
| **Track** | Infrastructure / IP Strategy |
| **Epic Type** | Structural Refactor — Repository Architecture & Licensing |
| **Priority** | HIGHEST (blocks all future Noometric proprietary development) |
| **Status** | IN PROGRESS |
| **Created** | 2026-04-14 |
| **Owner** | Sarah (PO) |
| **Depends On** | None |
| **Blocked By** | None |
| **Estimated Effort** | ~2–3 days (mix of admin, dev, and documentation work) |

## Executive Summary

NewsAnalyzer V2 was built under an MIT license when it was envisioned as a standalone open-source project. Noometric LLC is now the organizing entity, and the project contains IP — the cognitive bias ontology, evaluation methodology, and reasoning service — that should be protected as Noometric proprietary assets rather than MIT-licensed public infrastructure.

This epic restructures the repository architecture:

1. Establishes a `noometric-llc` GitHub organization as the canonical home for all Noometric code
2. Transfers `news-analyzer` (public, MIT) to the new org and retires the old `newsanalyzer-admin` org
3. Creates a new private `noometric-intelligence` repository for proprietary IP: the reasoning service, ontology, evaluation methodology, and bias datasets
4. Establishes a clean API boundary — NewsAnalyzer calls the reasoning service as an external HTTP API rather than running it as a co-located Docker container
5. Updates licensing and documentation to reflect the Noometric relationship

**The end state:** NewsAnalyzer is a MIT-licensed public showcase of evaluation techniques that calls a hosted Noometric API. The proprietary IP (what Noometric actually does) lives in a private repo, callable but not inspectable.

## Business Value

1. **IP protection** — Proprietary cognitive bias ontology and evaluation methodology are no longer publicly MIT-licensed
2. **Brand alignment** — All code lives under `noometric-llc` org, consistent with company identity
3. **Clean architecture** — API boundary between public showcase and private intelligence layer is explicit and maintainable
4. **Foundation for EVAL-4+** — Future Noometric IP development has a proper home from the start
5. **Consulting credibility** — Public repo demonstrates technique; the "how we really do it" lives in a private repo that clients can be given access to

## Context: Why This Matters Now

The reasoning service and ontology files (`cognitive-bias.ttl`, `cognitive-bias-shapes.ttl`, `newsanalyzer.ttl`) are currently in the public `news-analyzer` repo under MIT. While traffic has been negligible (5 unique visitors, 0 forks), every day of delay increases the public disclosure clock for any patent-worthy elements and deepens the architectural debt of having proprietary logic embedded in a public repo.

This is a prerequisite for all future Noometric development — do it now before EVAL-4 adds more proprietary work on top of the current structure.

## Stories

---

### INFRA-1.0: Establish noometric-llc GitHub Organization

**Goal:** Create the `noometric-llc` GitHub org, transfer `news-analyzer` to it, create the `noometric-intelligence` private repo, and retire the old `newsanalyzer-admin` org.

This is primarily manual GitHub admin work. No code changes.

**Effort:** ~2 hours

---

### INFRA-1.1: Migrate Reasoning Service to noometric-intelligence

**Goal:** Copy `reasoning-service/`, `docs/evaluation-methodology-bias.md`, and `eval/datasets/bias/` into the `noometric-intelligence` repo with a clean initial commit. Bootstrap BMAD for future story tracking.

**Effort:** ~4 hours

---

### INFRA-1.2: Define and Document the Reasoning Service API Contract

**Goal:** Document all API endpoints the NewsAnalyzer application calls on the reasoning service. Produce a formal API contract (OpenAPI spec or structured markdown) that becomes the stable interface between the two repos.

**Effort:** ~4 hours

---

### INFRA-1.3: Refactor NewsAnalyzer to Call External Reasoning API

**Goal:** Remove `reasoning-service/` from NewsAnalyzer, add `REASONING_SERVICE_URL` environment variable, update `docker-compose.yml` and nginx configuration, and verify the application works against an external reasoning service URL.

**Effort:** ~4 hours

---

### INFRA-1.4: Update NewsAnalyzer Licensing and Documentation

**Goal:** Update the LICENSE copyright holder, update README to reflect the Noometric relationship, and sanitize any documentation that contains proprietary methodology details.

**Effort:** ~2 hours

---

## Sizing Summary

| Story | Description | Effort | Type |
|-------|-------------|--------|------|
| INFRA-1.0 | Establish noometric-llc GitHub org | ~2 hrs | Manual admin |
| INFRA-1.1 | Migrate reasoning-service to noometric-intelligence | ~4 hrs | Dev + setup |
| INFRA-1.2 | Define reasoning service API contract | ~4 hrs | Documentation |
| INFRA-1.3 | Refactor NewsAnalyzer to call external API | ~4 hrs | Dev |
| INFRA-1.4 | Update licensing and documentation | ~2 hrs | Documentation |
| **Total** | | **~16 hrs** | |

**Dependency chain:** INFRA-1.0 → INFRA-1.1 → INFRA-1.2 → INFRA-1.3 → INFRA-1.4

## Follow-On Epic

**INFRA-2 — Deploy noometric-intelligence Reasoning Service** (not scoped here)

The live demo capability requires hosting the reasoning service somewhere publicly accessible so the deployed NewsAnalyzer can call it. This includes:
- Selecting a hosting platform (Fly.io, Render, Railway, or VPS)
- CI/CD from `noometric-intelligence` to that host
- Pointing the production NewsAnalyzer deployment at the hosted URL
- Environment-specific configuration (dev vs. staging vs. prod)

## Definition of Done

- [x] `noometric-llc` GitHub org exists and is the owner of both repos
- [x] `news-analyzer` repo is under `noometric-llc`
- [x] `noometric-intelligence` private repo exists under `noometric-llc`
- [x] All reasoning service code, ontology, methodology, and bias datasets are in `noometric-intelligence`
- [x] `reasoning-service/` directory is removed from `news-analyzer`
- [x] NewsAnalyzer application calls reasoning service via `REASONING_SERVICE_URL` env var
- [ ] Local development still works (with reasoning service running locally from `noometric-intelligence`)
- [ ] LICENSE in `news-analyzer` updated to reflect Noometric LLC
- [x] README updated to reflect new architecture and Noometric relationship
- [ ] No regressions to NewsAnalyzer application functionality
- [ ] `newsanalyzer-admin` org archived or deleted
