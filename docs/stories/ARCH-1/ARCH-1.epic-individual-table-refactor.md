# Epic ARCH-1: Individual Table Refactor

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | ARCH-1 |
| **Epic Name** | Individual Table Refactor |
| **Epic Type** | Architecture / Data Model |
| **Priority** | HIGH (Foundational) |
| **Status** | In Progress (8/9 stories Done, ARCH-1.9 remaining) |
| **Created** | 2026-01-08 |
| **Owner** | Sarah (PO) |
| **Depends On** | None (foundational change) |
| **Blocks** | KB-2 (Presidential Administrations) |
| **Triggered By** | Data model review: Person table is Congress-centric but serves as general individual reference |

## Executive Summary

Refactor the data model to establish a clean separation between **universal individual data** (name, birth date, biographical info) and **role-specific data** (Congressional membership, judicial appointments, etc.). This creates a true "single point of reference" for any person in the system, regardless of their roles.

### Current State

The `persons` table currently serves dual purposes:

```
┌─────────────────────────────────────────────────────────────────┐
│                         persons                                  │
├─────────────────────────────────────────────────────────────────┤
│ Core Fields (General):                                          │
│   id, first_name, last_name, birth_date, death_date,           │
│   birth_place, image_url, external_ids, social_media            │
│                                                                 │
│ Congress-Specific Fields:                                       │
│   bioguide_id, chamber, state, congress_last_sync               │
│                                                                 │
│ Mixed Tracking:                                                 │
│   data_source (defaults to CONGRESS_GOV)                        │
└─────────────────────────────────────────────────────────────────┘
          ▲
          │ person_id
          │
    ┌─────┴─────┬──────────────────┬────────────────────┐
    │           │                  │                    │
Presidency  PositionHolding  CommitteeMembership   (Judges via
                                                  PositionHolding)
```

**Problems:**
1. Table documentation says "master data for Congressional members"
2. Default `dataSource` is `CONGRESS_GOV` even for presidents
3. Congress-specific fields are nullable noise for non-Congress persons
4. Conceptual confusion about table's purpose
5. No clean extension point for future person types (CEOs, journalists, etc.)

### Target State

```
┌──────────────────────────────────────────────────────────────────┐
│                       individuals                                 │
│ (Master table for all persons in the system)                     │
├──────────────────────────────────────────────────────────────────┤
│ id (UUID, PK)                                                    │
│ first_name, last_name, middle_name, suffix                       │
│ birth_date, death_date, birth_place                              │
│ gender, image_url                                                │
│ external_ids (JSONB), social_media (JSONB)                       │
│ created_at, updated_at                                           │
└──────────────────────────────────────────────────────────────────┘
          ▲
          │ individual_id (FK)
          │
    ┌─────┴─────────────────────────────────────────────────────┐
    │                                                           │
    ▼                                                           ▼
┌────────────────────────────────┐      ┌────────────────────────────┐
│     congressional_members       │      │      position_holdings     │
├────────────────────────────────┤      ├────────────────────────────┤
│ id (UUID, PK)                  │      │ individual_id (FK)         │
│ individual_id (FK, unique)     │      │ position_id (FK)           │
│ bioguide_id (unique)           │      │ presidency_id (FK)         │
│ chamber, state                 │      │ start_date, end_date       │
│ congress_last_sync             │      └────────────────────────────┘
│ enrichment_source/version      │
│ data_source                    │      ┌────────────────────────────┐
└────────────────────────────────┘      │       presidencies         │
                                        ├────────────────────────────┤
                                        │ individual_id (FK)         │
                                        │ number, party, dates       │
                                        └────────────────────────────┘
```

## Business Value

### Why This Epic Matters

1. **Single Source of Truth** - One record per real-world person (Donald Trump = 1 individual, regardless of roles)
2. **Clean Separation of Concerns** - Biographical data vs role-specific data
3. **Extensibility** - Easy to add new person types (CEO, journalist, academic) without polluting core table
4. **Data Quality** - No nullable Congress-specific fields for non-Congress persons
5. **Semantic Clarity** - Clear intent: `individuals` = people, `congressional_members` = their Congressional role
6. **Foundation for KB-2** - Presidential Administrations epic needs clean individual references

