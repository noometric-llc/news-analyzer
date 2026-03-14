# Story ARCH-1.9: Verification and Cleanup

## Status

**Status:** Approved
**Priority:** P1 (revised from P2 — blocking ARCH-1 completion which blocks KB-2)
**Estimate:** 5 story points (revised from 2 — codebase audit revealed 9 production files still using Person/PersonRepository)
**Phase:** Final

## Story

**As a** developer,
**I want** the refactor verified and legacy code cleaned up,
**So that** the codebase is clean, maintainable, and Person.java can be fully removed.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | `Person.Chamber` enum extracted to standalone `Chamber.java` (or reuse `CongressionalMember.Chamber`) | |
| AC2 | `GovernmentPosition` and related files use extracted Chamber enum, not `Person.Chamber` | |
| AC3 | 5 services migrated from `PersonRepository` to `CongressionalMemberRepository` for bioguide lookups | |
| AC4 | Old `Person.java` entity deleted | |
| AC5 | Old `PersonRepository.java` deleted | |
| AC6 | All backend tests pass (762+ expected) | |
| AC7 | All frontend tests pass | |
| AC8 | Manual verification of key workflows | |
| AC9 | Documentation updated (entity diagrams, source tree) | |

## Tasks / Subtasks

- [ ] **Task 1: Extract Chamber Enum** (AC1, AC2)
  - [ ] Determine if `CongressionalMember.Chamber` already exists and can be reused, OR create standalone `backend/src/main/java/org/newsanalyzer/model/Chamber.java`
  - [ ] Update `GovernmentPosition.java` — change `Person.Chamber` → new Chamber reference (field on line 65, constants on lines 209, 218, 227)
  - [ ] Update `GovernmentPositionRepository.java` — change import and method signatures (lines 6, 31, 33, 39)
  - [ ] Update `PositionController.java` — change `Person.Chamber` import (line 10, usages on lines 132, 133, 155)
  - [ ] Update `TermSyncService.java` — change `Person.Chamber` import (line 7, usages on lines 151-159)
  - [ ] Compile and verify no remaining `Person.Chamber` references

- [ ] **Task 2: Migrate CongressSearchService** (AC3)
  - [ ] Replace `PersonRepository` with `CongressionalMemberRepository`
  - [ ] Update duplicate detection: `personRepository.findByBioguideId()` → `congressionalMemberRepository.findByBioguideId()`
  - [ ] Update result mapping (CongressionalMember.getId() instead of Person.getId())
  - [ ] Update `CongressSearchServiceTest.java` mocks

- [ ] **Task 3: Migrate LegislatorsSearchService** (AC3)
  - [ ] Replace `PersonRepository` with `CongressionalMemberRepository`
  - [ ] Update `findLocalPerson()` → `findLocalMember()` (returns CongressionalMember)
  - [ ] Update local match checking to use CongressionalMember
  - [ ] Update `LegislatorsSearchServiceTest.java` mocks

- [ ] **Task 4: Migrate LegislatorEnrichmentImportService** (AC3)
  - [ ] Replace `PersonRepository` with `CongressionalMemberRepository`
  - [ ] Update bioguide lookup and enrichment operations
  - [ ] Enrichment fields (externalIds, socialMedia) now go to Individual via CongressionalMember.getIndividual()
  - [ ] Update save operations to save both CongressionalMember and Individual if needed
  - [ ] Update tests

- [ ] **Task 5: Migrate LegislatorsEnrichmentService** (AC3)
  - [ ] Replace `PersonRepository` with `CongressionalMemberRepository`
  - [ ] Update batch enrichment to use CongressionalMember + Individual pattern
  - [ ] Update `enrichPerson()` → `enrichMember()` or similar
  - [ ] Update `LegislatorsEnrichmentServiceTest.java` mocks

- [ ] **Task 6: Migrate AdminImportController** (AC3)
  - [ ] Replace `PersonRepository` with `CongressionalMemberRepository`
  - [ ] Update existence check: `personRepository.findByBioguideId()` → `congressionalMemberRepository.findByBioguideId()`
  - [ ] Update `LegislatorExistsResponse` mapping to use CongressionalMember data
  - [ ] Update `AdminImportControllerTest.java` mocks

- [ ] **Task 7: Delete Legacy Files** (AC4, AC5)
  - [ ] Grep for any remaining `import org.newsanalyzer.model.Person` — must be zero
  - [ ] Grep for any remaining `PersonRepository` usage — must be zero
  - [ ] Delete `backend/src/main/java/org/newsanalyzer/model/Person.java`
  - [ ] Delete `backend/src/main/java/org/newsanalyzer/repository/PersonRepository.java`
  - [ ] Compile to verify clean deletion

- [ ] **Task 8: Run Full Test Suites** (AC6, AC7)
  - [ ] Run `cd backend && ./mvnw clean test` — all tests must pass
  - [ ] Run `cd frontend && pnpm test` — all tests must pass
  - [ ] Run `cd frontend && pnpm exec tsc --noEmit` — no type errors

- [ ] **Task 9: Manual Verification** (AC8)
  - [ ] Start local dev environment (Docker infra + 3 services)
  - [ ] Test Congress member search via admin dashboard
  - [ ] Test Legislators enrichment sync
  - [ ] Test Member listing page
  - [ ] Test Presidential administration pages
  - [ ] Test Judge data display

- [ ] **Task 10: Update Documentation** (AC9)
  - [ ] Update `docs/architecture/source-tree.md` — remove Person references, add Individual/CongressionalMember
  - [ ] Update entity relationship references in architecture docs
  - [ ] Update ARCH-1 epic status to Done

## Dev Notes

### Codebase Audit Findings (2026-03-14)

