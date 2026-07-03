# Story ES-1.1: Article Persistence Model

## Status

Ready for Review

## Story

**As a** backend developer,
**I want** a persisted Article model and database schema,
**so that** ingested article content and metadata can be stored and referenced by other records.

## Acceptance Criteria

1. An `Article` JPA entity exists with fields: id (UUID), source/outlet name, url, publicationDate, rawText, ingestedAt, following existing `Entity` model conventions.
2. A Flyway migration creates the `articles` table additively, following the naming and indexing conventions in `coding-standards.md`.
3. `Entity` gains a nullable `article_id` FK column via an additive migration, with `@JsonIgnore` on the lazy relation and the FK id exposed directly for API consumers, per the `GovernmentOrganization` pattern.
4. An `ArticleRepository` (Spring Data JPA) exists, mirroring `EntityRepository`'s structure, providing the persistence access used by Story ES-1.2 onward.
5. Unit tests (`ArticleTest`) verify entity mapping and constraints.

### Integration Verification

- **IV1:** Existing `Entity` records with a null `article_id` continue to load, serialize, and pass through existing `EntityController`/`EntityService`/`EntityRepository` tests unchanged.
- **IV2:** Existing Flyway migration history is unaffected — the new migration runs cleanly on top of the current schema version with no destructive statements.
- **IV3:** No measurable startup or query performance regression on existing `/api/entities` endpoints.

## Tasks / Subtasks

- [x] Task 1: Determine actual next Flyway migration version (AC: 2)
  - [x] Inspect `backend/src/main/resources/db/migration/` directly for the real latest `V{n}` — do not assume `V4` is current; `source-tree.md` only documents through `V4` and the repo has moved on since. **Actual latest was V44**; this story uses V45/V46.
- [x] Task 2: Create `Article` JPA entity (AC: 1)
  - [x] Add `Article.java` to `model/` with `id` (UUID), `sourceName`, `url`, `publicationDate`, `rawText`, `ingestedAt`
  - [x] Add `extractionStatus` and `biasDetectionStatus` fields now, even though they're only populated by later stories (ES-1.3/ES-1.4) — the columns need to exist from this story so those stories don't require their own schema migration
  - [x] Add nullable `reliabilityScore` (FLOAT) field, always `null` at MVP (FR7)
- [x] Task 3: Create `ArticleStatus` enum + JPA converter (AC: 1)
  - [x] `ArticleStatus.java` enum (`PENDING` | `SUCCESS` | `FAILED`)
  - [x] `ArticleStatusConverter.java`, mirroring the existing `OrganizationTypeConverter` pattern
- [x] Task 4: Write `V45__create_evidence_articles.sql` (AC: 2) — renamed from `articles` to `evidence_articles` after discovering a pre-existing, unused table by that name (see Completion Notes)
  - [x] `evidence_articles` table, snake_case columns per `coding-standards.md`
  - [x] `idx_evidence_articles_source_name`, `idx_evidence_articles_publication_date` indexes
- [x] Task 5: Write `V46__add_entity_article_link.sql` (AC: 3)
  - [x] Nullable `article_id UUID` column on `entities`
  - [x] `fk_entities_article` FK constraint + `idx_entities_article_id` index
- [x] Task 6: Add the `Entity` → `Article` relation (AC: 3)
  - [x] `@ManyToOne(fetch = FetchType.LAZY)` `@JoinColumn(name = "article_id", insertable = false, updatable = false)` `@JsonIgnore` on the entity-side relation
  - [x] Expose `articleId` directly as a plain field for API/DTO consumers, per the `GovernmentOrganization.parent`/`parentId` self-referential pattern (verified against actual code — `Entity.governmentOrganization` itself does not use this pattern; decision confirmed with user)
- [x] Task 7: Create `ArticleRepository` (AC: 4)
  - [x] `ArticleRepository.java extends JpaRepository<Article, UUID>`, mirroring `EntityRepository`
- [x] Task 8: Write unit tests (AC: 5)
  - [x] `ArticleTest.java` — entity mapping and constraint verification (8 tests)
  - [x] Confirm existing `EntityTest` still passes with the new nullable field present — required fixing `testAllArgsConstructor`'s positional constructor call (2 new fields added by `@AllArgsConstructor`); only call site of its kind in the codebase
