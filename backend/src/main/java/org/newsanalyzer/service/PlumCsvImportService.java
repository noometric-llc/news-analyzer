package org.newsanalyzer.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.PlumCsvRecord;
import org.newsanalyzer.dto.PlumImportResult;
import org.newsanalyzer.model.*;
import org.newsanalyzer.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for importing executive branch appointee data from OPM's PLUM CSV file.
 *
 * Downloads and parses the PLUM (Policy and Supporting Positions) CSV file,
 * creating/updating Individual, GovernmentPosition, and PositionHolding records.
 *
 * Part of ARCH-1.6: Updated to use Individual instead of Person.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 * @see <a href="https://www.opm.gov/about-us/open-government/plum-reporting/">OPM PLUM Data</a>
 */
@Service
@Slf4j
public class PlumCsvImportService {

    private final IndividualService individualService;
    private final GovernmentPositionRepository positionRepository;
    private final PositionHoldingRepository holdingRepository;
    private final GovernmentOrganizationRepository orgRepository;
    private final RestTemplate restTemplate;
    private final TransactionTemplate transactionTemplate;

    public PlumCsvImportService(IndividualService individualService,
                                 GovernmentPositionRepository positionRepository,
                                 PositionHoldingRepository holdingRepository,
                                 GovernmentOrganizationRepository orgRepository,
                                 RestTemplate restTemplate,
                                 PlatformTransactionManager transactionManager) {
        this.individualService = individualService;
        this.positionRepository = positionRepository;
        this.holdingRepository = holdingRepository;
        this.orgRepository = orgRepository;
        this.restTemplate = restTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Value("${plum.csv.url:https://www.opm.gov/about-us/open-government/plum-reporting/plum-archive/plum-archive-biden-administration.csv}")
    private String plumCsvUrl;

    @Value("${plum.import.batch-size:100}")
    private int batchSize;

    private static final DateTimeFormatter PLUM_DATE_FORMAT =
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm");

    private static final DateTimeFormatter PLUM_DATE_FORMAT_ALT =
            DateTimeFormatter.ofPattern("M/d/yyyy");

    private static final String[] REQUIRED_PLUM_HEADERS = {
            "AgencyName", "PositionTitle", "PositionStatus", "IncumbentFirstName", "IncumbentLastName"
    };

    // Cache for organization lookups during import
    private final Map<String, UUID> orgCache = new ConcurrentHashMap<>();

    /**
     * Import PLUM data from the configured CSV URL (processes all records).
     *
     * Note: This method is NOT transactional to allow individual record failures
     * without cascading to the entire import. Each record is processed in its own
     * transaction via processSingleRecord().
     *
     * @return Import result with statistics
     */
    public PlumImportResult importFromUrl() {
        return importFromUrl(null, null);
    }

    /**
     * Import PLUM data from the configured CSV URL with optional offset/limit for chunked processing.
     *
     * @param offset Number of records to skip (null = start from beginning)
     * @param limit Maximum number of records to process (null = process all remaining)
     * @return Import result with statistics
     */
    public PlumImportResult importFromUrl(Integer offset, Integer limit) {
        log.info("Starting PLUM CSV import from: {} (offset={}, limit={})", plumCsvUrl, offset, limit);

        PlumImportResult.PlumImportResultBuilder result = PlumImportResult.builder()
                .startTime(LocalDateTime.now())
                .errorDetails(new ArrayList<>())
                .unmatchedAgencyNames(new ArrayList<>());

        try {
            // Download CSV content
            String csvContent = downloadCsv();
            if (csvContent == null || csvContent.isBlank()) {
                log.error("Failed to download PLUM CSV - empty response");
                result.errors(1);
                result.endTime(LocalDateTime.now());
                PlumImportResult builtResult = result.build();
                builtResult.addError(0, "Failed to download CSV - empty response", null);
                return builtResult;
            }

            // Parse and process
            return processCSV(csvContent, result, offset, limit);

        } catch (Exception e) {
            log.error("PLUM import failed with exception", e);
            result.errors(1);
            result.endTime(LocalDateTime.now());
            PlumImportResult builtResult = result.build();
            builtResult.addError(0, "Import failed: " + e.getMessage(), null);
            return builtResult;
        }
    }

    /**
     * Import PLUM data from a local file (for testing or manual imports).
     *
     * Note: This method is NOT transactional to allow individual record failures
     * without cascading to the entire import. Each record is processed in its own
     * transaction via processSingleRecord().
     *
     * @param file CSV file to import
     * @return Import result with statistics
     */
    public PlumImportResult importFromFile(File file) {
        log.info("Starting PLUM CSV import from file: {}", file.getAbsolutePath());

        PlumImportResult.PlumImportResultBuilder result = PlumImportResult.builder()
                .startTime(LocalDateTime.now())
                .errorDetails(new ArrayList<>())
                .unmatchedAgencyNames(new ArrayList<>());

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return processCSV(sb.toString(), result);

        } catch (IOException e) {
            log.error("Failed to read PLUM CSV file", e);
            result.errors(1);
            result.endTime(LocalDateTime.now());
            PlumImportResult builtResult = result.build();
            builtResult.addError(0, "Failed to read file: " + e.getMessage(), null);
            return builtResult;
        }
    }

