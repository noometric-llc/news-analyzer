# Story ES-1.1: Article Persistence Model

## Status

Draft

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

- [ ] Task 1: Determine actual next Flyway migration version (AC: 2)
  - [ ] Inspect `backend/src/main/resources/db/migration/` directly for the real latest `V{n}` — do not assume `V4` is current; `source-tree.md` only documents through `V4` and the repo has moved on since
- [ ] Task 2: Create `Article` JPA entity (AC: 1)
  - [ ] Add `Article.java` to `model/` with `id` (UUID), `sourceName`, `url`, `publicationDate`, `rawText`, `ingestedAt`
  - [ ] Add `extractionStatus` and `biasDetectionStatus` fields now, even though they're only populated by later stories (ES-1.3/ES-1.4) — the columns need to exist from this story so those stories don't require their own schema migration
  - [ ] Add nullable `reliabilityScore` (FLOAT) field, always `null` at MVP (FR7)
- [ ] Task 3: Create `ArticleStatus` enum + JPA converter (AC: 1)
  - [ ] `ArticleStatus.java` enum (`PENDING` | `SUCCESS` | `FAILED`)
  - [ ] `ArticleStatusConverter.java`, mirroring the existing `OrganizationTypeConverter` pattern
- [ ] Task 4: Write `V{next}__create_articles.sql` (AC: 2)
  - [ ] `articles` table, snake_case columns per `coding-standards.md`
  - [ ] `idx_articles_source_name`, `idx_articles_publication_date` indexes
- [ ] Task 5: Write `V{next+1}__add_entity_article_link.sql` (AC: 3)
  - [ ] Nullable `article_id UUID` column on `entities`
  - [ ] `fk_entities_article` FK constraint + `idx_entities_article_id` index
- [ ] Task 6: Add the `Entity` → `Article` relation (AC: 3)
  - [ ] `@ManyToOne(fetch = FetchType.LAZY)` `@JoinColumn(name = "article_id", insertable = false, updatable = false)` `@JsonIgnore` on the entity-side relation
  - [ ] Expose `articleId` directly as a plain field for API/DTO consumers, per the existing `GovernmentOrganization`/`parentId` pattern documented in `coding-standards.md`
- [ ] Task 7: Create `ArticleRepository` (AC: 4)
  - [ ] `ArticleRepository.java extends JpaRepository<Article, UUID>`, mirroring `EntityRepository`
- [ ] Task 8: Write unit tests (AC: 5)
  - [ ] `ArticleTest.java` — entity mapping and constraint verification
  - [ ] Confirm existing `EntityTest` still passes with the new nullable field present
- [ ] Task 9: Regression verification (IV1, IV2, IV3)
  - [ ] Run the full existing `mvn test` suite — confirm `EntityControllerTest`/`EntityServiceTest`/`EntityRepositoryTest` pass unchanged
  - [ ] Confirm the migration applies cleanly with no destructive statements
  - [ ] Spot-check `/api/entities` response time before/after for regression

## Dev Notes

Pulled directly from `docs/prd/ES-1.md` and `docs/architecture-evidence-store-foundation.md` and `docs/architecture/coding-standards.md` — no invented details.

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
├── V{next}__create_articles.sql            # NEW
└── V{next+1}__add_entity_article_link.sql  # NEW
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
| 2026-07-03 | 0.1 | Initial draft, created from `docs/prd/ES-1.md` and `docs/architecture-evidence-store-foundation.md` | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-03 | 0.2 | Moved to `docs/stories/ES-1/` and retitled from "Story 1.1" to "Story ES-1.1" to match project epic-ID convention | Sarah (PO) / Steve Kosuth-Wood |

## Dev Agent Record

*(To be populated by the development agent during implementation.)*

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## QA Results

*(To be populated by QA agent review of the completed story implementation.)*
