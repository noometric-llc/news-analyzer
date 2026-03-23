package org.newsanalyzer.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a person holding a government position for a specific time period.
 *
 * This is a temporal join table that tracks who held what position when.
 * Enables historical queries like "who was in Congress on date X".
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@jakarta.persistence.Entity
@Table(name = "position_holdings",
        indexes = {
                @Index(name = "idx_holding_individual", columnList = "individual_id"),
                @Index(name = "idx_holding_position", columnList = "position_id"),
                @Index(name = "idx_holding_dates", columnList = "start_date, end_date"),
                @Index(name = "idx_holding_congress", columnList = "congress"),
                @Index(name = "idx_holding_presidency", columnList = "presidency_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // =====================================================================
    // Relationships
    // =====================================================================

    @Column(name = "individual_id", nullable = false)
    @NotNull(message = "Individual ID is required")
    private UUID individualId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_id", insertable = false, updatable = false)
    @JsonIgnore
    private Individual individual;

    @Column(name = "position_id", nullable = false)
    @NotNull(message = "Position ID is required")
    private UUID positionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", insertable = false, updatable = false)
    @JsonIgnore
    private GovernmentPosition position;

    /**
     * Optional link to a specific presidency.
     * Used for executive branch appointments (VP, Cabinet, CoS) to track
     * which presidential administration the position was held under.
     * Enables queries like "all VPs during the 37th presidency".
     */
    @Column(name = "presidency_id")
    private UUID presidencyId;

    // =====================================================================
    // Temporal Information
    // =====================================================================

    @Column(name = "start_date", nullable = false)
    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Column(name = "end_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Column(name = "senior_status_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate seniorStatusDate;

    @Column(name = "congress")
    @Min(value = 1, message = "Congress number must be positive")
    private Integer congress;

    /**
     * Tenure code from PLUM data (executive branch positions).
     * Indicates the type of employment tenure.
     */
    @Column(name = "tenure")
    private Integer tenure;

    // =====================================================================
    // Appointment Metadata (Judicial)
    // =====================================================================

    @Column(name = "appointing_president", length = 100)
    private String appointingPresident;

    @Column(name = "party_of_appointing_president", length = 50)
    private String partyOfAppointingPresident;

    @Column(name = "aba_rating", length = 50)
    private String abaRating;

    @Column(name = "nomination_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nominationDate;

    @Column(name = "confirmation_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate confirmationDate;

    // =====================================================================
    // Data Source Tracking
    // =====================================================================

    @Column(name = "data_source", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Data source is required")
    private DataSource dataSource;

    @Column(name = "source_reference", length = 200)
    @Size(max = 200, message = "Source reference must be less than 200 characters")
    private String sourceReference;

    // =====================================================================
    // Audit Fields
    // =====================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // =====================================================================
    // Lifecycle Callbacks
    // =====================================================================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (dataSource == null) {
            dataSource = DataSource.CONGRESS_GOV;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    /**
     * Check if this holding is currently active (no end date or end date in future)
     */
    public boolean isCurrent() {
        return endDate == null || endDate.isAfter(LocalDate.now());
    }

    /**
     * Check if position was held on a specific date
     */
    public boolean wasHeldOn(LocalDate date) {
        if (date == null) {
            return false;
        }
        boolean afterStart = !date.isBefore(startDate);
        boolean beforeEnd = endDate == null || !date.isAfter(endDate);
        return afterStart && beforeEnd;
    }

    /**
     * Get duration in days (null if still current)
     */
    public Long getDurationDays() {
        if (endDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * Get term label (e.g., "118th Congress (2023-2025)")
     */
    public String getTermLabel() {
        if (congress != null) {
            String suffix = getOrdinalSuffix(congress);
            if (endDate != null) {
                return String.format("%d%s Congress (%d-%d)", congress, suffix,
                        startDate.getYear(), endDate.getYear());
            } else {
                return String.format("%d%s Congress (%d-present)", congress, suffix,
                        startDate.getYear());
            }
        }
        return String.format("%d-%s", startDate.getYear(),
                endDate != null ? String.valueOf(endDate.getYear()) : "present");
    }

    private String getOrdinalSuffix(int n) {
        if (n >= 11 && n <= 13) {
            return "th";
        }
        switch (n % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }
}
