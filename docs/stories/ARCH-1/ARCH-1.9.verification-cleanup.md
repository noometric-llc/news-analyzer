# Story ARCH-1.9: Verification and Cleanup

## Status

**Status:** Done
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

- [x] **Task 1: Extract Chamber Enum** (AC1, AC2)
  - [x] Created standalone `backend/src/main/java/org/newsanalyzer/model/Chamber.java`
  - [x] Removed inner Chamber enum from `CongressionalMember.java` and `Person.java`
  - [x] Updated ~20 files: GovernmentPosition, GovernmentPositionRepository, PositionController, TermSyncService, PositionInitializationService, CongressionalMemberService, CongressionalMemberRepository, MemberController, MemberSyncService, MemberService, PositionHoldingRepository, PersonRepository, + 8 test files
  - [x] Compile verified — no remaining `Person.Chamber` or `CongressionalMember.Chamber` references

- [x] **Task 2: Migrate CongressSearchService** (AC3)
  - [x] Replace `PersonRepository` with `CongressionalMemberRepository`
  - [x] Update duplicate detection: `personRepository.findByBioguideId()` → `congressionalMemberRepository.findByBioguideId()`
  - [x] Update result mapping (CongressionalMember.getId() instead of Person.getId())
  - [x] Update `CongressSearchServiceTest.java` mocks

- [x] **Task 3: Migrate LegislatorsSearchService** (AC3)
  - [x] Replace `PersonRepository` with `CongressionalMemberRepository`
  - [x] Update `findLocalPerson()` → `findLocalMember()` (returns CongressionalMember)
  - [x] Update local match checking to use CongressionalMember
  - [x] Update `LegislatorsSearchServiceTest.java` mocks

- [x] **Task 4: Migrate LegislatorEnrichmentImportService** (AC3)
  - [x] Replace `PersonRepository` with `CongressionalMemberRepository` + `IndividualRepository`
  - [x] Update `previewEnrichment()` — CongressionalMember + Individual pattern
  - [x] Update `enrichPerson()` — externalIds/socialMedia → Individual, enrichmentSource/Version → CongressionalMember
  - [x] Save both entities

- [x] **Task 5: Migrate LegislatorsEnrichmentService** (AC3)
  - [x] Replace `PersonRepository` with `CongressionalMemberRepository` + `IndividualRepository`
  - [x] Update batch enrichment to use CongressionalMember + Individual pattern
  - [x] Update `enrichPerson()` → `enrichMember()` (splits data across two entities)
  - [x] Update `LegislatorsEnrichmentServiceTest.java` — full rewrite for two-entity pattern

- [x] **Task 6: Migrate AdminImportController** (AC3)
  - [x] Remove `PersonRepository` field entirely
  - [x] Update `checkLegislatorExists()` — use `congressionalMemberRepository.findByBioguideIdWithIndividual()`
  - [x] Update `LegislatorExistsResponse` mapping to use CongressionalMember + Individual data
  - [x] Update `AdminImportControllerTest.java` — remove PersonRepository mock

- [x] **Task 7: Delete Legacy Files** (AC4, AC5)
  - [x] Grep confirmed zero remaining `import org.newsanalyzer.model.Person` references
  - [x] Grep confirmed zero remaining `PersonRepository` usage
  - [x] Deleted `backend/src/main/java/org/newsanalyzer/model/Person.java`
  - [x] Deleted `backend/src/main/java/org/newsanalyzer/repository/PersonRepository.java`
  - [x] Compile verified clean

- [x] **Task 8: Run Full Test Suites** (AC6, AC7)
  - [x] Backend: `./mvnw clean test` — 765 tests passed, 0 failures, BUILD SUCCESS
  - [x] Frontend: `vitest run` — 33 test files, 687 tests passed
  - [x] Frontend: `tsc --noEmit` — no type errors

- [x] **Task 9: Manual Verification** (AC8)
  - [x] Start local dev environment (Docker infra + 3 services)
  - [x] Test Congress member search via admin dashboard — returns total: 538, API connected
  - [x] Test Legislators search — returns results (9 for "sanders"), YAML parsing fixed
  - [x] Test Member listing page — paginated JSON, empty DB correct
  - [x] Test Presidential administration pages — paginated JSON, empty DB correct
  - [x] Test Member exists check — proper JSON response
  - [x] Fixed pre-existing bug: SnakeYAML code point limit (3MB → 50MB) in LegislatorsRepoClient
  - [x] Fixed pre-existing bug: Integer overflow in LegislatorYamlRecord (Integer → Long for govtrack/votesmart/cspan/houseHistory)

- [x] **Task 10: Update Documentation** (AC9)
  - [x] Update `docs/architecture/source-tree.md` — remove Person references
  - [x] Update ARCH-1 epic status
  - [x] Story file updated with completion notes

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
Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References
- No blocking issues encountered during implementation
- Scope was larger than story initially listed (4 files → ~20 files for Chamber extraction due to both Person.Chamber and CongressionalMember.Chamber references)
- PositionInitializationService.java was discovered as an unlisted dependency during Task 1

### Completion Notes List
- Created standalone Chamber.java enum to decouple from entity classes
- All 5 services migrated from PersonRepository to CongressionalMemberRepository
- Enrichment services (LegislatorEnrichmentImportService, LegislatorsEnrichmentService) now split data: externalIds/socialMedia → Individual, enrichmentSource/enrichmentVersion → CongressionalMember
- Person.java and PersonRepository.java successfully deleted with zero remaining references
- 765 backend tests pass, 687 frontend tests pass
- Task 9 Manual Verification complete — all endpoints return correct JSON structure
- Fixed 2 pre-existing bugs discovered during manual verification:
  - SnakeYAML code point limit too small for congress-legislators YAML files (~10MB)
  - LegislatorYamlRecord numeric ID fields overflow int (changed Integer → Long)