### Success Metrics

| Metric | Target |
|--------|--------|
| All existing data migrated without loss | Yes |
| All existing tests pass post-migration | Yes |
| All existing API responses unchanged | Yes (backward compatible DTOs) |
| No duplicate individuals in database | Yes |
| Congressional member data in separate table | Yes |
| Individual table serves all person references | Yes |

## Data Model

### New Entity: Individual

```java
@Entity
@Table(name = "individuals",
    indexes = {
        @Index(name = "idx_individuals_name", columnList = "first_name, last_name"),
        @Index(name = "idx_individuals_birth_date", columnList = "birth_date")
    })
public class Individual {
    @Id
    private UUID id;

    // Core biographical
    private String firstName;
    private String lastName;
    private String middleName;
    private String suffix;
    private LocalDate birthDate;
    private LocalDate deathDate;
    private String birthPlace;
    private String gender;
    private String imageUrl;

    // Current/primary party affiliation (nullable)
    // Role-specific tables track contextual party (e.g., Presidency tracks party at election)
    private String party;

    // Flexible extension
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode externalIds;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode socialMedia;

    // Data provenance
    @Enumerated(EnumType.STRING)
    private DataSource primaryDataSource;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

> **Architect Note (MOD-2):** The `party` field on Individual represents current/primary affiliation. Role-specific tables (Presidency, CongressionalMember) track contextual party at time of that role.

### Refactored Entity: CongressionalMember

```java
@Entity
@Table(name = "congressional_members")
public class CongressionalMember {
    @Id
    private UUID id;

    @Column(name = "individual_id", unique = true)
    private UUID individualId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_id", insertable = false, updatable = false)
    private Individual individual;

    // Congress-specific
    private String bioguideId;

    @Enumerated(EnumType.STRING)
    private Chamber chamber;

    private String state;
    private LocalDateTime congressLastSync;
    private String enrichmentSource;
    private String enrichmentVersion;

    @Enumerated(EnumType.STRING)
    private DataSource dataSource;
}
```

### Updated References

| Entity | Current | Target |
|--------|---------|--------|
| `Presidency` | `person_id` → `persons` | `individual_id` → `individuals` |
| `PositionHolding` | `person_id` → `persons` | `individual_id` → `individuals` |
| `CommitteeMembership` | `person_id` → `persons` | `congressional_member_id` → `congressional_members` |

## Scope

### In Scope

1. **New `individuals` Table**
   - Flyway migration to create table
   - Individual JPA entity
   - IndividualRepository with standard queries

2. **Refactor `persons` to `congressional_members`**
   - Rename table
   - Remove general fields (moved to individuals)
   - Add `individual_id` FK
   - Update CongressionalMember entity

3. **Data Migration**
   - Migrate all persons to individuals table
   - Link congressional_members to individuals
   - Handle duplicate detection (same person, different data sources)

4. **Update Referencing Entities**
   - Presidency: `person_id` → `individual_id`
   - PositionHolding: `person_id` → `individual_id`
   - CommitteeMembership: `person_id` → `congressional_member_id`

5. **Update Services**
   - PersonService → IndividualService + CongressionalMemberService
   - PresidentialSyncService → use Individual
   - FjcCsvImportService → use Individual
   - PlumCsvImportService → use Individual

6. **Update DTOs & Controllers**
   - PersonDTO → IndividualDTO (for general use)
   - MemberDTO (for Congressional members, includes individual data)
   - Backward-compatible API responses

7. **Update Frontend**
   - Update type definitions
   - Update API client calls
   - Component updates (minimal - data structure similar)

### Out of Scope

- New person types (CEO, journalist) - future epics
- UI redesign - this is a data model refactor
- New features - pure infrastructure change

## Stories

### Story Summary

| ID | Story | Priority | Estimate | Status |
|----|-------|----------|----------|--------|
| ARCH-1.1 | Create Individual Entity and Table | P0 | 3 pts | Done |
| ARCH-1.2 | Create CongressionalMember Entity and Refactor Persons Table | P0 | 4 pts | Done |
| ARCH-1.3 | Migrate Existing Data | P0 | **7 pts** | Done |
| ARCH-1.4 | Update Presidency and PositionHolding References | P0 | **5 pts** | Done |
| ARCH-1.5 | Update CommitteeMembership Reference | P1 | 2 pts | Done |
| ARCH-1.6 | Update Services Layer | P1 | 5 pts | Done |
| ARCH-1.7 | Update DTOs and Controllers | P1 | 4 pts | Done |
| ARCH-1.8 | Update Frontend Types and Components | P1 | 1 pt (revised) | Done |
| ARCH-1.9 | Verification and Cleanup | P2 | 2 pts | Draft |

**Epic Total:** 36 story points *(revised from 32 per architect review)*

### Dependency Graph (Revised per MOD-4)

```
Phase 1: ARCH-1.1 (Individual Entity) ──► ARCH-1.3a (Populate individuals)
                                                    │