- [x] Task 9: Regression verification (IV1, IV2, IV3)
  - [x] Full clean `mvn compile` — BUILD SUCCESS, 195 source files
  - [x] Full `mvn test-compile` — BUILD SUCCESS, 56 test files
  - [x] `ArticleTest`, `EntityTest`, `EntityServiceTest`, `EntityControllerTest`, `EntityRepositoryTest` — **81/81 pass, 0 failures, 0 errors**
  - [x] Migration applies cleanly with no destructive statements — confirmed via `EntityRepositoryTest`'s Testcontainers Postgres run (Flyway applies V45/V46 automatically on context startup)
  - [x] `/api/entities` response time — no dedicated benchmark run (no load-testing infra in this environment), but architecturally sound: new code shares no query logic with existing `/api/entities` endpoints, so no regression is expected

## Dev Notes

Pulled directly from `docs/prd/ES-1.md` and `docs/architecture/ES-1-ARCHITECT-HANDOFF.md` and `docs/architecture/coding-standards.md` — no invented details.

**Relevant Source Tree** (new files for this story only):
```
backend/src/main/java/org/newsanalyzer/
├── model/
│   ├── Article.java                    # NEW
│   ├── ArticleStatus.java              # NEW — enum (PENDING/SUCCESS/FAILED)
│   └── converter/
│       └── ArticleStatusConverter.java # NEW
├── repository/
│   └── ArticleRepository.java          # NEW
backend/src/main/resources/db/migration/
├── V45__create_evidence_articles.sql       # NEW
└── V46__add_entity_article_link.sql        # NEW
backend/src/test/java/org/newsanalyzer/model/
└── ArticleTest.java                    # NEW
```
`Entity.java` (existing) is **modified**, not replaced — add the `articleId` field and lazy `article` relation only.

**Existing Patterns to Mirror (verified against actual code, not assumed):**
- **JPA converter pattern:** `OrganizationTypeConverter`/`GovernmentBranchConverter` (`model/converter/`) are the existing precedent for enum-to-DB-column mapping — follow the same shape for `ArticleStatusConverter`.
- **Lazy relation + exposed FK pattern:** `GovernmentOrganization`'s `parentId` field is the documented precedent (`coding-standards.md`, "Hibernate Lazy Loading & JSON Serialization" section) — `@JsonIgnore` on the lazy `@ManyToOne` relation, plain FK id field exposed directly for API consumers. Apply this exact shape to `Entity.articleId`/`Entity.article`.
- **Two-migration precedent:** `V3__create_government_organizations.sql` followed by `V4__add_entity_gov_org_link.sql` is the historical, load-bearing precedent for "create new table, then link `Entity` to it in a separate migration" — this story's two migrations follow that exact sequence.

**Key Constraints:**
- Migrations must be additive-only — no destructive statements, no narrowing of existing columns (CR2)
- `entity.article_id` must be nullable, defaulting to `NULL` for all existing rows — no backfill (CR1)
- No new dependencies — everything needed (JPA, Flyway, Hibernate) is already in the stack
- DB naming: snake_case tables/columns, `idx_{table}_{columns}` indexes, `fk_{table}_{columns}` constraints (`coding-standards.md`, Database Standards)
- `extractionStatus`/`biasDetectionStatus` columns are created now but populated by later stories (ES-1.3, ES-1.4) — this story only needs them to exist with a sensible default (`PENDING`)

**Previous Story Context:** None — this is Story ES-1.1, the first story in Epic ES-1. There is no prior story to carry context from.

### Testing

- **Test file location:** `backend/src/test/java/org/newsanalyzer/model/ArticleTest.java`
- **Test standards:** JUnit 5 + Mockito, Given/When/Then structure, per `coding-standards.md`'s Testing Standards section
- **Frameworks/patterns:** Plain JUnit for entity mapping/constraint tests (no `@SpringBootTest` needed for this story — no service/controller logic yet)
- **Specific requirement for this story:** Must include running the full existing suite as a regression check — `EntityTest`, `EntityRepositoryTest`, `EntityServiceTest`, `EntityControllerTest` must all pass unchanged (this is IV1, not optional)

## Change Log

| Date | Version | Description | Author |
|---|---|---|---|
| 2026-07-03 | 0.1 | Initial draft, created from `docs/prd/ES-1.md` and `docs/architecture/ES-1-ARCHITECT-HANDOFF.md` | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-03 | 0.2 | Moved to `docs/stories/ES-1/` and retitled from "Story 1.1" to "Story ES-1.1" to match project epic-ID convention | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-03 | 0.3 | Status: Draft → Approved — cleared for dev agent pickup | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-03 | 0.4 | Tasks 1-8 implemented and unit-tested; Task 9 in progress pending Docker for Testcontainers-based EntityRepositoryTest | James (Dev) |
| 2026-07-03 | 0.5 | Task 9 complete — discovered and resolved a table-naming collision with a dead V1 table (`articles` → `evidence_articles`); full regression 81/81 passing | James (Dev) |
| 2026-07-03 | 0.6 | Story DoD checklist run; Status: Approved → Ready for Review. One follow-up flagged (non-blocking): architecture doc / PRD need updating to reflect the `evidence_articles` table rename before ES-1.2 is drafted | James (Dev) |

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (James, Dev agent persona)

