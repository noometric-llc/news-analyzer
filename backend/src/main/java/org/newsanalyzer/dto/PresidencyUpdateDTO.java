package org.newsanalyzer.dto;

import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * Request DTO for updating presidency fields (KB-2.5).
 * All fields are optional — only non-null fields are applied.
 */
public record PresidencyUpdateDTO(
        String party,
        @PastOrPresent(message = "Start date cannot be in the future")
        LocalDate startDate,
        LocalDate endDate,
        Integer electionYear,
        String endReason
) {}