Phase 2:                                            ▼
         ARCH-1.4a (Add individual_id to presidencies/position_holdings)
                                                    │
Phase 3:                                            ▼
         ARCH-1.2 (Refactor persons → congressional_members)
                                                    │
Phase 4:                                            ▼
         ARCH-1.4b (Drop old person_id columns)
         ARCH-1.5 (CommitteeMembership)
                    │
Phase 5:            ▼
         ARCH-1.6 (Services) ──► ARCH-1.7 (DTOs) ──► ARCH-1.8 (Frontend)
                                                            │
                                                            ▼
                                                    ARCH-1.9 (Verification)
```

> **Architect Note (MOD-4):** Migration order revised to ensure FKs are never broken. Add new columns first, populate them, then rename/drop old columns.

---

## Story Details

### ARCH-1.1: Create Individual Entity and Table

**Status:** Draft | **Estimate:** 3 pts | **Priority:** P0

**As a** developer,
**I want** an Individual entity representing any person in the system,
**So that** we have a single source of truth for biographical data.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `Individual` JPA entity created with all core biographical fields |
| AC2 | Flyway migration `V34__create_individuals_table.sql` creates table |
| AC3 | `IndividualRepository` with standard CRUD + `findByFirstNameAndLastName` |
| AC4 | Indexes on `(first_name, last_name)` and `birth_date` |
| AC5 | Entity includes `external_ids` JSONB for cross-referencing |
| AC6 | Entity includes `party` field for current/primary affiliation (MOD-2) |
| AC7 | Entity includes `primary_data_source` for provenance tracking (MOD-3) |
| AC8 | Composite unique constraint on `(first_name, last_name, birth_date)` for deduplication (MOD-1) |
| AC9 | Unit tests for entity and repository |

#### Technical Notes

**Files to Create:**
- `backend/src/main/java/org/newsanalyzer/model/Individual.java`
- `backend/src/main/java/org/newsanalyzer/repository/IndividualRepository.java`
- `backend/src/main/resources/db/migration/V34__create_individuals_table.sql`

**Migration SQL:**
```sql
CREATE TABLE individuals (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    suffix VARCHAR(20),
    birth_date DATE,
    death_date DATE,
    birth_place VARCHAR(200),
    gender VARCHAR(10),
    image_url VARCHAR(500),
    party VARCHAR(50),
    external_ids JSONB,
    social_media JSONB,
    primary_data_source VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_individuals_name ON individuals(first_name, last_name);
CREATE INDEX idx_individuals_birth_date ON individuals(birth_date);

-- MOD-1: Composite unique constraint for deduplication
-- Prevents duplicate Individual records at the database level
-- WHERE clause handles cases where birth_date is unknown
CREATE UNIQUE INDEX idx_individuals_unique_person
ON individuals(LOWER(first_name), LOWER(last_name), birth_date)
WHERE birth_date IS NOT NULL;
```

---

### ARCH-1.2: Create CongressionalMember Entity and Refactor Persons Table

**Status:** Draft | **Estimate:** 4 pts | **Priority:** P0

**As a** developer,
**I want** Congressional-specific data in a separate table linked to Individual,
**So that** Congressional members are a specialized view of individuals.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `CongressionalMember` entity created with Congress-specific fields only |
| AC2 | `individual_id` FK links to `individuals` table (unique constraint) |
| AC3 | Flyway migration renames `persons` → `congressional_members` |
| AC4 | Migration adds `individual_id` column |
| AC5 | Migration removes fields moved to `individuals` |
| AC6 | `CongressionalMemberRepository` with `findByBioguideId`, `findByIndividualId` |
| AC7 | Unique constraint on `bioguide_id` preserved |

#### Technical Notes

**Files to Create:**
- `backend/src/main/java/org/newsanalyzer/model/CongressionalMember.java`
- `backend/src/main/java/org/newsanalyzer/repository/CongressionalMemberRepository.java`
- `backend/src/main/resources/db/migration/V35__refactor_persons_to_congressional_members.sql`

**Migration Strategy:**
```sql
-- Step 1: Add individual_id column (nullable initially)
ALTER TABLE persons ADD COLUMN individual_id UUID;

-- Step 2: Rename table
ALTER TABLE persons RENAME TO congressional_members;

-- Step 3: Update column constraints (after data migration in ARCH-1.3)
```

---

### ARCH-1.3: Migrate Existing Data

**Status:** Draft | **Estimate:** 7 pts | **Priority:** P0

> **Estimate Revised:** Increased from 5 to 7 pts per architect review (MOD-4) due to multi-phase migration complexity.

**As a** developer,
**I want** all existing person data migrated to the new structure,
**So that** no data is lost and all references remain valid.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | All existing persons have corresponding Individual records |
| AC2 | Duplicate detection: same name + birth date = same individual |
| AC3 | Congressional members linked to their individual records |
| AC4 | Non-Congressional persons (presidents, judges, appointees) have individual records |
| AC5 | `individual_id` populated for all congressional_members |
| AC6 | Migration is idempotent (can run multiple times safely) |
| AC7 | Rollback script available |
| AC8 | Data verification queries confirm no data loss |

#### Technical Notes

**Migration Approach:**
1. For each person in `persons` table:
   - Check if Individual exists (by name + birth date)
   - If not, create Individual with biographical data
   - Link congressional_member to individual

**Deduplication Logic:**
```sql
-- Example: Donald Trump may have been imported as both president and appointee
-- Merge into single Individual record
SELECT first_name, last_name, birth_date, COUNT(*)
FROM persons
GROUP BY first_name, last_name, birth_date
HAVING COUNT(*) > 1;
```

**Files to Create:**
- `backend/src/main/resources/db/migration/V36__migrate_persons_to_individuals.sql`
- `backend/src/main/java/org/newsanalyzer/service/DataMigrationService.java` (for complex logic)

---

### ARCH-1.4: Update Presidency and PositionHolding References

**Status:** Draft | **Estimate:** 5 pts | **Priority:** P0

> **Estimate Revised:** Increased from 4 to 5 pts per architect review (MOD-4) due to two-phase FK migration.

**As a** developer,
**I want** Presidency and PositionHolding to reference Individual instead of Person,
**So that** presidents, VPs, and appointees link to the master individual record.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `Presidency.personId` renamed to `individualId` |
| AC2 | `Presidency.person` relationship updated to `individual` |
| AC3 | `PositionHolding.personId` renamed to `individualId` |
| AC4 | `PositionHolding.person` relationship updated to `individual` |
| AC5 | Flyway migration updates FK references |
| AC6 | Indexes updated for new column names |
| AC7 | All existing FK values point to valid Individual records |
| AC8 | Repository query methods updated |

#### Technical Notes

**Two-Phase Migration Approach (MOD-4):**

**Phase A: Add new column (V37)**
```sql
-- Add individual_id columns (nullable initially)
ALTER TABLE presidencies ADD COLUMN individual_id UUID;
ALTER TABLE position_holdings ADD COLUMN individual_id UUID;

-- Populate from mapping (person_id → individuals.id)
UPDATE presidencies p
SET individual_id = i.id
FROM individuals i
INNER JOIN persons per ON i.first_name = per.first_name
                       AND i.last_name = per.last_name
WHERE p.person_id = per.id;

UPDATE position_holdings ph
SET individual_id = i.id
FROM individuals i
INNER JOIN persons per ON i.first_name = per.first_name
                       AND i.last_name = per.last_name
WHERE ph.person_id = per.id;
```

**Phase B: Finalize constraints (V38 - after ARCH-1.2)**
```sql
-- Make NOT NULL and add FK constraints
ALTER TABLE presidencies ALTER COLUMN individual_id SET NOT NULL;
ALTER TABLE presidencies ADD CONSTRAINT fk_presidency_individual
    FOREIGN KEY (individual_id) REFERENCES individuals(id);
ALTER TABLE presidencies DROP COLUMN person_id;

ALTER TABLE position_holdings ALTER COLUMN individual_id SET NOT NULL;
ALTER TABLE position_holdings ADD CONSTRAINT fk_holding_individual
    FOREIGN KEY (individual_id) REFERENCES individuals(id);
ALTER TABLE position_holdings DROP COLUMN person_id;
```

---

### ARCH-1.5: Update CommitteeMembership Reference

**Status:** Draft | **Estimate:** 2 pts | **Priority:** P1

**As a** developer,
**I want** CommitteeMembership to reference CongressionalMember,
**So that** committee membership is properly scoped to Congressional context.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `CommitteeMembership.personId` renamed to `congressionalMemberId` |
| AC2 | FK updated to reference `congressional_members` table |
| AC3 | Repository query methods updated |
| AC4 | Unique constraint updated |

#### Technical Notes

**Files to Modify:**
- `backend/src/main/java/org/newsanalyzer/model/CommitteeMembership.java`
- `backend/src/main/java/org/newsanalyzer/repository/CommitteeMembershipRepository.java`

> **Architectural Decision (MOD-5):** CommitteeMembership references CongressionalMember (not Individual) because committee membership is **semantically Congressional** - only Congressional members can serve on Congressional committees. This constraint is properly modeled at the FK level, ensuring data integrity.

---

### ARCH-1.6: Update Services Layer

**Status:** Draft | **Estimate:** 5 pts | **Priority:** P1

**As a** developer,
**I want** all services updated to use Individual and CongressionalMember,
**So that** business logic uses the new data model correctly.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `IndividualService` created for individual CRUD operations |
| AC2 | `CongressionalMemberService` created (or PersonService refactored) |
| AC3 | `PresidentialSyncService` updated to create/find Individuals |
| AC4 | `FjcCsvImportService` updated to create Individuals for judges |
| AC5 | `PlumCsvImportService` updated to create Individuals for appointees |
| AC6 | `CongressSyncService` updated to use CongressionalMember + Individual |
| AC7 | Duplicate detection logic in all sync services uses Individual lookup |
| AC8 | All service tests pass |

#### Technical Notes

**Key Change in Sync Logic:**
```java
// Before
Person person = personRepository.findByFirstNameAndLastName(first, last);

// After
Individual individual = individualRepository.findByFirstNameAndLastName(first, last)
    .orElseGet(() -> createIndividual(first, last, birthDate, ...));
```

---

### ARCH-1.7: Update DTOs and Controllers

**Status:** Draft | **Estimate:** 4 pts | **Priority:** P1

**As a** developer,
**I want** DTOs and controllers updated for backward compatibility,
**So that** API consumers don't experience breaking changes.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `IndividualDTO` created with all biographical fields |
| AC2 | `MemberDTO` includes individual data + Congressional fields (flattened for backward compat) |
| AC3 | `PresidencyDTO` continues to include president name/bio (from Individual) |
| AC4 | `JudgeDTO` continues to include judge name/bio (from Individual) |
| AC5 | API response structure unchanged (field names preserved) |
| AC6 | Controller tests verify backward compatibility |
| AC7 | OpenAPI documentation updated |
| AC8 | API version remains v1 (no breaking changes at endpoint level) (MOD-6) |
| AC9 | OpenAPI schemas updated with deprecation notices on any person-specific endpoints (MOD-6) |

#### Technical Notes

**Backward Compatibility Strategy:**
```java
// MemberDTO flattens Individual + CongressionalMember data
public record MemberDTO(
    UUID id,
    // From Individual
    String firstName,
    String lastName,
    LocalDate birthDate,
    String birthPlace,
    String imageUrl,
    // From CongressionalMember
    String bioguideId,
    String chamber,
    String state,
    String party
) {}
```

---

### ARCH-1.8: Update Frontend Types and Components

**Status:** Draft | **Estimate:** 3 pts | **Priority:** P1

**As a** frontend developer,
**I want** TypeScript types and components updated for the new model,
**So that** the UI works correctly with the refactored backend.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `Individual` TypeScript interface created |
| AC2 | `CongressionalMember` TypeScript interface created |
| AC3 | Existing `Person` type mapped to backward-compatible DTO |
| AC4 | API client functions updated if endpoint paths changed |
| AC5 | Components using person data continue to work |
| AC6 | All frontend tests pass |

#### Technical Notes

**Minimal Changes Expected:**
- Backend provides backward-compatible DTOs
- Frontend types may need aliases: `type Person = MemberDTO`
- Component props should remain compatible

---

### ARCH-1.9: Verification and Cleanup

**Status:** Draft | **Estimate:** 2 pts | **Priority:** P2

**As a** developer,
**I want** the refactor verified and legacy code cleaned up,
**So that** the codebase is clean and maintainable.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | All backend tests pass (unit + integration) |
| AC2 | All frontend tests pass |
| AC3 | Manual verification of key workflows |
| AC4 | Old `Person` entity removed (replaced by Individual + CongressionalMember) |
| AC5 | Old `PersonRepository` removed or deprecated |
| AC6 | Documentation updated (entity diagrams, API docs) |
| AC7 | ROADMAP.md updated with ARCH-1 completion |

---

## Migration Strategy

### Phase 1: Additive Changes (Non-Breaking)
1. Create `individuals` table (empty)
2. Add `individual_id` column to `persons` (nullable)
3. Add `individual_id` columns to `presidencies`, `position_holdings` (nullable)

### Phase 2: Data Migration
4. Populate `individuals` from existing `persons` data
5. Set `individual_id` FKs in all tables
6. Verify no null FKs remain

### Phase 3: Schema Finalization
7. Make `individual_id` NOT NULL where required
8. Rename `persons` → `congressional_members`
9. Drop old columns from `congressional_members`
10. Add FK constraints

### Phase 4: Code Updates
11. Update entities, repositories, services
12. Update DTOs for backward compatibility
13. Update frontend types

### Rollback Plan

Each migration includes reverse migration:
- `V34__create_individuals_table.sql` → `V34_rollback__drop_individuals_table.sql`
- etc.

---

## Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Data loss during migration | HIGH | LOW | Backup before migration, verification queries |
| Duplicate individuals created | MEDIUM | MEDIUM | Deduplication logic using name + birth date + unique constraint |
| Breaking API changes | HIGH | LOW | Backward-compatible DTOs, extensive testing |
| Performance regression | MEDIUM | LOW | Proper indexing, query optimization |
| Extended downtime | MEDIUM | LOW | Migrations designed to be incremental |
| **Name collision for different people** (MOD-7) | HIGH | LOW | Birth_date in deduplication; when same name + same DOB, require manual confirmation or additional distinguishing data in `external_ids` |

## Definition of Done

- [ ] ARCH-1.1: Individual entity and table created
- [ ] ARCH-1.2: CongressionalMember entity and table refactored
- [ ] ARCH-1.3: All data migrated successfully
- [ ] ARCH-1.4: Presidency and PositionHolding use Individual
- [ ] ARCH-1.5: CommitteeMembership uses CongressionalMember
- [ ] ARCH-1.6: All services updated
- [ ] ARCH-1.7: DTOs provide backward compatibility
- [ ] ARCH-1.8: Frontend works with new model
- [ ] ARCH-1.9: Verification complete, cleanup done
- [ ] All tests pass (600+ backend, 600+ frontend)
- [ ] No data loss verified
- [ ] API backward compatible
- [ ] ROADMAP.md updated

## Related Documentation

- [Person Entity (Current)](../../backend/src/main/java/org/newsanalyzer/model/Person.java)
- [Presidency Entity](../../backend/src/main/java/org/newsanalyzer/model/Presidency.java)
- [PositionHolding Entity](../../backend/src/main/java/org/newsanalyzer/model/PositionHolding.java)
- [KB-1 Epic (Presidency Data)](../KB-1/KB-1.epic-potus-data.md)
- [KB-2 Epic (Presidential Administrations)](../KB-2/KB-2.epic-presidential-administrations.md) - BLOCKED by this epic

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-01-08 | 1.0 | Initial epic creation | Sarah (PO) |
| 2026-01-08 | 1.1 | Architect review: Applied 7 modifications (MOD-1 through MOD-7), revised estimates (32→36 pts), updated migration order | Winston (Architect) |
| 2026-03-14 | 1.2 | Stories 1.1–1.8 confirmed Done via codebase audit. ARCH-1.8 revised (3→1 pt, separate type files unnecessary). Only ARCH-1.9 (Verification & Cleanup) remains. Person.java and PersonRepository.java still need removal. | Sarah (PO) |

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | Sarah (PO) | 2026-01-08 | DRAFTED |
| Architect | Winston | 2026-01-08 | **APPROVED WITH MODIFICATIONS** |
| Developer | TBD | TBD | - |

---

## Architect Review Notes

**Review Date:** 2026-01-08
**Reviewer:** Winston (Architect)

### Verdict: APPROVED WITH MODIFICATIONS

The epic is architecturally sound and addresses a real data modeling debt. The proposed separation of Individual from role-specific tables follows established patterns and will provide a clean foundation for future expansion.

### Applied Modifications

| # | Modification | Impact |
|---|--------------|--------|
| MOD-1 | Add composite unique constraint `(first_name, last_name, birth_date)` on individuals | ARCH-1.1 - prevents duplicates at DB level |
| MOD-2 | Add `party` field to Individual entity | ARCH-1.1 - supports general party affiliation |
| MOD-3 | Add `primary_data_source` to Individual | ARCH-1.1 - tracks data provenance |
| MOD-4 | Revise migration order (data before rename) | ARCH-1.2, 1.3, 1.4 - ensures FKs never broken |
| MOD-5 | Document CommitteeMembership FK rationale | ARCH-1.5 - semantic constraint documented |
| MOD-6 | Add API versioning ACs | ARCH-1.7 - explicit backward compat requirements |
| MOD-7 | Add name collision risk | Risk table - handles edge case of same name+DOB |

### Estimate Adjustments

| Story | Original | Revised | Reason |
|-------|----------|---------|--------|
| ARCH-1.3 | 5 pts | 7 pts | Migration complexity with proper ordering |
| ARCH-1.4 | 4 pts | 5 pts | Two-phase FK migration |
| **Total** | 32 pts | 36 pts | +4 pts |

### Architecture Alignment

- **Pattern Used:** Party pattern (single entity for real-world person, role-specific satellites)
- **Normalization:** Proper 3NF with JSONB for flexible extension
- **Backward Compatibility:** DTO flattening preserves API contracts

---

*End of Epic Document*