### Debug Log References

- Clean compile: `mvn -o clean compile` — BUILD SUCCESS, 195 source files
- Test compile: `mvn -o test-compile` — BUILD SUCCESS, 56 test files
- First full regression attempt (after Docker became available): `EntityRepositoryTest` FAILED — `FlywaySqlScriptException`, SQL State 42P07, "relation \"articles\" already exists"
- Root cause investigated and fixed (see Completion Notes) — table renamed to `evidence_articles`
- Final full regression run: `mvn -o test -Dtest=ArticleTest,EntityTest,EntityServiceTest,EntityControllerTest,EntityRepositoryTest` — **81/81 pass, 0 failures, 0 errors**

### Completion Notes List

- **Migration numbering corrected against reality, not docs**: `source-tree.md` documents migrations only through `V4`; actual latest in the repo is `V44`. Used `V45`/`V46` per Task 1's explicit instruction to verify rather than assume.
- **FK pattern decision required user input**: the story's Dev Notes claimed `Entity.governmentOrganization` already uses the `@JsonIgnore` + exposed-ID pattern ("the GovernmentOrganization pattern"). Verified against the actual code and found this is false — that pattern belongs to `GovernmentOrganization`'s own self-referential `parent`/`parentId` fields, not to `Entity`'s relation to it (which is a plain lazy `@ManyToOne` with no `@JsonIgnore`, relying on `JacksonConfig`'s globally-registered `Hibernate6Module`). Presented both options to the user; chose the dedicated-field pattern (matches architecture doc's `EntityDTO.articleId` spec, better ID-access performance) despite it diverging from `Entity.governmentOrganization`'s current implementation.
- **One existing test required modification, not just new tests**: `EntityTest.testAllArgsConstructor` uses `Entity`'s Lombok `@AllArgsConstructor` positionally; adding `articleId`/`article` fields shifted every subsequent constructor argument. Searched the full codebase for other positional-constructor call sites — found none besides this one test method — and updated it with two additional `null` arguments at the correct position.
- **Significant discovery: a table named `articles` already existed, from `V1__initial_schema.sql`.** Migration V45 failed against a real Postgres (Testcontainers) with "relation already exists" — the PRD and architecture doc's foundational claim ("no article persistence exists in production") turned out to be incomplete; V1 created a full `articles` table (url/title/content/author/analysis_status) that was **never wired to any JPA entity or application code** (confirmed via full-codebase search — zero references). Presented three options to the user (rename ours / drop V1's dead table / repurpose V1's table); repurposing was explicitly ruled out since V1's `url UNIQUE NOT NULL` constraint contradicts this story's deliberate no-dedup-at-MVP decision (PRD Story 1.2 AC5), and its single `analysis_status` field conflicts with the required `extraction_status`/`bias_detection_status` split (NFR3). User chose renaming — table is now `evidence_articles` (also a better name, matching the "Evidence Store" framing). Migration file renamed to `V45__create_evidence_articles.sql` before ever being applied anywhere, so no Flyway checksum history was at risk.
- Reused `EntityService` rather than calling `EntityRepository` directly was specified by the architecture doc but not yet needed — no `ArticleService` exists yet (that's Story ES-1.3). This story only touches the model/repository/migration layer.

### File List

**New files:**
- `backend/src/main/java/org/newsanalyzer/model/Article.java`
- `backend/src/main/java/org/newsanalyzer/model/ArticleStatus.java`
- `backend/src/main/java/org/newsanalyzer/model/converter/ArticleStatusConverter.java`
- `backend/src/main/java/org/newsanalyzer/repository/ArticleRepository.java`
- `backend/src/main/resources/db/migration/V45__create_evidence_articles.sql`
- `backend/src/main/resources/db/migration/V46__add_entity_article_link.sql`
- `backend/src/test/java/org/newsanalyzer/model/ArticleTest.java`

**Modified files:**
- `backend/src/main/java/org/newsanalyzer/model/Entity.java` — added `articleId`/`article` fields
- `backend/src/test/java/org/newsanalyzer/model/EntityTest.java` — updated `testAllArgsConstructor` for the new constructor signature

## QA Results

*(To be populated by QA agent review of the completed story implementation.)*
