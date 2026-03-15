package org.newsanalyzer.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a new position holding (KB-2.5).
 */
public record PositionHoldingCreateDTO(
        @NotNull(message = "Individual ID is required")
        UUID individualId,
        @NotNull(message = "Position ID is required")
        UUID positionId,
        @NotNull(message = "Presidency ID is required")
        UUID presidencyId,
        @NotNull(message = "Start date is required")
        LocalDate startDate,
        LocalDate endDate
) {}
