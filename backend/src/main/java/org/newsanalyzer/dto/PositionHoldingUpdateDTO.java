package org.newsanalyzer.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for updating an existing position holding (KB-2.5).
 */
public record PositionHoldingUpdateDTO(
        @NotNull(message = "Start date is required")
        LocalDate startDate,
        LocalDate endDate
) {}