### File List

**Created:**
- `backend/src/main/java/org/newsanalyzer/model/Chamber.java` — standalone Chamber enum

**Deleted:**
- `backend/src/main/java/org/newsanalyzer/model/Person.java`
- `backend/src/main/java/org/newsanalyzer/repository/PersonRepository.java`

**Modified (source):**
- `backend/src/main/java/org/newsanalyzer/model/CongressionalMember.java` — removed inner Chamber enum
- `backend/src/main/java/org/newsanalyzer/model/GovernmentPosition.java` — Person.Chamber → Chamber
- `backend/src/main/java/org/newsanalyzer/repository/CongressionalMemberRepository.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/repository/GovernmentPositionRepository.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/repository/PositionHoldingRepository.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/controller/AdminImportController.java` — removed PersonRepository
- `backend/src/main/java/org/newsanalyzer/controller/MemberController.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/controller/PositionController.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/service/CongressSearchService.java` — PersonRepository → CongressionalMemberRepository
- `backend/src/main/java/org/newsanalyzer/service/CongressionalMemberService.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/service/LegislatorEnrichmentImportService.java` — two-entity enrichment pattern
- `backend/src/main/java/org/newsanalyzer/service/LegislatorsEnrichmentService.java` — two-entity enrichment pattern
- `backend/src/main/java/org/newsanalyzer/service/LegislatorsSearchService.java` — PersonRepository → CongressionalMemberRepository
- `backend/src/main/java/org/newsanalyzer/service/MemberService.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/service/MemberSyncService.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/service/PositionInitializationService.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/service/TermSyncService.java` — Chamber import
- `backend/src/main/java/org/newsanalyzer/service/LegislatorsRepoClient.java` — SnakeYAML code point limit fix (3MB → 50MB)
- `backend/src/main/java/org/newsanalyzer/dto/LegislatorYamlRecord.java` — Integer → Long for numeric ID fields
- `backend/src/main/java/org/newsanalyzer/dto/LegislatorDetailDTO.java` — Integer → Long for ExternalIdsInfo

**Modified (tests):**
- `backend/src/test/java/org/newsanalyzer/controller/AdminImportControllerTest.java` — removed PersonRepository mock
- `backend/src/test/java/org/newsanalyzer/controller/CommitteeControllerTest.java` — Chamber import
- `backend/src/test/java/org/newsanalyzer/controller/MemberControllerTest.java` — Chamber import
- `backend/src/test/java/org/newsanalyzer/controller/PositionControllerTest.java` — Chamber import
- `backend/src/test/java/org/newsanalyzer/repository/CongressionalMemberRepositoryTest.java` — Chamber import
- `backend/src/test/java/org/newsanalyzer/service/CongressSearchServiceTest.java` — updated mocks
- `backend/src/test/java/org/newsanalyzer/service/CongressionalMemberServiceTest.java` — Chamber import
- `backend/src/test/java/org/newsanalyzer/service/LegislatorsEnrichmentServiceTest.java` — full rewrite for two-entity pattern
- `backend/src/test/java/org/newsanalyzer/service/LegislatorsSearchServiceTest.java` — updated mocks
- `backend/src/test/java/org/newsanalyzer/service/MemberSyncServiceTest.java` — Chamber import
- `backend/src/test/java/org/newsanalyzer/service/TermSyncServiceTest.java` — Chamber import
- `backend/src/test/java/org/newsanalyzer/service/LegislatorsRepoClientTest.java` — Integer → Long assertions
- `backend/src/test/java/org/newsanalyzer/controller/AdminSearchControllerTest.java` — Integer → Long in builder

## QA Results

### Review Date: 2026-03-14

### Reviewed By: Quinn (Test Architect)

**Verification Summary:**

| AC | Criterion | Verified |
|----|-----------|----------|
| AC1 | Chamber.java extracted as standalone enum | PASS — `backend/src/main/java/org/newsanalyzer/model/Chamber.java` exists |
| AC2 | GovernmentPosition uses extracted Chamber | PASS — zero `Person.Chamber` or `CongressionalMember.Chamber` code references |
| AC3 | 5 services migrated to CongressionalMemberRepository | PASS — grep confirms zero `PersonRepository` code usage |
| AC4 | Person.java deleted | PASS — file does not exist |
| AC5 | PersonRepository.java deleted | PASS — file does not exist |
| AC6 | Backend tests pass | PASS — 765 tests, 0 failures |
| AC7 | Frontend tests pass | PASS — 687 tests, 0 failures, tsc clean |
| AC8 | Manual verification of key workflows | PASS — all endpoints return correct JSON |
| AC9 | Documentation updated | PASS — source tree and epic status updated |

**Additional findings:**
- 2 pre-existing bugs fixed during verification (SnakeYAML limit, Integer overflow) — bonus quality improvement
- 5 test files contain `PersonRepository` in comments only (migration history notes) — acceptable
- Some DTOs retain Person-era naming (PersonSnapshot, personId) — cosmetic, not blocking

### Gate Status

Gate: PASS → docs/qa/gates/ARCH-1.9-verification-cleanup.yml
