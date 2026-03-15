package org.newsanalyzer.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request DTO for updating individual (person) fields (KB-2.5).
 */
public record IndividualUpdateDTO(
        @NotBlank(message = "First name is required")
        String firstName,
        @NotBlank(message = "Last name is required")
        String lastName,
        LocalDate birthDate,
        LocalDate deathDate,
        String birthPlace,
        String imageUrl
) {}