    /**
     * Validate that required PLUM CSV headers are present.
     */
    private void validatePlumHeaders(String csvContent) {
        String headerLine = csvContent.lines().findFirst().orElse("");
        Set<String> headers = Arrays.stream(headerLine.split(","))
                .map(h -> h.trim().replace("\"", ""))
                .collect(java.util.stream.Collectors.toSet());

        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_PLUM_HEADERS) {
            if (!headers.contains(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException("PLUM CSV missing required headers: " + missing);
        }
    }

    /**
     * Download CSV content from URL.
     */
    private String downloadCsv() {
        try {
            log.debug("Downloading CSV from: {}", plumCsvUrl);
            return restTemplate.getForObject(plumCsvUrl, String.class);
        } catch (Exception e) {
            log.error("Failed to download PLUM CSV", e);
            return null;
        }
    }

    /**
     * Process the CSV content and import records (all records).
     */
    private PlumImportResult processCSV(String csvContent, PlumImportResult.PlumImportResultBuilder resultBuilder) {
        return processCSV(csvContent, resultBuilder, null, null);
    }

    /**
     * Process the CSV content and import records with optional offset/limit.
     *
     * @param csvContent The CSV content to process
     * @param resultBuilder Builder for the result
     * @param offset Number of records to skip (null = start from beginning)
     * @param limit Maximum number of records to process (null = process all remaining)
     */
    private PlumImportResult processCSV(String csvContent, PlumImportResult.PlumImportResultBuilder resultBuilder,
                                        Integer offset, Integer limit) {
        // Handle BOM if present
        if (csvContent.startsWith("\uFEFF")) {
            csvContent = csvContent.substring(1);
        }

        // Validate required headers before parsing
        validatePlumHeaders(csvContent);

        PlumImportResult result = resultBuilder.build();
        List<PlumCsvRecord> records;

        try (Reader reader = new StringReader(csvContent)) {
            CsvToBean<PlumCsvRecord> csvToBean = new CsvToBeanBuilder<PlumCsvRecord>(reader)
                    .withType(PlumCsvRecord.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .build();

            records = csvToBean.parse();
            result.setTotalRecords(records.size());
            log.info("Parsed {} PLUM records from CSV", records.size());

        } catch (Exception e) {
            log.error("Failed to parse PLUM CSV", e);
            result.addError(0, "CSV parsing failed: " + e.getMessage(), null);
            result.setEndTime(LocalDateTime.now());
            return result;
        }

        // Clear org cache for fresh import
        orgCache.clear();

        // Pre-warm org cache
        preWarmOrgCache();

        // Apply offset and limit
        int startIndex = (offset != null && offset > 0) ? offset : 0;
        int endIndex = records.size();
        if (limit != null && limit > 0) {
            endIndex = Math.min(startIndex + limit, records.size());
        }

        // Validate bounds
        if (startIndex >= records.size()) {
            log.warn("Offset {} exceeds total records {}, nothing to process", startIndex, records.size());
            result.setEndTime(LocalDateTime.now());
            return result;
        }

        log.info("Processing records {} to {} (of {} total)", startIndex, endIndex - 1, records.size());

        // Process records - each in its own transaction to prevent cascade failures
        for (int i = startIndex; i < endIndex; i++) {
            PlumCsvRecord record = records.get(i);
            final int lineNumber = i + 2; // +2 because: +1 for 0-based to 1-based, +1 for header row
            final PlumCsvRecord currentRecord = record;
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    try {
                        processRecord(currentRecord, lineNumber, result);
                    } catch (Exception e) {
                        // Mark transaction for rollback but don't propagate exception
                        status.setRollbackOnly();
                        throw new RuntimeException(e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                log.warn("Error processing record at line {}: {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage(), record.getPositionTitle());
            }

            // Log progress every 500 records
            if ((i - startIndex + 1) % 500 == 0) {
                log.info("Processed {} of {} records in this chunk", i - startIndex + 1, endIndex - startIndex);
            }
        }

        result.setEndTime(LocalDateTime.now());
        log.info(result.getSummary());
        log.info("Chunk complete: processed records {} to {} (of {} total)",
                startIndex, endIndex - 1, records.size());

        return result;
    }

    /**
     * Pre-warm the organization cache with all executive branch orgs.
     */
    private void preWarmOrgCache() {
        List<GovernmentOrganization> orgs = orgRepository.findByBranch(
                GovernmentOrganization.GovernmentBranch.EXECUTIVE);

        for (GovernmentOrganization org : orgs) {
            // Cache by official name
            if (org.getOfficialName() != null) {
                orgCache.put(normalizeAgencyName(org.getOfficialName()), org.getId());
            }
            // Cache by acronym
            if (org.getAcronym() != null) {
                orgCache.put(normalizeAgencyName(org.getAcronym()), org.getId());
            }
        }
        log.debug("Pre-warmed org cache with {} entries", orgCache.size());
    }

    /**
     * Process a single PLUM record.
     */
    private void processRecord(PlumCsvRecord record, int lineNumber, PlumImportResult result) {
        // Skip vacant positions without incumbent info
        if (record.isVacant()) {
            result.setVacantPositions(result.getVacantPositions() + 1);
            // Still create the position even if vacant
            UUID orgId = matchAgency(record.getAgencyName(), result);
            createOrUpdatePosition(record, orgId, result);
            return;
        }

        // Validate required fields for filled positions
        if (!record.hasIncumbent()) {
            result.setSkipped(result.getSkipped() + 1);
            log.debug("Skipping record at line {} - missing incumbent info", lineNumber);
            return;
        }

        // Match agency to organization
        UUID orgId = matchAgency(record.getAgencyName(), result);

        // Create or update position
        GovernmentPosition position = createOrUpdatePosition(record, orgId, result);

        // Create or update individual
        Individual individual = createOrUpdateIndividual(record, result);

        // Create or update position holding
        createOrUpdateHolding(individual, position, record, result);
    }

    /**
     * Match agency name to a GovernmentOrganization.
     */
    private UUID matchAgency(String agencyName, PlumImportResult result) {
        if (agencyName == null || agencyName.isBlank()) {
            return null;
        }

        String normalized = normalizeAgencyName(agencyName);

        // Check cache first
        if (orgCache.containsKey(normalized)) {
            return orgCache.get(normalized);
        }

        // Try exact match (case-insensitive)
        Optional<GovernmentOrganization> org = orgRepository.findByOfficialNameIgnoreCase(agencyName);
        if (org.isPresent()) {
            orgCache.put(normalized, org.get().getId());
            return org.get().getId();
        }

        // Try fuzzy search
        List<GovernmentOrganization> fuzzyMatches = orgRepository.fuzzySearch(agencyName);
        if (!fuzzyMatches.isEmpty()) {
            UUID matchedId = fuzzyMatches.get(0).getId();
            orgCache.put(normalized, matchedId);
            log.debug("Fuzzy matched agency '{}' to '{}'", agencyName, fuzzyMatches.get(0).getOfficialName());
            return matchedId;
        }

        // No match found
        result.addUnmatchedAgency(agencyName);
        log.debug("Unmatched agency: {}", agencyName);
        return null;
    }

    /**
     * Normalize agency name for cache lookup.
     */
    private String normalizeAgencyName(String name) {
        if (name == null) return "";
        return name.toLowerCase().trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "");
    }

    /**
     * Create or update a GovernmentPosition from PLUM record.
     */
    private GovernmentPosition createOrUpdatePosition(PlumCsvRecord record, UUID orgId, PlumImportResult result) {
        String title = record.getPositionTitle();
        if (title == null || title.isBlank()) {
            return null;
        }

        // Try to find existing position
        Optional<GovernmentPosition> existing = positionRepository
                .findByBranchAndTitleAndOrganizationId(Branch.EXECUTIVE, title, orgId);

        GovernmentPosition position;
        if (existing.isPresent()) {
            position = existing.get();
            // Update fields
            updatePositionFromRecord(position, record);
            position = positionRepository.save(position);
            result.setPositionsUpdated(result.getPositionsUpdated() + 1);
        } else {
            // Create new position
            position = GovernmentPosition.builder()
                    .title(title)
                    .branch(Branch.EXECUTIVE)
                    .positionType(determinePositionType(record))
                    .organizationId(orgId)
                    .appointmentType(parseAppointmentType(record.getAppointmentTypeDescription()))
                    .payPlan(record.getPayPlanCode())
                    .payGrade(record.getPayGrade())
                    .location(record.getLocation())
                    .expirationDate(parseDate(record.getExpirationDate(), "expirationDate"))
                    .build();
            position = positionRepository.save(position);
            result.setPositionsCreated(result.getPositionsCreated() + 1);
        }

        return position;
    }

    /**
     * Update position fields from PLUM record.
     */
    private void updatePositionFromRecord(GovernmentPosition position, PlumCsvRecord record) {
        position.setAppointmentType(parseAppointmentType(record.getAppointmentTypeDescription()));
        position.setPayPlan(record.getPayPlanCode());
        position.setPayGrade(record.getPayGrade());
        position.setLocation(record.getLocation());
        position.setExpirationDate(parseDate(record.getExpirationDate(), "expirationDate"));
    }

    /**
     * Determine PositionType from PLUM record.
     */
    private PositionType determinePositionType(PlumCsvRecord record) {
        String desc = record.getAppointmentTypeDescription();
        if (desc == null) {
            return PositionType.APPOINTED;
        }

        String lower = desc.toLowerCase();
        if (lower.contains("senate") || lower.contains("pas")) {
            return PositionType.APPOINTED; // PAS = Presidential Appointment with Senate Confirmation
        }
        if (lower.contains("career")) {
            return PositionType.CAREER;
        }
        if (lower.contains("schedule c") || lower.contains("xs")) {
            return PositionType.APPOINTED;
        }
        return PositionType.APPOINTED;
    }

    /**
     * Parse appointment type from description.
     */
    private AppointmentType parseAppointmentType(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return AppointmentType.fromCsvValue(description);
    }

    /**
     * Create or update an Individual from PLUM record.
     */
    private Individual createOrUpdateIndividual(PlumCsvRecord record, PlumImportResult result) {
        String firstName = record.getIncumbentFirstName() != null ? record.getIncumbentFirstName().trim() : "";
        String lastName = record.getIncumbentLastName() != null ? record.getIncumbentLastName().trim() : "";

        // Use IndividualService.findOrCreate for deduplication
        // PLUM data doesn't have birth dates, so we use name-only matching
        var existingOpt = individualService.findByFirstNameAndLastName(firstName, lastName);

        Individual individual;
        if (existingOpt.isPresent()) {
            individual = existingOpt.get();
            // No updates needed for PLUM - just name
            result.setIndividualsUpdated(result.getIndividualsUpdated() + 1);
        } else {
            // Check by case-insensitive name
            var matches = individualService.findByNameIgnoreCase(firstName, lastName);
            if (!matches.isEmpty()) {
                // Use the existing individual (might be from another source)
                individual = matches.get(0);
                result.setIndividualsUpdated(result.getIndividualsUpdated() + 1);
            } else {
                // Create new individual via findOrCreate
                individual = individualService.findOrCreate(
                        firstName,
                        lastName,
                        null, // birthDate - not provided in PLUM
                        DataSource.PLUM_CSV
                );
                result.setIndividualsCreated(result.getIndividualsCreated() + 1);
            }
        }

        return individual;
    }

    /**
     * Create or update a PositionHolding linking individual to position.
     */
    private void createOrUpdateHolding(Individual individual, GovernmentPosition position,
                                       PlumCsvRecord record, PlumImportResult result) {
        if (individual == null || position == null) {
            return;
        }

        // Try to find existing current holding
        Optional<PositionHolding> existing = holdingRepository
                .findCurrentByIndividualIdAndPositionId(individual.getId(), position.getId());

        PositionHolding holding;
        if (existing.isPresent()) {
            holding = existing.get();
            // Update tenure if changed
            Integer newTenure = record.getTenureCode();
            if (newTenure != null && !newTenure.equals(holding.getTenure())) {
                holding.setTenure(newTenure);
                holdingRepository.save(holding);
            }
            result.setHoldingsUpdated(result.getHoldingsUpdated() + 1);
        } else {
            // Create new holding
            LocalDate startDate = parseDate(record.getIncumbentBeginDate(), "incumbentBeginDate");
            if (startDate == null) {
                startDate = LocalDate.now(); // Default to today if not provided
            }
            LocalDate endDate = parseDate(record.getIncumbentVacateDate(), "incumbentVacateDate");

            // Guard against bad PLUM data where end_date precedes start_date
            if (endDate != null && endDate.isBefore(startDate)) {
                log.warn("PLUM data has endDate ({}) before startDate ({}) — swapping dates for individual {}",
                        endDate, startDate, individual.getId());
                LocalDate temp = startDate;
                startDate = endDate;
                endDate = temp;
            }

            holding = PositionHolding.builder()
                    .individualId(individual.getId())
                    .positionId(position.getId())
                    .startDate(startDate)
                    .endDate(endDate)
                    .tenure(record.getTenureCode())
                    .dataSource(DataSource.PLUM_CSV)
                    .sourceReference(plumCsvUrl)
                    .build();
            holdingRepository.save(holding);
            result.setHoldingsCreated(result.getHoldingsCreated() + 1);
        }
    }

    /**
     * Parse date from PLUM format (M/d/yyyy H:mm or M/d/yyyy).
     */
    private LocalDate parseDate(String dateStr) {
        return parseDate(dateStr, "unknown");
    }

    private LocalDate parseDate(String dateStr, String fieldName) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        dateStr = dateStr.trim();

        try {
            // Try with time component first
            return java.time.LocalDateTime.parse(dateStr, PLUM_DATE_FORMAT).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                // Try without time component
                return LocalDate.parse(dateStr, PLUM_DATE_FORMAT_ALT);
            } catch (DateTimeParseException e2) {
                log.warn("Failed to parse PLUM date field '{}': '{}'", fieldName, dateStr);
                return null;
            }
        }
    }

    /**
     * Get the current PLUM CSV URL.
     */
    public String getPlumCsvUrl() {
        return plumCsvUrl;
    }

    /**
     * Set the PLUM CSV URL (for testing).
     */
    public void setPlumCsvUrl(String url) {
        this.plumCsvUrl = url;
    }
}
