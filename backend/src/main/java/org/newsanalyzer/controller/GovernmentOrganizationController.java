package org.newsanalyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.CsvImportResult;
import org.newsanalyzer.dto.SyncJobStatus;
import org.newsanalyzer.model.GovernmentOrganization;
import org.newsanalyzer.model.GovernmentOrganization.GovernmentBranch;
import org.newsanalyzer.model.GovernmentOrganization.OrganizationType;
import org.newsanalyzer.service.GovOrgCsvImportService;
import org.newsanalyzer.service.GovernmentOrganizationService;
import org.newsanalyzer.service.GovernmentOrgSyncService;
import org.newsanalyzer.service.SyncJobRegistry;
import org.newsanalyzer.service.SyncOrchestrator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Government Organization management.
 *
 * Provides endpoints for:
 * - CRUD operations on government organizations
 * - Search and filtering (exact, fuzzy, full-text)
 * - Hierarchy navigation (parents, children, ancestry)
 * - Entity validation against official data
 * - Statistics and reporting
 *
 * Base Path: /api/government-organizations
 *
 * @author Winston (Architect Agent)
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/government-organizations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Government Organizations", description = "Manage US Government organizational structure")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class GovernmentOrganizationController {

    private final GovernmentOrganizationService service;
    private final GovernmentOrgSyncService syncService;
    private final GovOrgCsvImportService csvImportService;
    private final SyncJobRegistry registry;
    private final SyncOrchestrator orchestrator;

    // =====================================================================
    // CRUD Endpoints
    // =====================================================================

    @GetMapping
    @Operation(summary = "List all government organizations",
               description = "Get paginated list of all government organizations")
    public ResponseEntity<Page<GovernmentOrganization>> listAll(Pageable pageable) {
        Page<GovernmentOrganization> organizations = service.findAll(pageable);
        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/active")
    @Operation(summary = "List active government organizations",
               description = "Get all currently active (not dissolved) government organizations")
    public ResponseEntity<List<GovernmentOrganization>> listActive() {
        List<GovernmentOrganization> organizations = service.findAllActive();
        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID",
               description = "Retrieve a specific government organization by UUID")
    public ResponseEntity<GovernmentOrganization> getById(@PathVariable UUID id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new organization",
               description = "Create a new government organization (admin only)")
    public ResponseEntity<GovernmentOrganization> create(
            @Valid @RequestBody GovernmentOrganization organization) {
        try {
            GovernmentOrganization created = service.create(organization);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating organization: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update organization",
               description = "Update an existing government organization (admin only)")
    public ResponseEntity<GovernmentOrganization> update(
            @PathVariable UUID id,
            @Valid @RequestBody GovernmentOrganization organization) {
        // ResourceNotFoundException is handled by GlobalExceptionHandler -> 404
        // IllegalArgumentException is handled by GlobalExceptionHandler -> 400
        GovernmentOrganization updated = service.update(id, organization);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete organization",
               description = "Soft delete an organization by setting dissolved date (admin only)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        // ResourceNotFoundException is handled by GlobalExceptionHandler -> 404
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // Search Endpoints
    // =====================================================================

    @GetMapping("/search")
    @Operation(summary = "Search organizations",
               description = "Search by name or acronym (LIKE query)")
    public ResponseEntity<List<GovernmentOrganization>> search(
            @RequestParam String query) {
        List<GovernmentOrganization> results = service.search(query);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/fuzzy")
    @Operation(summary = "Fuzzy search organizations",
               description = "Search using trigram similarity for typo tolerance")
    public ResponseEntity<List<GovernmentOrganization>> fuzzySearch(
            @RequestParam String query) {
        List<GovernmentOrganization> results = service.fuzzySearch(query);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/fulltext")
    @Operation(summary = "Full-text search organizations",
               description = "Search across name, acronym, mission, and description")
    public ResponseEntity<List<GovernmentOrganization>> fullTextSearch(
            @RequestParam String query) {
        List<GovernmentOrganization> results = service.fullTextSearch(query);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/find")
    @Operation(summary = "Find by name or acronym",
               description = "Find exact match by official name or acronym")
    public ResponseEntity<GovernmentOrganization> findByNameOrAcronym(
            @RequestParam String nameOrAcronym) {
        return service.findByNameOrAcronym(nameOrAcronym)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =====================================================================
    // Filter Endpoints
    // =====================================================================

    @GetMapping("/cabinet-departments")
    @Operation(summary = "Get Cabinet departments",
               description = "Get the 15 Cabinet-level executive departments")
    public ResponseEntity<List<GovernmentOrganization>> getCabinetDepartments() {
        List<GovernmentOrganization> departments = service.getCabinetDepartments();
        return ResponseEntity.ok(departments);
    }

    @GetMapping("/independent-agencies")
    @Operation(summary = "Get independent agencies",
               description = "Get independent executive agencies (EPA, NASA, etc.)")
    public ResponseEntity<List<GovernmentOrganization>> getIndependentAgencies() {
        List<GovernmentOrganization> agencies = service.getIndependentAgencies();
        return ResponseEntity.ok(agencies);
    }

    @GetMapping("/by-type")
    @Operation(summary = "Get organizations by type",
               description = "Filter organizations by type (department, agency, bureau, etc.)")
    public ResponseEntity<List<GovernmentOrganization>> getByType(
            @RequestParam String type) {
        OrganizationType orgType = OrganizationType.fromValue(type);
        List<GovernmentOrganization> organizations = service.findByType(orgType);
        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/by-branch")
    @Operation(summary = "Get organizations by branch",
               description = "Filter organizations by government branch (executive, legislative, judicial)")
    public ResponseEntity<List<GovernmentOrganization>> getByBranch(
            @RequestParam String branch) {
        GovernmentBranch govBranch = GovernmentBranch.fromValue(branch);
        List<GovernmentOrganization> organizations = service.findByBranch(govBranch);
        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/by-jurisdiction")
    @Operation(summary = "Get organizations by jurisdiction",
               description = "Find organizations with specific jurisdiction area")
    public ResponseEntity<List<GovernmentOrganization>> getByJurisdiction(
            @RequestParam String jurisdiction) {
        List<GovernmentOrganization> organizations = service.findByJurisdiction(jurisdiction);
        return ResponseEntity.ok(organizations);
    }

    // =====================================================================
    // Hierarchy Endpoints
    // =====================================================================

    @GetMapping("/{id}/hierarchy")
    @Operation(summary = "Get organization hierarchy",
               description = "Get full hierarchy including ancestors (parents) and children")
    public ResponseEntity<GovernmentOrganizationService.OrganizationHierarchy> getHierarchy(
            @PathVariable UUID id) {
        // ResourceNotFoundException is handled by GlobalExceptionHandler -> 404
        GovernmentOrganizationService.OrganizationHierarchy hierarchy = service.getHierarchy(id);
        return ResponseEntity.ok(hierarchy);
    }

    @GetMapping("/{id}/descendants")
    @Operation(summary = "Get all descendants",
               description = "Get all child organizations recursively")
    public ResponseEntity<List<GovernmentOrganization>> getDescendants(
            @PathVariable UUID id) {
        List<GovernmentOrganization> descendants = service.getDescendants(id);
        return ResponseEntity.ok(descendants);
    }

    @GetMapping("/{id}/ancestors")
    @Operation(summary = "Get all ancestors",
               description = "Get all parent organizations up to top level")
    public ResponseEntity<List<GovernmentOrganization>> getAncestors(
            @PathVariable UUID id) {
        List<GovernmentOrganization> ancestors = service.getAncestors(id);
        return ResponseEntity.ok(ancestors);
    }

    @GetMapping("/top-level")
    @Operation(summary = "Get top-level organizations",
               description = "Get organizations without parent (Cabinet, independent agencies)")
    public ResponseEntity<List<GovernmentOrganization>> getTopLevel() {
        List<GovernmentOrganization> topLevel = service.getTopLevelOrganizations();
        return ResponseEntity.ok(topLevel);
    }

    // =====================================================================
    // Validation Endpoints
    // =====================================================================

    @PostMapping("/validate-entity")
    @Operation(summary = "Validate entity",
               description = "Validate if entity text matches official government organization")
    public ResponseEntity<GovernmentOrganizationService.EntityValidationResult> validateEntity(
            @RequestBody EntityValidationRequest request) {
        GovernmentOrganizationService.EntityValidationResult result =
                service.validateEntity(request.entityText, request.entityType);
        return ResponseEntity.ok(result);
    }

    // =====================================================================
    // Statistics Endpoints
    // =====================================================================

    @GetMapping("/statistics")
    @Operation(summary = "Get organization statistics",
               description = "Get counts by type, branch, and overall statistics")
    public ResponseEntity<GovernmentOrganizationService.OrganizationStatistics> getStatistics() {
        GovernmentOrganizationService.OrganizationStatistics stats = service.getStatistics();
        return ResponseEntity.ok(stats);
    }

    // =====================================================================
    // Sync Endpoints
    // =====================================================================

    @PostMapping("/sync/federal-register")
    @Operation(summary = "Sync from Federal Register API (async)",
               description = "Starts an async sync of government organizations from Federal Register API. " +
                       "Returns immediately with a job ID. Poll GET /api/admin/sync/jobs/{jobId} for progress.")
    public ResponseEntity<SyncJobStatus> syncFromFederalRegister() {
        if (registry.isRunning("gov-orgs")) {
            return ResponseEntity.status(409).build();
        }
        log.info("Manual sync from Federal Register API triggered");
        SyncJobStatus status = registry.startJob("gov-orgs");
        orchestrator.runGovOrgSync(status.getJobId());
        return ResponseEntity.accepted().body(status);
    }

    @GetMapping("/sync/status")
    @Operation(summary = "Get sync status",
               description = "Get current sync status including last sync time, organization counts, and API availability")
    public ResponseEntity<GovernmentOrgSyncService.SyncStatus> getSyncStatus() {
        GovernmentOrgSyncService.SyncStatus status = syncService.getStatus();
        return ResponseEntity.ok(status);
    }

    // =====================================================================
    // Import Endpoints
    // =====================================================================

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import organizations from CSV",
               description = "Import government organizations from a CSV file. " +
                       "Supports Legislative and Judicial branches. " +
                       "Uses merge strategy: match by acronym first, then by name.")
    public ResponseEntity<CsvImportResult> importFromCsv(
            @RequestParam("file") MultipartFile file) {
        log.info("CSV import requested, filename: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        // Validate file
        if (file.isEmpty()) {
            CsvImportResult errorResult = CsvImportResult.builder()
                    .success(false)
                    .build();
            errorResult.addError("File is empty");
            return ResponseEntity.badRequest().body(errorResult);
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.contains("csv") && !contentType.contains("text")) {
            CsvImportResult errorResult = CsvImportResult.builder()
                    .success(false)
                    .build();
            errorResult.addError("Invalid file type. Expected CSV file");
            return ResponseEntity.badRequest().body(errorResult);
        }

        try {
            CsvImportResult result = csvImportService.importFromCsv(file.getInputStream());

            if (result.hasValidationErrors()) {
                // Return 400 for validation errors
                return ResponseEntity.badRequest().body(result);
            }

            log.info("CSV import completed: {}", result);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", e.getMessage(), e);
            CsvImportResult errorResult = CsvImportResult.builder()
                    .success(false)
                    .build();
            errorResult.addError("Failed to read uploaded file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        } catch (Exception e) {
            log.error("CSV import failed: {}", e.getMessage(), e);
            CsvImportResult errorResult = CsvImportResult.builder()
                    .success(false)
                    .build();
            errorResult.addError("Import failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    /**
     * Request DTO for entity validation
     */
    public record EntityValidationRequest(
            String entityText,
            String entityType,
            String context
    ) {}
}
