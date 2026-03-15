package org.newsanalyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.IndividualUpdateDTO;
import org.newsanalyzer.dto.PositionHoldingCreateDTO;
import org.newsanalyzer.dto.PositionHoldingUpdateDTO;
import org.newsanalyzer.dto.PresidencyUpdateDTO;
import org.newsanalyzer.model.Individual;
import org.newsanalyzer.model.PositionHolding;
import org.newsanalyzer.model.Presidency;
import org.newsanalyzer.service.AdminPresidencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin CRUD endpoints for presidency-related data (KB-2.5).
 *
 * All endpoints under /api/admin/ path prefix. Auth enforcement deferred —
 * Spring Security is currently disabled. Path convention preserves future auth boundary.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Presidency CRUD", description = "Create, update, and delete presidency data")
public class AdminPresidencyController {

    private final AdminPresidencyService adminPresidencyService;

    // =====================================================================
    // Presidency Endpoints
    // =====================================================================

    @PutMapping("/presidencies/{id}")
    @Operation(summary = "Update a presidency",
               description = "Updates editable fields of a presidency record")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Presidency updated"),
        @ApiResponse(responseCode = "404", description = "Presidency not found"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<Presidency> updatePresidency(
            @PathVariable UUID id,
            @Valid @RequestBody PresidencyUpdateDTO dto) {
        Presidency updated = adminPresidencyService.updatePresidency(id, dto);
        return ResponseEntity.ok(updated);
    }

    // =====================================================================
    // Individual Endpoints
    // =====================================================================

    @PutMapping("/individuals/{id}")
    @Operation(summary = "Update an individual",
               description = "Updates biographical fields of an individual record")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Individual updated"),
        @ApiResponse(responseCode = "404", description = "Individual not found"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<Individual> updateIndividual(
            @PathVariable UUID id,
            @Valid @RequestBody IndividualUpdateDTO dto) {
        Individual updated = adminPresidencyService.updateIndividual(id, dto);
        return ResponseEntity.ok(updated);
    }

    // =====================================================================
    // Position Holding Endpoints
    // =====================================================================

    @PostMapping("/position-holdings")
    @Operation(summary = "Create a position holding",
               description = "Creates a new position holding linking an individual to a government position")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Position holding created"),
        @ApiResponse(responseCode = "400", description = "Validation error or FK not found")
    })
    public ResponseEntity<PositionHolding> createPositionHolding(
            @Valid @RequestBody PositionHoldingCreateDTO dto) {
        PositionHolding created = adminPresidencyService.createPositionHolding(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/position-holdings/{id}")
    @Operation(summary = "Update a position holding",
               description = "Updates dates of an existing position holding")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Position holding updated"),
        @ApiResponse(responseCode = "404", description = "Position holding not found"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<PositionHolding> updatePositionHolding(
            @PathVariable UUID id,
            @Valid @RequestBody PositionHoldingUpdateDTO dto) {
        PositionHolding updated = adminPresidencyService.updatePositionHolding(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/position-holdings/{id}")
    @Operation(summary = "Delete a position holding",
               description = "Removes a position holding record")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Position holding deleted"),
        @ApiResponse(responseCode = "404", description = "Position holding not found")
    })
    public ResponseEntity<Void> deletePositionHolding(@PathVariable UUID id) {
        adminPresidencyService.deletePositionHolding(id);
        return ResponseEntity.noContent().build();
    }
}
