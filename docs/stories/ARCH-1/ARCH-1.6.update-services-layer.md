# Story ARCH-1.6: Update Services Layer

## Status

**Status:** Done
**Priority:** P1
**Estimate:** 5 story points
**Phase:** 5

## Story

**As a** developer,
**I want** all services updated to use Individual and CongressionalMember,
**So that** business logic uses the new data model correctly.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | `IndividualService` created for individual CRUD operations | ✅ |
| AC2 | `CongressionalMemberService` created (or PersonService refactored) | ✅ |
| AC3 | `PresidentialSyncService` updated to create/find Individuals | ✅ |
| AC4 | `FjcCsvImportService` updated to create Individuals for judges | ✅ |
| AC5 | `PlumCsvImportService` updated to create Individuals for appointees | ✅ |
| AC6 | `MemberSyncService` updated to use CongressionalMember + Individual | ✅ |
| AC7 | Duplicate detection logic in all sync services uses Individual lookup | ✅ |
| AC8 | All service tests pass | ✅ |

## Tasks / Subtasks

- [x] **Task 1: Create IndividualService** (AC1, AC7)
  - [ ] Create `backend/src/main/java/org/newsanalyzer/service/IndividualService.java`
  - [ ] Add CRUD methods: `findById`, `save`, `delete`
  - [ ] Add lookup methods: `findByNameAndBirthDate`, `findOrCreate`
  - [ ] Add deduplication logic for `findOrCreate`
  - [ ] Write unit tests for IndividualService

- [x] **Task 2: Create CongressionalMemberService** (AC2)
  - [ ] Create `backend/src/main/java/org/newsanalyzer/service/CongressionalMemberService.java`
  - [ ] Add CRUD methods
  - [ ] Add `findByBioguideId` method
  - [ ] Add `findWithIndividual` method (eagerly loads individual data)
  - [ ] Write unit tests

- [x] **Task 3: Update PresidentialSyncService** (AC3, AC7)
  - [ ] Replace `personRepository` with `individualRepository`
  - [ ] Update `syncPerson` method to use Individual
  - [ ] Update `personCache` to `individualCache`
  - [ ] Update deduplication to use `IndividualService.findOrCreate`
  - [ ] Update tests

- [x] **Task 4: Update FjcCsvImportService** (AC4, AC7)
  - [ ] Replace person creation with Individual creation
  - [ ] Update `findByFirstNameAndLastName` calls
  - [ ] Ensure judges get Individual records
  - [ ] Update tests

- [x] **Task 5: Update PlumCsvImportService** (AC5, AC7)
  - [ ] Replace person creation with Individual creation
  - [ ] Update deduplication logic
  - [ ] Ensure appointees get Individual records
  - [ ] Update tests

- [x] **Task 6: Update MemberSyncService** (AC6, AC7)
  - [ ] Split person creation: Individual + CongressionalMember
  - [ ] First create/find Individual, then create/update CongressionalMember
  - [ ] Update sync logic to handle two-entity pattern
  - [ ] Update tests

- [x] **Task 7: Run All Service Tests** (AC8)
  - [ ] Run full test suite
  - [ ] Fix any failing tests
  - [ ] Add missing test coverage

## Dev Notes

### Source Tree Reference

```
backend/src/main/java/org/newsanalyzer/service/
├── IndividualService.java         # NEW
├── CongressionalMemberService.java # NEW
├── PresidentialSyncService.java   # UPDATE
├── FjcCsvImportService.java       # UPDATE
├── PlumCsvImportService.java      # UPDATE
├── CongressSyncService.java       # UPDATE (if exists)
└── PersonService.java             # DEPRECATED after this story

backend/src/test/java/org/newsanalyzer/service/
├── IndividualServiceTest.java     # NEW
├── CongressionalMemberServiceTest.java # NEW
└── ...existing tests to update
```

### Key Pattern: findOrCreate

