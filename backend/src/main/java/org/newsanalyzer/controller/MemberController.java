package org.newsanalyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.newsanalyzer.dto.MemberDTO;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.CommitteeMembership;
import org.newsanalyzer.model.CongressionalMember;
import org.newsanalyzer.model.CongressionalMember.Chamber;
import org.newsanalyzer.model.PositionHolding;
import org.newsanalyzer.dto.SyncJobStatus;
import org.newsanalyzer.service.CommitteeService;
import org.newsanalyzer.service.MemberService;
import org.newsanalyzer.service.MemberSyncService;
import org.newsanalyzer.service.LegislatorsEnrichmentService;
import org.newsanalyzer.service.TermSyncService;
import org.newsanalyzer.service.SyncJobRegistry;
import org.newsanalyzer.service.SyncOrchestrator;
import org.newsanalyzer.scheduler.EnrichmentScheduler;
import org.newsanalyzer.repository.CongressionalMemberRepository;
import org.newsanalyzer.repository.PositionHoldingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Congressional Member lookup.
 *
 * Provides endpoints for:
 * - Listing all members (paginated)
 * - Looking up by BioGuide ID
 * - Searching by name
 * - Filtering by state or chamber
 * - Sync trigger (admin)
 *
 * Part of ARCH-1.6: Updated to use CongressionalMember instead of Person.
 *
 * Base Path: /api/members
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/members")
@Tag(name = "Members", description = "Congressional member lookup")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final MemberService memberService;
    private final MemberSyncService memberSyncService;
    private final CommitteeService committeeService;
    private final EnrichmentScheduler enrichmentScheduler;
    private final TermSyncService termSyncService;
    private final PositionHoldingRepository positionHoldingRepository;
    private final CongressionalMemberRepository congressionalMemberRepository;
    private final SyncJobRegistry registry;
    private final SyncOrchestrator orchestrator;

    public MemberController(MemberService memberService,
                           MemberSyncService memberSyncService,
                           CommitteeService committeeService,
                           EnrichmentScheduler enrichmentScheduler,
                           TermSyncService termSyncService,
                           PositionHoldingRepository positionHoldingRepository,
                           CongressionalMemberRepository congressionalMemberRepository,
                           SyncJobRegistry registry,
                           SyncOrchestrator orchestrator) {
        this.memberService = memberService;
        this.memberSyncService = memberSyncService;
        this.committeeService = committeeService;
        this.enrichmentScheduler = enrichmentScheduler;
        this.termSyncService = termSyncService;
        this.positionHoldingRepository = positionHoldingRepository;
        this.congressionalMemberRepository = congressionalMemberRepository;
        this.registry = registry;
        this.orchestrator = orchestrator;
    }

    // =====================================================================
    // List Endpoints
    // =====================================================================

    @GetMapping
    @Operation(summary = "List all members",
               description = "Get paginated list of all Congressional members")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of members returned")
    })
    public ResponseEntity<Page<MemberDTO>> listAll(Pageable pageable) {
        // Get all members with individuals eagerly loaded, then paginate
        List<CongressionalMember> allMembers = memberService.findAllWithIndividual();
        List<MemberDTO> dtos = allMembers.stream()
                .map(MemberDTO::from)
                .collect(Collectors.toList());

        // Apply pagination manually
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtos.size());
        List<MemberDTO> pageContent = start < dtos.size() ? dtos.subList(start, end) : List.of();

        Page<MemberDTO> page = new PageImpl<>(pageContent, pageable, dtos.size());
        return ResponseEntity.ok(page);
    }

    // =====================================================================
    // Lookup Endpoints
    // =====================================================================

    @GetMapping("/{bioguideId}")
    @Operation(summary = "Get member by BioGuide ID",
               description = "Retrieve a specific member by their Congress.gov BioGuide ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Member found"),
        @ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<MemberDTO> getByBioguideId(
            @Parameter(description = "BioGuide ID (e.g., S000033 for Bernie Sanders)")
            @PathVariable String bioguideId) {
        return memberService.findByBioguideIdWithIndividual(bioguideId)
                .map(MemberDTO::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + bioguideId));
    }

    // =====================================================================
    // Search Endpoints
    // =====================================================================

    @GetMapping("/search")
    @Operation(summary = "Search members by name",
               description = "Search for members by first or last name (case-insensitive)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search results returned")
    })
    public ResponseEntity<Page<MemberDTO>> searchByName(
            @Parameter(description = "Name to search for")
            @RequestParam String name,
            Pageable pageable) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Page<CongressionalMember> members = memberService.searchByName(name.trim(), pageable);
        // Convert to DTOs - need to load individuals
        List<MemberDTO> dtos = members.getContent().stream()
                .map(m -> congressionalMemberRepository.findByIdWithIndividual(m.getId()).orElse(m))
                .map(MemberDTO::from)
                .collect(Collectors.toList());
        Page<MemberDTO> dtoPage = new PageImpl<>(dtos, pageable, members.getTotalElements());
        return ResponseEntity.ok(dtoPage);
    }

    // =====================================================================
    // Filter Endpoints
    // =====================================================================

    @GetMapping("/by-state/{state}")
    @Operation(summary = "List members by state",
               description = "Get all members representing a specific state")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Members from state returned"),
        @ApiResponse(responseCode = "400", description = "Invalid state code")
    })
    public ResponseEntity<Page<MemberDTO>> getByState(
            @Parameter(description = "2-letter state code (e.g., CA, TX, NY)")
            @PathVariable String state,
            Pageable pageable) {
        if (state == null || state.length() != 2) {
            return ResponseEntity.badRequest().build();
        }
        // Use eager loading query
        List<CongressionalMember> members = congressionalMemberRepository.findByStateWithIndividual(state.toUpperCase());
        List<MemberDTO> dtos = members.stream()
                .map(MemberDTO::from)
                .collect(Collectors.toList());

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtos.size());
        List<MemberDTO> pageContent = start < dtos.size() ? dtos.subList(start, end) : List.of();

        Page<MemberDTO> page = new PageImpl<>(pageContent, pageable, dtos.size());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/by-chamber/{chamber}")
    @Operation(summary = "List members by chamber",
               description = "Get all members in a specific chamber (SENATE or HOUSE)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Members from chamber returned"),
        @ApiResponse(responseCode = "400", description = "Invalid chamber")
    })
    public ResponseEntity<Page<MemberDTO>> getByChamber(
            @Parameter(description = "Chamber: SENATE or HOUSE")
            @PathVariable String chamber,
            Pageable pageable) {
        try {
            Chamber chamberEnum = Chamber.valueOf(chamber.toUpperCase());
            // Use eager loading query
            List<CongressionalMember> members = congressionalMemberRepository.findByChamberWithIndividual(chamberEnum);
            List<MemberDTO> dtos = members.stream()
                    .map(MemberDTO::from)
                    .collect(Collectors.toList());

            // Apply pagination
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), dtos.size());
            List<MemberDTO> pageContent = start < dtos.size() ? dtos.subList(start, end) : List.of();

            Page<MemberDTO> page = new PageImpl<>(pageContent, pageable, dtos.size());
            return ResponseEntity.ok(page);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid chamber: {}", chamber);
            return ResponseEntity.badRequest().build();
        }
    }

    // =====================================================================
    // Statistics Endpoints
    // =====================================================================

    @GetMapping("/count")
    @Operation(summary = "Get total member count",
               description = "Returns the total number of members in the database")
    public ResponseEntity<Long> count() {
        return ResponseEntity.ok(memberService.count());
    }

    @GetMapping("/stats/party")
    @Operation(summary = "Get party distribution",
               description = "Returns count of members by party")
    public ResponseEntity<List<Object[]>> getPartyDistribution() {
        return ResponseEntity.ok(memberService.getPartyDistribution());
    }

    @GetMapping("/stats/state")
    @Operation(summary = "Get state distribution",
               description = "Returns count of members by state")
    public ResponseEntity<List<Object[]>> getStateDistribution() {
        return ResponseEntity.ok(memberService.getStateDistribution());
    }

    // =====================================================================
    // Cross-Reference Lookup Endpoints
    // =====================================================================

    @GetMapping("/by-external-id/{type}/{id}")
    @Operation(summary = "Find member by external ID",
               description = "Look up a member by an external ID type (fec, govtrack, opensecrets, votesmart)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Member found"),
        @ApiResponse(responseCode = "400", description = "Invalid ID type"),
        @ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<MemberDTO> getByExternalId(
            @Parameter(description = "External ID type: fec, govtrack, opensecrets, votesmart")
            @PathVariable String type,
            @Parameter(description = "External ID value")
            @PathVariable String id) {
        log.debug("Looking up member by external ID: type={}, id={}", type, id);

        return memberService.findByExternalId(type, id)
                .map(m -> congressionalMemberRepository.findByIdWithIndividual(m.getId()).orElse(m))
                .map(MemberDTO::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Member not found with %s ID: %s", type, id)));
    }

    // =====================================================================
    // Term History Endpoints
    // =====================================================================

    @GetMapping("/{bioguideId}/terms")
    @Operation(summary = "Get member's term history",
               description = "List all Congressional terms for a member")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Term list returned"),
        @ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<List<PositionHolding>> getMemberTerms(
            @Parameter(description = "BioGuide ID of the member")
            @PathVariable String bioguideId) {

        // Verify member exists and get their linked Individual ID
        CongressionalMember member = memberService.getByBioguideId(bioguideId);

        // Get position holdings by the linked Individual's ID
        List<PositionHolding> terms = positionHoldingRepository
                .findByIndividualIdOrderByStartDateDesc(member.getIndividualId());
        return ResponseEntity.ok(terms);
    }

    @GetMapping("/on-date/{date}")
    @Operation(summary = "List members in office on date",
               description = "Get all members who were serving in Congress on a specific date")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Members on date returned"),
        @ApiResponse(responseCode = "400", description = "Invalid date format (use YYYY-MM-DD)")
    })
    public ResponseEntity<Page<PositionHolding>> getMembersOnDate(
            @Parameter(description = "Date in YYYY-MM-DD format")
            @PathVariable String date,
            Pageable pageable) {
        try {
            LocalDate queryDate = LocalDate.parse(date);
            Page<PositionHolding> holdings = positionHoldingRepository
                    .findAllActiveOnDate(queryDate, pageable);
            return ResponseEntity.ok(holdings);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", date);
            return ResponseEntity.badRequest().build();
        }
    }

    // =====================================================================
    // Committee Endpoints
    // =====================================================================

    @GetMapping("/{bioguideId}/committees")
    @Operation(summary = "Get member's committee assignments",
               description = "List all committees a member serves on")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Committee list returned"),
        @ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<Page<CommitteeMembership>> getMemberCommittees(
            @Parameter(description = "BioGuide ID of the member")
            @PathVariable String bioguideId,
            @Parameter(description = "Congress session number (e.g., 118)")
            @RequestParam(required = false) Integer congress,
            Pageable pageable) {

        // Verify member exists
        memberService.getByBioguideId(bioguideId);

        Page<CommitteeMembership> committees = committeeService.findCommitteesForMember(bioguideId, pageable);
        return ResponseEntity.ok(committees);
    }

    // =====================================================================
    // Admin Endpoints
    // =====================================================================

    @PostMapping("/sync")
    @Operation(summary = "Trigger member sync (async)",
               description = "Starts an async sync of all current members from Congress.gov API. " +
                       "Returns immediately with a job ID. Poll GET /api/admin/sync/jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Sync started"),
        @ApiResponse(responseCode = "409", description = "Sync already in progress")
    })
    public ResponseEntity<SyncJobStatus> triggerSync() {
        if (registry.isRunning("members")) {
            return ResponseEntity.status(409).build();
        }
        log.info("Manual member sync triggered via API");
        SyncJobStatus status = registry.startJob("members");
        orchestrator.runMemberSync(status.getJobId());
        return ResponseEntity.accepted().body(status);
    }

    @PostMapping("/enrichment-sync")
    @Operation(summary = "Trigger enrichment sync (async)",
               description = "Starts an async sync from unitedstates/congress-legislators GitHub repo. " +
                       "Returns immediately with a job ID. Poll GET /api/admin/sync/jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Enrichment sync started"),
        @ApiResponse(responseCode = "409", description = "Enrichment sync already in progress")
    })
    public ResponseEntity<SyncJobStatus> triggerEnrichmentSync(
            @Parameter(description = "Force sync even if commit unchanged")
            @RequestParam(defaultValue = "false") boolean force) {
        if (registry.isRunning("enrichment")) {
            return ResponseEntity.status(409).build();
        }
        log.info("Manual enrichment sync triggered via API (force={})", force);
        SyncJobStatus status = registry.startJob("enrichment");
        orchestrator.runEnrichmentSync(status.getJobId(), force);
        return ResponseEntity.accepted().body(status);
    }

    @GetMapping("/enrichment-status")
    @Operation(summary = "Get enrichment sync status",
               description = "Get the status of the enrichment sync scheduler")
    public ResponseEntity<EnrichmentScheduler.SyncStatus> getEnrichmentStatus() {
        return ResponseEntity.ok(enrichmentScheduler.getStatus());
    }

    @PostMapping("/sync-terms")
    @Operation(summary = "Trigger term history sync (async)",
               description = "Starts an async sync of term history for all members from Congress.gov API. " +
                       "Returns immediately with a job ID. Poll GET /api/admin/sync/jobs/{jobId} for progress.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Term sync started"),
        @ApiResponse(responseCode = "409", description = "Term sync already in progress")
    })
    public ResponseEntity<SyncJobStatus> triggerTermSync() {
        if (registry.isRunning("terms")) {
            return ResponseEntity.status(409).build();
        }
        log.info("Manual term sync triggered via API");
        SyncJobStatus status = registry.startJob("terms");
        orchestrator.runTermSync(status.getJobId());
        return ResponseEntity.accepted().body(status);
    }
}
