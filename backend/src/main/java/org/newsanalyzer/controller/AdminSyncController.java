package org.newsanalyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.newsanalyzer.dto.SyncJobStatus;
import org.newsanalyzer.scheduler.EnrichmentScheduler;
import org.newsanalyzer.scheduler.GovernmentOrgScheduler;
import org.newsanalyzer.scheduler.RegulationSyncScheduler;
import org.newsanalyzer.service.ExecutiveOrderSyncService;
import org.newsanalyzer.service.PlumCsvImportService;
import org.newsanalyzer.service.PresidentialSyncService;
import org.newsanalyzer.service.SyncJobRegistry;
import org.newsanalyzer.service.SyncOrchestrator;
import org.newsanalyzer.service.UsCodeImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for administrative data synchronization operations.
 *
 * All sync POST endpoints are now async — they return HTTP 202 Accepted with a
 * {@link SyncJobStatus} immediately. Clients poll GET /jobs/{jobId} for progress.
 *
 * Base Path: /api/admin/sync
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/admin/sync")
@Tag(name = "Admin Sync", description = "Administrative data synchronization operations")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class AdminSyncController {

    private static final Logger log = LoggerFactory.getLogger(AdminSyncController.class);

    private final SyncJobRegistry registry;
    private final SyncOrchestrator orchestrator;
    private final PlumCsvImportService plumImportService;
    private final UsCodeImportService usCodeImportService;
    private final PresidentialSyncService presidentialSyncService;
    private final ExecutiveOrderSyncService executiveOrderSyncService;

    // Optional scheduler dependencies (may be disabled via config)
    private final RegulationSyncScheduler regulationSyncScheduler;
    private final GovernmentOrgScheduler governmentOrgScheduler;
    private final EnrichmentScheduler enrichmentScheduler;

    public AdminSyncController(SyncJobRegistry registry,
                               SyncOrchestrator orchestrator,
                               PlumCsvImportService plumImportService,
                               UsCodeImportService usCodeImportService,
                               PresidentialSyncService presidentialSyncService,
                               ExecutiveOrderSyncService executiveOrderSyncService,
                               @org.springframework.beans.factory.annotation.Autowired(required = false)
                               RegulationSyncScheduler regulationSyncScheduler,
                               @org.springframework.beans.factory.annotation.Autowired(required = false)
                               GovernmentOrgScheduler governmentOrgScheduler,
                               @org.springframework.beans.factory.annotation.Autowired(required = false)
                               EnrichmentScheduler enrichmentScheduler) {
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.plumImportService = plumImportService;
        this.usCodeImportService = usCodeImportService;
        this.presidentialSyncService = presidentialSyncService;
        this.executiveOrderSyncService = executiveOrderSyncService;
        this.regulationSyncScheduler = regulationSyncScheduler;
        this.governmentOrgScheduler = governmentOrgScheduler;
        this.enrichmentScheduler = enrichmentScheduler;
    }

    // =====================================================================
    // Job Polling Endpoints
    // =====================================================================

    @GetMapping("/jobs")
    @Operation(summary = "List all sync jobs",
               description = "Get all tracked sync jobs (most recent first). Keeps last 20 completed jobs in memory.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job list returned")
    })
    public ResponseEntity<List<SyncJobStatus>> getAllJobs() {
        return ResponseEntity.ok(registry.getAllJobs());
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get sync job status",
               description = "Poll this endpoint to track progress of an async sync job")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job status returned"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    public ResponseEntity<SyncJobStatus> getJob(@PathVariable String jobId) {
        return registry.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =====================================================================
    // PLUM Import Endpoints
    // =====================================================================

    @PostMapping("/plum")
    @Operation(summary = "Import PLUM data from OPM (async)",
               description = "Starts an async import of executive branch appointee data from the OPM PLUM CSV file. " +
                       "Returns immediately with a job ID. Poll GET /jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Import started"),
        @ApiResponse(responseCode = "409", description = "Import already in progress")
    })
    public ResponseEntity<SyncJobStatus> importPlumData(
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        if (registry.isRunning("plum")) {
            return ResponseEntity.status(409).build();
        }

        log.info("PLUM import triggered via API (offset={}, limit={})", offset, limit);
        SyncJobStatus status = registry.startJob("plum");
        orchestrator.runPlumImport(status.getJobId(), offset, limit);
        return ResponseEntity.accepted().body(status);
    }

    @GetMapping("/plum/status")
    @Operation(summary = "Get PLUM import status",
               description = "Check if a PLUM import is currently running and get last import results")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status returned")
    })
    public ResponseEntity<Map<String, Object>> getPlumStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("inProgress", registry.isRunning("plum"));
        status.put("csvUrl", plumImportService.getPlumCsvUrl());

        // Find last completed plum job for backward-compatible status
        registry.getAllJobs().stream()
                .filter(j -> "plum".equals(j.getSyncType()) && j.getState() != SyncJobStatus.State.RUNNING)
                .findFirst()
                .ifPresent(j -> status.put("lastImport", j.getResult()));

        return ResponseEntity.ok(status);
    }

    @GetMapping("/plum/last-result")
    @Operation(summary = "Get last PLUM import result",
               description = "Returns the full result from the last PLUM import including error details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Last result returned"),
        @ApiResponse(responseCode = "404", description = "No previous import found")
    })
    public ResponseEntity<Object> getLastPlumResult() {
        return registry.getAllJobs().stream()
                .filter(j -> "plum".equals(j.getSyncType()) && j.getState() == SyncJobStatus.State.COMPLETED)
                .findFirst()
                .map(j -> ResponseEntity.ok(j.getResult()))
                .orElse(ResponseEntity.notFound().build());
    }

    // =====================================================================
    // US Code Import Endpoints
    // =====================================================================

    @PostMapping("/statutes")
    @Operation(summary = "Import all US Code titles (async)",
               description = "Starts an async import of all US Code titles from uscode.house.gov. " +
                       "Returns immediately with a job ID. Poll GET /jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Import started"),
        @ApiResponse(responseCode = "409", description = "Import already in progress")
    })
    public ResponseEntity<SyncJobStatus> importAllStatutes(
            @RequestParam(required = false) String releasePoint) {
        if (registry.isRunning("statutes")) {
            return ResponseEntity.status(409).build();
        }

        log.info("Full US Code import triggered via API (releasePoint: {})", releasePoint);
        SyncJobStatus status = registry.startJob("statutes");
        orchestrator.runUsCodeImportAll(status.getJobId(), releasePoint);
        return ResponseEntity.accepted().body(status);
    }

    @PostMapping("/statutes/{titleNumber}")
    @Operation(summary = "Import a specific US Code title (async)",
               description = "Starts an async import of a single US Code title from uscode.house.gov.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Import started"),
        @ApiResponse(responseCode = "409", description = "Import already in progress"),
        @ApiResponse(responseCode = "400", description = "Invalid title number")
    })
    public ResponseEntity<SyncJobStatus> importStatuteTitle(
            @PathVariable int titleNumber,
            @RequestParam(required = false) String releasePoint) {

        if (titleNumber < 1 || titleNumber > 54) {
            return ResponseEntity.badRequest().build();
        }

        if (registry.isRunning("statutes")) {
            return ResponseEntity.status(409).build();
        }

        log.info("US Code Title {} import triggered via API (releasePoint: {})", titleNumber, releasePoint);
        SyncJobStatus status = registry.startJob("statutes");
        orchestrator.runUsCodeImportTitle(status.getJobId(), titleNumber, releasePoint);
        return ResponseEntity.accepted().body(status);
    }

    @GetMapping("/statutes/status")
    @Operation(summary = "Get US Code import status",
               description = "Check if a US Code import is currently running and get last import results")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status returned")
    })
    public ResponseEntity<Map<String, Object>> getStatutesStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("inProgress", registry.isRunning("statutes"));
        status.put("totalStatutes", usCodeImportService.getTotalStatuteCount());
        status.put("usCodeStatutes", usCodeImportService.getUsCodeCount());

        registry.getAllJobs().stream()
                .filter(j -> "statutes".equals(j.getSyncType()) && j.getState() != SyncJobStatus.State.RUNNING)
                .findFirst()
                .ifPresent(j -> status.put("lastImport", j.getResult()));

        return ResponseEntity.ok(status);
    }

    @GetMapping("/statutes/last-result")
    @Operation(summary = "Get last US Code import result",
               description = "Returns the full result from the last US Code import including error details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Last result returned"),
        @ApiResponse(responseCode = "404", description = "No previous import found")
    })
    public ResponseEntity<Object> getLastStatutesResult() {
        return registry.getAllJobs().stream()
                .filter(j -> "statutes".equals(j.getSyncType()) && j.getState() == SyncJobStatus.State.COMPLETED)
                .findFirst()
                .map(j -> ResponseEntity.ok(j.getResult()))
                .orElse(ResponseEntity.notFound().build());
    }

    // =====================================================================
    // Presidential Data Sync Endpoints
    // =====================================================================

    @PostMapping("/presidencies")
    @Operation(summary = "Sync presidential data (async)",
               description = "Starts an async import of all 47 U.S. presidencies from seed data. " +
                       "Returns immediately with a job ID. Poll GET /jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Sync started"),
        @ApiResponse(responseCode = "409", description = "Sync already in progress")
    })
    public ResponseEntity<SyncJobStatus> syncPresidencies() {
        if (registry.isRunning("presidencies")) {
            return ResponseEntity.status(409).build();
        }

        log.info("Presidential sync triggered via API");
        SyncJobStatus status = registry.startJob("presidencies");
        orchestrator.runPresidentialSync(status.getJobId());
        return ResponseEntity.accepted().body(status);
    }

    @GetMapping("/presidencies/status")
    @Operation(summary = "Get presidential sync status",
               description = "Check if a presidential sync is currently running and get current data counts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status returned")
    })
    public ResponseEntity<Map<String, Object>> getPresidenciesStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("inProgress", registry.isRunning("presidencies"));
        status.put("totalPresidencies", presidentialSyncService.getPresidencyCount());

        registry.getAllJobs().stream()
                .filter(j -> "presidencies".equals(j.getSyncType()) && j.getState() != SyncJobStatus.State.RUNNING)
                .findFirst()
                .ifPresent(j -> status.put("lastSync", j.getResult()));

        return ResponseEntity.ok(status);
    }

    @GetMapping("/presidencies/last-result")
    @Operation(summary = "Get last presidential sync result",
               description = "Returns the full result from the last presidential sync including error details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Last result returned"),
        @ApiResponse(responseCode = "404", description = "No previous sync found")
    })
    public ResponseEntity<Object> getLastPresidenciesResult() {
        return registry.getAllJobs().stream()
                .filter(j -> "presidencies".equals(j.getSyncType()) && j.getState() == SyncJobStatus.State.COMPLETED)
                .findFirst()
                .map(j -> ResponseEntity.ok(j.getResult()))
                .orElse(ResponseEntity.notFound().build());
    }

    // =====================================================================
    // Executive Order Sync Endpoints
    // =====================================================================

    @PostMapping("/executive-orders")
    @Operation(summary = "Sync all Executive Orders (async)",
               description = "Starts an async fetch of Executive Order data for all presidencies. " +
                       "Returns immediately with a job ID. Poll GET /jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Sync started"),
        @ApiResponse(responseCode = "409", description = "Sync already in progress")
    })
    public ResponseEntity<SyncJobStatus> syncAllExecutiveOrders() {
        if (registry.isRunning("executive-orders")) {
            return ResponseEntity.status(409).build();
        }

        log.info("Executive Order sync triggered via API");
        SyncJobStatus status = registry.startJob("executive-orders");
        orchestrator.runExecutiveOrderSync(status.getJobId());
        return ResponseEntity.accepted().body(status);
    }

    @PostMapping("/executive-orders/{presidencyNumber}")
    @Operation(summary = "Sync Executive Orders for a specific presidency (async)",
               description = "Starts an async fetch of EO data for a specific presidency.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Sync started"),
        @ApiResponse(responseCode = "409", description = "Sync already in progress"),
        @ApiResponse(responseCode = "400", description = "Invalid presidency number")
    })
    public ResponseEntity<SyncJobStatus> syncExecutiveOrdersForPresidency(
            @PathVariable int presidencyNumber) {

        if (presidencyNumber < 1 || presidencyNumber > 47) {
            return ResponseEntity.badRequest().build();
        }

        if (registry.isRunning("executive-orders")) {
            return ResponseEntity.status(409).build();
        }

        log.info("Executive Order sync for presidency #{} triggered via API", presidencyNumber);
        SyncJobStatus status = registry.startJob("executive-orders");
        orchestrator.runExecutiveOrderSyncForPresidency(status.getJobId(), presidencyNumber);
        return ResponseEntity.accepted().body(status);
    }

    @GetMapping("/executive-orders/status")
    @Operation(summary = "Get Executive Order sync status",
               description = "Check if an EO sync is currently running and get last sync results")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status returned")
    })
    public ResponseEntity<Map<String, Object>> getExecutiveOrdersStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("inProgress", registry.isRunning("executive-orders"));
        status.put("eoCounts", executiveOrderSyncService.getExecutiveOrderCounts());

        registry.getAllJobs().stream()
                .filter(j -> "executive-orders".equals(j.getSyncType()) && j.getState() != SyncJobStatus.State.RUNNING)
                .findFirst()
                .ifPresent(j -> status.put("lastSync", j.getResult()));

        return ResponseEntity.ok(status);
    }

    @GetMapping("/executive-orders/last-result")
    @Operation(summary = "Get last Executive Order sync result",
               description = "Returns the full result from the last EO sync including error details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Last result returned"),
        @ApiResponse(responseCode = "404", description = "No previous sync found")
    })
    public ResponseEntity<Object> getLastExecutiveOrdersResult() {
        return registry.getAllJobs().stream()
                .filter(j -> "executive-orders".equals(j.getSyncType()) && j.getState() == SyncJobStatus.State.COMPLETED)
                .findFirst()
                .map(j -> ResponseEntity.ok(j.getResult()))
                .orElse(ResponseEntity.notFound().build());
    }

    // =====================================================================
    // General Admin Endpoints
    // =====================================================================

    @GetMapping("/health")
    @Operation(summary = "Sync service health check",
               description = "Verify the sync service is operational")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        Map<String, Object> services = new HashMap<>();
        services.put("plumImport", Map.of(
                "available", true,
                "inProgress", registry.isRunning("plum"),
                "csvUrl", plumImportService.getPlumCsvUrl()
        ));
        services.put("usCodeImport", Map.of(
                "available", true,
                "inProgress", registry.isRunning("statutes"),
                "totalStatutes", usCodeImportService.getTotalStatuteCount()
        ));
        services.put("presidentialSync", Map.of(
                "available", true,
                "inProgress", registry.isRunning("presidencies"),
                "totalPresidencies", presidentialSyncService.getPresidencyCount()
        ));
        services.put("executiveOrderSync", Map.of(
                "available", true,
                "inProgress", registry.isRunning("executive-orders")
        ));

        // Scheduled sync health
        Map<String, Object> scheduledSyncs = new HashMap<>();
        if (regulationSyncScheduler != null) {
            Map<String, Object> regSync = new HashMap<>();
            regSync.put("enabled", true);
            regSync.put("lastRun", regulationSyncScheduler.getLastSyncTime());
            regSync.put("lastStatus", regulationSyncScheduler.getLastSyncStatus());
            regSync.put("lastError", regulationSyncScheduler.getLastSyncError());
            scheduledSyncs.put("regulationSync", regSync);
        } else {
            scheduledSyncs.put("regulationSync", Map.of("enabled", false));
        }
        if (governmentOrgScheduler != null) {
            Map<String, Object> govSync = new HashMap<>();
            govSync.put("enabled", true);
            govSync.put("lastRun", governmentOrgScheduler.getLastScheduledSyncTime());
            var result = governmentOrgScheduler.getLastSyncResult();
            govSync.put("lastStatus", result != null ? "COMPLETED" : null);
            govSync.put("lastErrors", result != null ? result.getErrors() : null);
            scheduledSyncs.put("govOrgSync", govSync);
        } else {
            scheduledSyncs.put("govOrgSync", Map.of("enabled", false));
        }
        if (enrichmentScheduler != null) {
            var status = enrichmentScheduler.getStatus();
            Map<String, Object> enrSync = new HashMap<>();
            enrSync.put("enabled", true);
            enrSync.put("lastRun", status.lastSyncTime());
            enrSync.put("lastCommit", status.lastCommitSha());
            enrSync.put("schedule", status.schedule());
            scheduledSyncs.put("enrichmentSync", enrSync);
        } else {
            scheduledSyncs.put("enrichmentSync", Map.of("enabled", false));
        }
        health.put("scheduledSyncs", scheduledSyncs);

        health.put("services", services);
        return ResponseEntity.ok(health);
    }
}
