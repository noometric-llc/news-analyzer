package org.newsanalyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.Committee;
import org.newsanalyzer.model.CommitteeChamber;
import org.newsanalyzer.model.CommitteeMembership;
import org.newsanalyzer.dto.SyncJobStatus;
import org.newsanalyzer.service.CommitteeService;
import org.newsanalyzer.service.CommitteeSyncService;
import org.newsanalyzer.service.CommitteeMembershipSyncService;
import org.newsanalyzer.service.SyncJobRegistry;
import org.newsanalyzer.service.SyncOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Congressional Committee lookup.
 *
 * Provides endpoints for:
 * - Listing all committees (paginated)
 * - Looking up by committee code
 * - Filtering by chamber
 * - Getting subcommittees
 * - Getting committee members
 * - Sync triggers (admin)
 *
 * Base Path: /api/committees
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/committees")
@Tag(name = "Committees", description = "Congressional committee lookup")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class CommitteeController {

    private static final Logger log = LoggerFactory.getLogger(CommitteeController.class);

    private final CommitteeService committeeService;
    private final CommitteeSyncService committeeSyncService;
    private final CommitteeMembershipSyncService membershipSyncService;
    private final SyncJobRegistry registry;
    private final SyncOrchestrator orchestrator;

    public CommitteeController(CommitteeService committeeService,
                               CommitteeSyncService committeeSyncService,
                               CommitteeMembershipSyncService membershipSyncService,
                               SyncJobRegistry registry,
                               SyncOrchestrator orchestrator) {
        this.committeeService = committeeService;
        this.committeeSyncService = committeeSyncService;
        this.membershipSyncService = membershipSyncService;
        this.registry = registry;
        this.orchestrator = orchestrator;
    }

    // =====================================================================
    // List Endpoints
    // =====================================================================

    @GetMapping
    @Operation(summary = "List all committees",
               description = "Get paginated list of all Congressional committees")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of committees returned")
    })
    public ResponseEntity<Page<Committee>> listAll(Pageable pageable) {
        Page<Committee> committees = committeeService.findAll(pageable);
        return ResponseEntity.ok(committees);
    }

    // =====================================================================
    // Lookup Endpoints
    // =====================================================================

    @GetMapping("/{code}")
    @Operation(summary = "Get committee by code",
               description = "Retrieve a specific committee by its system code (e.g., hsju00)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Committee found"),
        @ApiResponse(responseCode = "404", description = "Committee not found")
    })
    public ResponseEntity<Committee> getByCode(
            @Parameter(description = "Committee system code (e.g., hsju00 for House Judiciary)")
            @PathVariable String code) {
        return committeeService.findByCode(code)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Committee not found: " + code));
    }

    // =====================================================================
    // Members Endpoints
    // =====================================================================

    @GetMapping("/{code}/members")
    @Operation(summary = "Get committee members",
               description = "List all members serving on a committee")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Member list returned"),
        @ApiResponse(responseCode = "404", description = "Committee not found")
    })
    public ResponseEntity<Page<CommitteeMembership>> getMembers(
            @Parameter(description = "Committee system code")
            @PathVariable String code,
            @Parameter(description = "Congress session number (e.g., 118)")
            @RequestParam(required = false) Integer congress,
            Pageable pageable) {

        // Verify committee exists
        committeeService.getByCode(code);

        Page<CommitteeMembership> members;
        if (congress != null) {
            members = committeeService.findMembersByCongress(code, congress, pageable);
        } else {
            members = committeeService.findMembers(code, pageable);
        }
        return ResponseEntity.ok(members);
    }

    // =====================================================================
    // Subcommittee Endpoints
    // =====================================================================

    @GetMapping("/{code}/subcommittees")
    @Operation(summary = "Get subcommittees",
               description = "List all subcommittees under a parent committee")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Subcommittee list returned"),
        @ApiResponse(responseCode = "404", description = "Committee not found")
    })
    public ResponseEntity<Page<Committee>> getSubcommittees(
            @Parameter(description = "Parent committee system code")
            @PathVariable String code,
            Pageable pageable) {

        // Verify committee exists
        committeeService.getByCode(code);

        Page<Committee> subcommittees = committeeService.findSubcommittees(code, pageable);
        return ResponseEntity.ok(subcommittees);
    }

    // =====================================================================
    // Filter Endpoints
    // =====================================================================

    @GetMapping("/by-chamber/{chamber}")
    @Operation(summary = "List committees by chamber",
               description = "Get all committees in a specific chamber (SENATE, HOUSE, or JOINT)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Committees from chamber returned"),
        @ApiResponse(responseCode = "400", description = "Invalid chamber")
    })
    public ResponseEntity<Page<Committee>> getByChamber(
            @Parameter(description = "Chamber: SENATE, HOUSE, or JOINT")
            @PathVariable String chamber,
            Pageable pageable) {
        try {
            CommitteeChamber chamberEnum = CommitteeChamber.valueOf(chamber.toUpperCase());
            Page<Committee> committees = committeeService.findByChamber(chamberEnum, pageable);
            return ResponseEntity.ok(committees);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid chamber: {}", chamber);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Search committees by name",
               description = "Search for committees by name (case-insensitive)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search results returned")
    })
    public ResponseEntity<Page<Committee>> searchByName(
            @Parameter(description = "Name to search for")
            @RequestParam String name,
            Pageable pageable) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Page<Committee> committees = committeeService.searchByName(name.trim(), pageable);
        return ResponseEntity.ok(committees);
    }

    // =====================================================================
    // Statistics Endpoints
    // =====================================================================

    @GetMapping("/count")
    @Operation(summary = "Get total committee count",
               description = "Returns the total number of committees in the database")
    public ResponseEntity<Long> count() {
        return ResponseEntity.ok(committeeService.count());
    }

    @GetMapping("/stats/type")
    @Operation(summary = "Get type distribution",
               description = "Returns count of committees by type")
    public ResponseEntity<List<Object[]>> getTypeDistribution() {
        return ResponseEntity.ok(committeeService.getTypeDistribution());
    }

    @GetMapping("/stats/chamber")
    @Operation(summary = "Get chamber distribution",
               description = "Returns count of committees by chamber")
    public ResponseEntity<List<Object[]>> getChamberDistribution() {
        return ResponseEntity.ok(committeeService.getChamberDistribution());
    }

    // =====================================================================
    // Admin Endpoints
    // =====================================================================

    @PostMapping("/sync")
    @Operation(summary = "Trigger committee sync (async)",
               description = "Starts an async sync of all committees from Congress.gov API. " +
                       "Returns immediately with a job ID. Poll GET /api/admin/sync/jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Sync started"),
        @ApiResponse(responseCode = "409", description = "Sync already in progress")
    })
    public ResponseEntity<SyncJobStatus> triggerSync() {
        if (registry.isRunning("committees")) {
            return ResponseEntity.status(409).build();
        }
        log.info("Manual committee sync triggered via API");
        SyncJobStatus status = registry.startJob("committees");
        orchestrator.runCommitteeSync(status.getJobId());
        return ResponseEntity.accepted().body(status);
    }

    @PostMapping("/sync/memberships")
    @Operation(summary = "Trigger membership sync (async)",
               description = "Starts an async sync of all committee memberships from Congress.gov API. " +
                       "Returns immediately with a job ID. Poll GET /api/admin/sync/jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Sync started"),
        @ApiResponse(responseCode = "409", description = "Sync already in progress")
    })
    public ResponseEntity<SyncJobStatus> triggerMembershipSync(
            @Parameter(description = "Congress session number (e.g., 118)")
            @RequestParam(defaultValue = "118") int congress) {
        if (registry.isRunning("memberships")) {
            return ResponseEntity.status(409).build();
        }
        log.info("Manual membership sync triggered via API for Congress {}", congress);
        SyncJobStatus status = registry.startJob("memberships");
        orchestrator.runMembershipSync(status.getJobId(), congress);
        return ResponseEntity.accepted().body(status);
    }
}
