package org.newsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.newsanalyzer.model.CongressionalMember;
import org.newsanalyzer.model.Individual;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Congressional Member API responses.
 *
 * Flattens Individual (biographical) + CongressionalMember (Congress-specific) data
 * into a single DTO for backward-compatible API responses.
 *
 * Part of ARCH-1.7: Update DTOs and Controllers
 *
 * @author James (Dev Agent)
 * @since 3.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDTO {

    private UUID id;

    // =====================================================================
    // From Individual (biographical data)
    // =====================================================================

    private String firstName;
    private String lastName;
    private String middleName;
    private String suffix;
    private String fullName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deathDate;

    private String birthPlace;
    private String gender;
    private String imageUrl;
    private boolean isLiving;

    // =====================================================================
    // From CongressionalMember (Congress-specific data)
    // =====================================================================

    private String bioguideId;
    private String chamber;
    private String state;
    private String party;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime congressLastSync;

    private String enrichmentSource;
    private String enrichmentVersion;
    private String dataSource;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // =====================================================================
    // Factory Methods
    // =====================================================================

    /**
     * Create MemberDTO from CongressionalMember with eagerly loaded Individual.
     *
     * @param member the congressional member entity (with individual loaded)
     * @return the DTO with flattened data
     */
    public static MemberDTO from(CongressionalMember member) {
        if (member == null) return null;

        Individual individual = member.getIndividual();

        return MemberDTO.builder()
                .id(member.getId())
                // Individual fields
                .firstName(individual != null ? individual.getFirstName() : null)
                .lastName(individual != null ? individual.getLastName() : null)
                .middleName(individual != null ? individual.getMiddleName() : null)
                .suffix(individual != null ? individual.getSuffix() : null)
                .fullName(individual != null ? individual.getFullName() : null)
                .birthDate(individual != null ? individual.getBirthDate() : null)
                .deathDate(individual != null ? individual.getDeathDate() : null)
                .birthPlace(individual != null ? individual.getBirthPlace() : null)
                .gender(individual != null ? individual.getGender() : null)
                .imageUrl(individual != null ? individual.getImageUrl() : null)
                .isLiving(individual != null && individual.isLiving())
                // CongressionalMember fields
                .bioguideId(member.getBioguideId())
                .chamber(member.getChamber() != null ? member.getChamber().name() : null)
                .state(member.getState())
                .party(member.getParty())
                .congressLastSync(member.getCongressLastSync())
                .enrichmentSource(member.getEnrichmentSource())
                .enrichmentVersion(member.getEnrichmentVersion())
                .dataSource(member.getDataSource() != null ? member.getDataSource().name() : null)
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    /**
     * Create MemberDTO from CongressionalMember and separate Individual.
     *
     * @param member the congressional member entity
     * @param individual the individual entity
     * @return the DTO with flattened data
     */
    public static MemberDTO from(CongressionalMember member, Individual individual) {
        if (member == null) return null;

        return MemberDTO.builder()
                .id(member.getId())
                // Individual fields
                .firstName(individual != null ? individual.getFirstName() : null)
                .lastName(individual != null ? individual.getLastName() : null)
                .middleName(individual != null ? individual.getMiddleName() : null)
                .suffix(individual != null ? individual.getSuffix() : null)
                .fullName(individual != null ? individual.getFullName() : null)
                .birthDate(individual != null ? individual.getBirthDate() : null)
                .deathDate(individual != null ? individual.getDeathDate() : null)
                .birthPlace(individual != null ? individual.getBirthPlace() : null)
                .gender(individual != null ? individual.getGender() : null)
                .imageUrl(individual != null ? individual.getImageUrl() : null)
                .isLiving(individual != null && individual.isLiving())
                // CongressionalMember fields
                .bioguideId(member.getBioguideId())
                .chamber(member.getChamber() != null ? member.getChamber().name() : null)
                .state(member.getState())
                .party(member.getParty())
                .congressLastSync(member.getCongressLastSync())
                .enrichmentSource(member.getEnrichmentSource())
                .enrichmentVersion(member.getEnrichmentVersion())
                .dataSource(member.getDataSource() != null ? member.getDataSource().name() : null)
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }
}
