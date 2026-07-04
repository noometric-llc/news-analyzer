# Epic ES-1: Evidence Store Foundation

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | ES-1 |
| **Epic Name** | Evidence Store Foundation |
| **Epic Type** | Infrastructure / Data Platform |
| **Priority** | TBD — not yet formally prioritized against other tracks |
| **Status** | Draft |
| **Created** | 2026-07-02 |
| **Owner** | Sarah (PO) |

## Executive Summary

Establish a persisted, source-attributed Evidence Store in NewsAnalyzer — article storage, entity-to-article linkage, and real bias/fallacy signal from the existing (but previously production-unused) reasoning-service `/eval/bias/detect` endpoint. This is the technical foundation for the political-analysis-agent vision explored in `docs/analysis/ES-1-brainstorming-session-results.md`: nothing in NewsAnalyzer today can ground a claim in "here's what was reported, by whom, and how reliable that source is," because article text is currently processed and discarded rather than persisted.

## Business Value

### Why This Epic Matters

1. **Unblocks the agent vision** — every downstream trust feature (audit trail, reliability scoring, the "challenge" dialogue) from the originating brainstorm assumes persisted, scored evidence exists. Nothing else can be built until this does.
2. **Upgrades a placeholder into real signal** — research during PRD drafting found `/eval/bias/detect` already exists as a stable, documented contract endpoint, unused in production. This epic makes NewsAnalyzer its first production caller, rather than shipping a null placeholder as originally scoped in the project brief.
3. **Closes a real testing gap** — the entity-extraction pipeline has only ever been validated against synthetic data (`SyntheticArticle`); this epic enables validation against real ingested content.
4. **Establishes a reusable pattern** — `ReasoningServiceClient` becomes the first Java-side reasoning-service caller and the reference implementation for any future one.
5. **Strategic optionality** — per `docs/analysis/ES-1-project-brief.md`, this may also become the technical foundation for NewsAnalyzer as a public case study for Noometric's LLM persona-profiling business direction, though that business decision is explicitly out of this epic's scope (see `docs/analysis/ES-1-project-brief.md` Priority #3).

### Success Metrics

*(Carried from `docs/analysis/ES-1-project-brief.md`'s KPIs — these predate the PRD and remain the epic's north star.)*

| Metric | Target | Measurement |
|--------|--------|-------------|
| Ingestion success rate | 100% for well-formed input | Article persistence success/failure tracking |
| Source-linkage coverage | 100% of newly extracted entities | % of `Entity` records with valid `article_id` |
| Reliability score field coverage | 100% (placeholder-null acceptable) | Every source has *a* score field populated, never absent |
| Zero silent blending | 100% | Grounded-query responses always cite evidence or explicitly say none was found |

## Scope

*(Carried from `docs/analysis/ES-1-project-brief.md`'s MVP Scope, refined during PRD/architecture drafting.)*

### In Scope

- Persisted `Article` model and schema (source, url, publicationDate, rawText, ingestion timestamps)
- API-driven ingestion (manual/API submission — no crawler or scheduled feed)
- Entity-to-article linkage via a real foreign key, replacing the freeform `source` string for newly extracted entities
- Real bias/fallacy annotation via `/eval/bias/detect`, stored per-article (raw signal, not aggregated)
- Grounded-query interface with zero silent blending between evidence-backed and ungrounded content
- Eval harness validation against real ingested articles, alongside the existing synthetic-data path

### Out of Scope

- Reliability-scoring *methodology* (belongs in `noometric-intelligence`, per the CLAUDE.md IP boundary)
- Cross-article/cross-source reliability aggregation (deferred — sampling-representativeness and correlated-bias risk, per the originating brainstorm)
- Any conversational agent, chat interface, or "challenge" dialogue
- Automated large-scale ingestion (RSS feeds, scrapers)
- Any citizen-facing UI
- Circuit breaker / automated retry-triggering for failed extraction or bias-detection (deferred to Phase 2, per architecture review)

## Architecture

### Technology Stack

*(No new technology — see `docs/architecture/ES-1-ARCHITECT-HANDOFF.md` Tech Stack section for full rationale.)*

| Component | Technology | Notes |
|-----------|------------|-------|
| Backend | Java 17 / Spring Boot 3.2.2 | Existing stack, no version change |
| HTTP Client | `RestTemplate` via `RestTemplateBuilder` | New `ReasoningServiceClient` — first Java-side reasoning-service caller, mirrors the verified `CongressApiClient` pattern |
| Database | PostgreSQL 15 + Flyway | Two new tables (`evidence_articles`, `article_bias_annotations`), one additive FK on `entities` |
| Reasoning Service | `noometric-intelligence` (private) | Consumes existing `/entities/extract` and `/eval/bias/detect` — no contract changes |

### Project Structure

*(See `docs/architecture/ES-1-ARCHITECT-HANDOFF.md` Source Tree for full detail — no new top-level packages, everything lands in the existing `model/`, `repository/`, `service/`, `controller/`, `dto/` structure.)*

## Stories

### Story Summary

| ID | Story | Status |
|----|-------|--------|
| ES-1.1 | [Article Persistence Model](ES-1.1.article-persistence-model.md) | Ready for Done |
| ES-1.2 | [Article Ingestion API (Persistence Only)](ES-1.2.article-ingestion-api.md) | Ready for Done |
| ES-1.3 | Entity Extraction Integration | Not yet drafted |
| ES-1.4 | Bias/Fallacy Annotation Integration | Not yet drafted |
| ES-1.5 | Grounded-Query Interface | Not yet drafted |
| ES-1.6 | Eval Harness Real-Article Integration | Not yet drafted |

*(Full acceptance criteria for ES-1.2 through ES-1.6 are defined in `docs/prd/ES-1.md`; they will be drafted as individual story files following the ES-1.1 pattern before implementation.)*

### Dependency Graph

```
ES-1.1 (Schema)
    │
    ▼
ES-1.2 (Ingestion API)
    │
    ├──────────────┐
    ▼              ▼
ES-1.3         ES-1.4
(Extraction)   (Bias Detection)
    │              │
    └──────┬───────┘
           ▼
        ES-1.5
     (Grounded Query)
           │
           ▼
        ES-1.6
   (Eval Harness Integration)
```

ES-1.3 and ES-1.4 are logically independent of each other (both depend only on ES-1.2) but are sequenced serially in the PRD specifically so bias-detection's failure mode — the first production use of an LLM-backed, previously-unused endpoint — can't take down extraction if something goes wrong.

## Acceptance Criteria (Epic Level)

1. **Schema Foundation:** `evidence_articles` and `article_bias_annotations` tables exist; `entities.article_id` is a nullable, additive FK — verified via Flyway migration review and IV1-style regression tests.
2. **Ingestion Pipeline:** An article submitted via API is persisted, has entities extracted and linked, and has bias annotations attached — end-to-end, verified by at least one real (non-synthetic) test article (per ES-1.6).
3. **Zero Silent Blending:** The grounded-query interface always distinguishes evidence-backed results from "no grounded evidence found" — never an ambiguous response.
4. **Zero Regression:** The full existing `mvn test` suite passes unchanged; `/api/entities` and `/api/government-orgs` behavior is unaffected.
5. **Contract Documentation Updated:** `docs/api/reasoning-service-contract.md` reflects NewsAnalyzer's backend as a new production caller of `/eval/bias/detect`.

## Risks & Mitigations

*(Carried from `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`'s Checklist Results Report — top risks, resolved status noted.)*

| Risk | Impact | Status | Mitigation |
|------|--------|--------|------------|
| `ArticleService` transaction boundary holding a DB connection across ~90s of external HTTP calls | High | **Resolved** | Explicit guidance: persistence steps and external calls must not share a `@Transactional` method (architecture doc, Component Architecture) |
| Unauthenticated, LLM-cost-bearing `/api/articles` endpoint | High | **Resolved** | Basic rate limiting added alongside the request-size cap (architecture doc, Security Integration) |
| No retry-triggering mechanism despite "retriable" being asserted | Medium | Open, deferred to Phase 2 | Explicitly scoped out rather than silently assumed handled |
| No circuit breaker for reasoning-service calls | Medium | Open, deferred to Phase 2 | Tolerable at MVP's manual/low-volume scope |
| `/eval/bias/detect` rate limits/quotas unconfirmed | Medium | Open — action item | Must be confirmed with `noometric-intelligence` before ES-1.4 implementation (PO validation finding) |
| Article full-text copyright/retention posture unresolved | Low-Medium | Open — needs legal input | Flagged for `business-attorney` per CLAUDE.md's Noometric agent table, before real (non-test) content ingestion scales |

## Definition of Done

- [ ] All 6 stories (ES-1.1–ES-1.6) drafted, implemented, and merged
- [ ] Full existing test suite passes with zero regressions
- [ ] At least one real ingested article demonstrates the full pipeline end-to-end
- [ ] `reasoning-service-contract.md` updated to reflect the new production caller
- [ ] Reasoning-service rate-limit/quota question resolved before ES-1.4 merges
- [ ] Code reviewed and follows `coding-standards.md` conventions throughout

## Related Documentation

- [Project Brief](../../analysis/ES-1-project-brief.md) — original business framing and priority sequencing
- [Brainstorming Session Results](../../analysis/ES-1-brainstorming-session-results.md) — originating ideation session
- [PRD](../../prd/ES-1.md) — full functional/non-functional requirements
- [Architecture](../../architecture/ES-1-ARCHITECT-HANDOFF.md) — technical design, component architecture, checklist validation
- [Reasoning Service Contract](../../api/reasoning-service-contract.md) — the API boundary this epic consumes
- [Factbase Expansion Architect Handoff](../../architecture/FACTBASE_EXPANSION_ARCHITECT_HANDOFF.md) — the precedent this epic's schema/migration approach mirrors

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-07-03 | 1.0 | Initial epic creation, consolidating brief/PRD/architecture into the project's standard epic-overview format | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-03 | 1.1 | ES-1.1 status updated to Ready for Review; table rename reconciled (`articles` → `evidence_articles`) after Story ES-1.1 implementation discovered a pre-existing, unused table of that name from `V1__initial_schema.sql` | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 1.2 | ES-1.1 completed — QA gate PASS (quality score 100), status updated to Ready for Done. ES-1.2 drafted and linked. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 1.3 | ES-1.2 completed — QA gate PASS (quality score 95), status updated to Ready for Done. Two of six ES-1 stories now done. | Sarah (PO) / Steve Kosuth-Wood |

## Architectural Review Summary

**Review Date:** 2026-07-02
**Reviewer:** Winston (Architect)
**Verdict:** APPROVED (architecture document), Medium-High readiness

### Strengths Identified

- Schema design directly mirrors the verified, historical `V3`/`V4` two-migration precedent from the Factbase expansion
- `ReasoningServiceClient` design verified against actual codebase behavior (no existing Java-side reasoning-service caller existed — confirmed by direct code search, not assumed) rather than the aspirational `@Retryable` pattern documented but never implemented elsewhere
- Transaction-boundary risk (long-held DB connection across external HTTP calls) identified and resolved during checklist review, not left as a latent bug
- Unauthenticated, cost-bearing endpoint risk identified and mitigated with rate limiting during the same review

### Recommendations (Non-Blocking)

1. Add a sequence diagram for the ingest→extract→bias-detect→persist flow (only a static component diagram exists currently)
2. Consider ADR entries for the two significant new decisions (Java-side reasoning-service client shape; monolith-first), matching this project's existing `docs/architecture/adr/` practice
3. Define alerting thresholds for new failure-rate metrics before wider real-world use

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | Sarah (PO) | 2026-07-03 | Drafted |
| Architect | Winston (Architect) | 2026-07-02 | Architecture APPROVED |
| PO Master Checklist | Sarah (PO) | 2026-07-02 | APPROVED (post should-fix resolution) |
| Tech Lead | _TBD_ | _Pending_ | _Pending_ |

---

*End of Epic Document*