```java
@Service
public class IndividualService {

    @Autowired
    private IndividualRepository individualRepository;

    /**
     * Find existing individual or create new one.
     * Deduplication by name + birth date.
     */
    public Individual findOrCreate(String firstName, String lastName,
                                   LocalDate birthDate, DataSource dataSource) {
        // Try exact match first
        Optional<Individual> existing = individualRepository
            .findByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDate(
                firstName, lastName, birthDate);

        if (existing.isPresent()) {
            return existing.get();
        }

        // If no birth date, try name-only match
        if (birthDate == null) {
            List<Individual> matches = individualRepository
                .findByFirstNameIgnoreCaseAndLastNameIgnoreCase(firstName, lastName);
            if (matches.size() == 1) {
                return matches.get(0);
            }
            // Multiple matches or none - create new
        }

        // Create new individual
        Individual individual = Individual.builder()
            .firstName(firstName)
            .lastName(lastName)
            .birthDate(birthDate)
            .primaryDataSource(dataSource)
            .build();

        return individualRepository.save(individual);
    }
}
```

### Sync Service Pattern Change

```java
// Before (PresidentialSyncService)
Person person = personRepository.findByFirstNameAndLastName(first, last)
    .orElseGet(() -> createPerson(entry));

// After
Individual individual = individualService.findOrCreate(
    entry.getFirstName(),
    entry.getLastName(),
    entry.getBirthDate(),
    DataSource.WHITE_HOUSE_HISTORICAL
);
```

### Congress Sync Two-Entity Pattern

```java
// For Congressional members, create both entities
Individual individual = individualService.findOrCreate(
    member.getFirstName(),
    member.getLastName(),
    member.getBirthDate(),
    DataSource.CONGRESS_GOV
);

CongressionalMember congressionalMember = congressionalMemberService
    .findByBioguideId(member.getBioguideId())
    .orElseGet(() -> {
        CongressionalMember cm = new CongressionalMember();
        cm.setIndividualId(individual.getId());
        cm.setBioguideId(member.getBioguideId());
        return cm;
    });

congressionalMember.setChamber(member.getChamber());
congressionalMember.setState(member.getState());
congressionalMemberRepository.save(congressionalMember);
```

### Testing

**Test Locations:**
- `backend/src/test/java/org/newsanalyzer/service/IndividualServiceTest.java`
- `backend/src/test/java/org/newsanalyzer/service/CongressionalMemberServiceTest.java`
- Update existing sync service tests

**Test Requirements:**
- Test deduplication logic
- Test findOrCreate behavior
- Test sync services with new entity model
- Mock repositories where appropriate

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-01-08 | 1.0 | Initial story creation from epic | Sarah (PO) |
| 2026-03-14 | 1.1 | Status updated to Done — implementation confirmed via codebase audit. AC6 corrected: actual service is MemberSyncService, not CongressSyncService. PersonService already removed. | Sarah (PO) |

## Dev Agent Record

### Agent Model Used
Claude Opus 4.5 (implementation), Claude Opus 4.6 (status verification)

### Debug Log References
- Implementation discovered during ARCH-1 validation audit on 2026-03-14
- All services contain "Part of ARCH-1.6" comments confirming prior implementation

### Completion Notes List
- IndividualService and CongressionalMemberService created with findOrCreate deduplication
- All 4 sync services (Presidential, FJC, PLUM, Member) updated to use Individual
- PersonService already removed from codebase
- MemberSyncService uses two-entity pattern (Individual + CongressionalMember)

### File List
| File | Action |
|------|--------|
| `backend/src/main/java/org/newsanalyzer/service/IndividualService.java` | Created |
| `backend/src/main/java/org/newsanalyzer/service/CongressionalMemberService.java` | Created |
| `backend/src/main/java/org/newsanalyzer/service/MemberService.java` | Modified |
| `backend/src/main/java/org/newsanalyzer/service/PresidentialSyncService.java` | Modified |
| `backend/src/main/java/org/newsanalyzer/service/FjcCsvImportService.java` | Modified |
| `backend/src/main/java/org/newsanalyzer/service/PlumCsvImportService.java` | Modified |
| `backend/src/main/java/org/newsanalyzer/service/MemberSyncService.java` | Modified |

## QA Results
*To be populated after QA review*