The original ARCH-1.9 assumed Person.java could be trivially deleted. A codebase audit revealed **9 production files and 5 test files** still actively using Person/PersonRepository:

#### Category 1: Chamber Enum Dependency (4 files)

`Person.Chamber` enum is embedded in Person.java but used by unrelated code:

| File | Usage |
|------|-------|
| `GovernmentPosition.java` | Field type: `Person.Chamber chamber` (line 65) |
| `GovernmentPositionRepository.java` | Method params use `Chamber` (lines 31, 33, 39) |
| `PositionController.java` | `Chamber.valueOf()` for API filtering (lines 132-155) |
| `TermSyncService.java` | Chamber conversion during sync (lines 151-159) |

**Fix:** Extract Chamber enum to standalone file or reuse CongressionalMember.Chamber.

#### Category 2: PersonRepository Active Usage (5 services)

| File | Usage | Migration Target |
|------|-------|-----------------|
| `CongressSearchService.java` | Duplicate detection by bioguideId | `CongressionalMemberRepository.findByBioguideId()` |
| `LegislatorsSearchService.java` | Local match checking | `CongressionalMemberRepository.findByBioguideId()` |
| `LegislatorEnrichmentImportService.java` | Enrichment: find + save Person | `CongressionalMemberRepository` + `IndividualRepository` |
| `LegislatorsEnrichmentService.java` | Batch enrichment | `CongressionalMemberRepository` + `IndividualRepository` |
| `AdminImportController.java` | Existence check endpoint | `CongressionalMemberRepository.findByBioguideId()` |

**Key insight:** All 5 services use `PersonRepository.findByBioguideId()` — `CongressionalMemberRepository` already has this method, making migration straightforward. The enrichment services additionally save data (externalIds, socialMedia) which will need to target Individual via the CongressionalMember relationship.

#### Already Removed (confirmed)
- `PersonService.java` — deleted
- `PersonDTO.java` — deleted
- `PersonController.java` — deleted

### Source Tree Reference

```
Files to MODIFY:
backend/src/main/java/org/newsanalyzer/
├── model/
│   ├── Chamber.java                    # NEW (or reuse CongressionalMember.Chamber)
│   └── GovernmentPosition.java         # UPDATE: Person.Chamber → Chamber
├── repository/
│   └── GovernmentPositionRepository.java # UPDATE: Person.Chamber → Chamber
├── controller/
│   ├── PositionController.java         # UPDATE: Person.Chamber → Chamber
│   └── AdminImportController.java      # UPDATE: PersonRepository → CongressionalMemberRepository
├── service/
│   ├── TermSyncService.java            # UPDATE: Person.Chamber → Chamber
│   ├── CongressSearchService.java      # UPDATE: PersonRepository → CongressionalMemberRepository
│   ├── LegislatorsSearchService.java   # UPDATE: PersonRepository → CongressionalMemberRepository
│   ├── LegislatorEnrichmentImportService.java # UPDATE: PersonRepository → CongressionalMember/Individual
│   └── LegislatorsEnrichmentService.java     # UPDATE: PersonRepository → CongressionalMember/Individual

Files to DELETE:
├── model/
│   └── Person.java                     # DELETE after all references removed
├── repository/
│   └── PersonRepository.java           # DELETE after all references removed

Test files to UPDATE:
backend/src/test/java/org/newsanalyzer/
├── controller/
│   ├── AdminImportControllerTest.java  # UPDATE mocks
│   └── PositionControllerTest.java     # UPDATE Chamber import
├── service/
│   ├── CongressSearchServiceTest.java  # UPDATE mocks
│   ├── LegislatorsSearchServiceTest.java      # UPDATE mocks
│   └── LegislatorsEnrichmentServiceTest.java  # UPDATE mocks
```

### Enrichment Migration Pattern

The enrichment services save `externalIds` and `socialMedia` to Person records. After migration:

```java
// Before
Person person = personRepository.findByBioguideId(bioguideId).get();
person.setExternalIds(mergedExternalIds);
person.setSocialMedia(mergedSocialMedia);
personRepository.save(person);

// After
CongressionalMember member = congressionalMemberRepository.findByBioguideId(bioguideId).get();
Individual individual = member.getIndividual();
individual.setExternalIds(mergedExternalIds);
individual.setSocialMedia(mergedSocialMedia);
individualRepository.save(individual);
```

### Testing

**Backend:**
- Run: `cd backend && ./mvnw clean test`
- Expected: 762+ tests pass
- Coverage: JaCoCo 70% threshold

**Frontend:**
- Run: `cd frontend && pnpm test`
- Run: `cd frontend && pnpm exec tsc --noEmit`

**Manual Verification:**
| Workflow | Endpoint | Expected Behavior |
|----------|----------|-------------------|
| Congress Search | POST /api/admin/search/congress | Finds duplicates via CongressionalMember |
| Legislators Search | POST /api/admin/search/legislators | Shows local match via CongressionalMember |
| Enrichment Sync | POST /api/admin/sync/enrichment | Enriches Individual via CongressionalMember |
| Member List | GET /api/members | Returns MemberDTO (unchanged) |
| President Page | GET /api/presidencies | Returns PresidencyDTO (unchanged) |

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-01-08 | 1.0 | Initial story creation from epic | Sarah (PO) |
| 2026-03-14 | 2.0 | Story revised — codebase audit found 9 production files still using Person. Expanded scope: Chamber enum extraction + 5 service migrations + cleanup. Estimate increased from 2 to 5 pts. Priority raised to P1. | Sarah (PO) |

## Dev Agent Record

### Agent Model Used
*To be populated during implementation*

### Debug Log References
*To be populated during implementation*

### Completion Notes List
*To be populated during implementation*

### File List
*To be populated during implementation*

## QA Results
*To be populated after QA review*
