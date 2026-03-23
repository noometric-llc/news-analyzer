package org.newsanalyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.JudgeDTO;
import org.newsanalyzer.model.*;
import org.newsanalyzer.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for querying federal judge data.
 *
 * Provides methods to search and filter judges by court, circuit, status, etc.
 *
 * Part of ARCH-1.6: Updated to use Individual instead of Person.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JudgeService {

    private final IndividualRepository individualRepository;
    private final GovernmentPositionRepository positionRepository;
    private final PositionHoldingRepository holdingRepository;
    private final GovernmentOrganizationRepository orgRepository;

    /**
     * Find all current judges (active or senior status).
     */
    public Page<JudgeDTO> findCurrentJudges(Pageable pageable) {
        // Find all current judicial holdings
        Page<PositionHolding> holdings = holdingRepository.findByDataSource(DataSource.FJC, pageable);

        List<JudgeDTO> judges = holdings.getContent().stream()
                .filter(h -> h.getEndDate() == null) // Current judges only
                .map(this::toJudgeDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(judges, pageable, holdings.getTotalElements());
    }

    /**
     * Find all judges with optional filters.
     */
    public Page<JudgeDTO> findJudges(String courtLevel, String circuit, String status,
                                      String search, Pageable pageable) {
        // Get all FJC holdings - use unpaged query to avoid sorting on PositionHolding
        // Sorting is applied after mapping to JudgeDTO since lastName is on Person, not PositionHolding
        List<PositionHolding> allHoldings = holdingRepository.findByDataSource(DataSource.FJC);

        List<JudgeDTO> allJudges = allHoldings.stream()
                .map(this::toJudgeDTO)
                .filter(Objects::nonNull)
                .filter(j -> matchesFilters(j, courtLevel, circuit, status, search))
                .collect(Collectors.toList());

        // Apply sorting based on pageable sort
        Comparator<JudgeDTO> comparator = getComparator(pageable.getSort());
        if (comparator != null) {
            allJudges.sort(comparator);
        }

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allJudges.size());

        List<JudgeDTO> pageContent = start < allJudges.size()
                ? allJudges.subList(start, end)
                : Collections.emptyList();

        return new PageImpl<>(pageContent, pageable, allJudges.size());
    }

    /**
     * Build comparator from Sort specification.
     */
    private Comparator<JudgeDTO> getComparator(org.springframework.data.domain.Sort sort) {
        if (sort.isUnsorted()) {
            return Comparator.comparing(JudgeDTO::getLastName, Comparator.nullsLast(String::compareToIgnoreCase));
        }

        Comparator<JudgeDTO> comparator = null;

        for (org.springframework.data.domain.Sort.Order order : sort) {
            Comparator<JudgeDTO> fieldComparator = getFieldComparator(order.getProperty());
            if (fieldComparator != null) {
                if (order.isDescending()) {
                    fieldComparator = fieldComparator.reversed();
                }
                comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
            }
        }

        return comparator;
    }

    private Comparator<JudgeDTO> getFieldComparator(String field) {
        switch (field) {
            case "lastName":
                return Comparator.comparing(JudgeDTO::getLastName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "firstName":
                return Comparator.comparing(JudgeDTO::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "fullName":
                return Comparator.comparing(JudgeDTO::getFullName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "courtName":
                return Comparator.comparing(JudgeDTO::getCourtName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "commissionDate":
                return Comparator.comparing(JudgeDTO::getCommissionDate, Comparator.nullsLast(LocalDate::compareTo));
            default:
                return Comparator.comparing(JudgeDTO::getLastName, Comparator.nullsLast(String::compareToIgnoreCase));
        }
    }

    /**
     * Find judge by ID.
     */
    public Optional<JudgeDTO> findById(UUID id) {
        return individualRepository.findById(id)
                .filter(i -> i.getPrimaryDataSource() == DataSource.FJC)
                .map(this::individualToJudgeDTO);
    }

    /**
     * Search judges by name.
     */
    public List<JudgeDTO> searchByName(String query) {
        return individualRepository.searchByName(query).stream()
                .filter(i -> i.getPrimaryDataSource() == DataSource.FJC)
                .map(this::individualToJudgeDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get statistics about judges.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalFjcIndividuals = individualRepository.countByPrimaryDataSource(DataSource.FJC);
        long totalFjcHoldings = holdingRepository.countByDataSource(DataSource.FJC);

        // Count current holdings (no end date)
        List<PositionHolding> fjcHoldings = holdingRepository.findByDataSource(DataSource.FJC);
        long currentJudges = fjcHoldings.stream()
                .filter(h -> h.getEndDate() == null)
                .count();

        stats.put("totalJudges", totalFjcIndividuals);
        stats.put("totalAppointments", totalFjcHoldings);
        stats.put("currentJudges", currentJudges);

        return stats;
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private JudgeDTO toJudgeDTO(PositionHolding holding) {
        if (holding == null) return null;

        Optional<Individual> individualOpt = individualRepository.findById(holding.getIndividualId());
        Optional<GovernmentPosition> positionOpt = positionRepository.findById(holding.getPositionId());

        if (individualOpt.isEmpty()) return null;

        Individual individual = individualOpt.get();
        GovernmentPosition position = positionOpt.orElse(null);
        GovernmentOrganization court = null;

        if (position != null && position.getOrganizationId() != null) {
            court = orgRepository.findById(position.getOrganizationId()).orElse(null);
        }

        return buildJudgeDTO(individual, position, holding, court);
    }

    private JudgeDTO individualToJudgeDTO(Individual individual) {
        if (individual == null) return null;

        // Find the most recent holding for this individual
        List<PositionHolding> holdings = holdingRepository.findByIndividualIdOrderByStartDateDesc(individual.getId());
        PositionHolding holding = holdings.isEmpty() ? null : holdings.get(0);

        GovernmentPosition position = null;
        GovernmentOrganization court = null;

        if (holding != null) {
            position = positionRepository.findById(holding.getPositionId()).orElse(null);
            if (position != null && position.getOrganizationId() != null) {
                court = orgRepository.findById(position.getOrganizationId()).orElse(null);
            }
        }

        return buildJudgeDTO(individual, position, holding, court);
    }

    private JudgeDTO buildJudgeDTO(Individual individual, GovernmentPosition position,
                                    PositionHolding holding, GovernmentOrganization court) {
        JudgeDTO.JudgeDTOBuilder builder = JudgeDTO.builder()
                .id(individual.getId())
                .firstName(individual.getFirstName())
                .middleName(individual.getMiddleName())
                .lastName(individual.getLastName())
                .suffix(individual.getSuffix())
                .fullName(individual.getFullName())
                .gender(individual.getGender())
                .birthDate(individual.getBirthDate());

        if (court != null) {
            builder.courtName(court.getOfficialName())
                   .courtOrganizationId(court.getId())
                   .courtType(determineCourtType(court.getOfficialName()));
        }

        if (holding != null) {
            builder.commissionDate(holding.getStartDate())
                   .terminationDate(holding.getEndDate())
                   .seniorStatusDate(holding.getSeniorStatusDate())
                   .current(holding.isCurrent())
                   .appointingPresident(holding.getAppointingPresident())
                   .partyOfAppointingPresident(holding.getPartyOfAppointingPresident())
                   .abaRating(holding.getAbaRating())
                   .nominationDate(holding.getNominationDate())
                   .confirmationDate(holding.getConfirmationDate());

            // Determine status using senior status date
            String status = determineStatus(holding.getSeniorStatusDate(), holding.getEndDate());
            builder.judicialStatus(status);
        }

        return builder.build();
    }

    private String determineCourtType(String courtName) {
        if (courtName == null) return null;
        String lower = courtName.toLowerCase();
        if (lower.contains("supreme")) return "Supreme Court";
        if (lower.contains("court of appeals") || lower.contains("circuit")) return "Court of Appeals";
        if (lower.contains("district")) return "District Court";
        if (lower.contains("bankruptcy")) return "Bankruptcy Court";
        if (lower.contains("international trade")) return "Court of International Trade";
        if (lower.contains("federal claims")) return "Court of Federal Claims";
        if (lower.contains("tax")) return "Tax Court";
        return "Other";
    }

    private String determineStatus(LocalDate seniorStatusDate, LocalDate endDate) {
        // If terminated, they're former
        if (endDate != null && !endDate.isAfter(LocalDate.now())) {
            return "FORMER";
        }
        // If has senior status date in the past, they're senior
        if (seniorStatusDate != null && !seniorStatusDate.isAfter(LocalDate.now())) {
            return "SENIOR";
        }
        // Otherwise active
        return "ACTIVE";
    }

    private boolean matchesFilters(JudgeDTO judge, String courtLevel, String circuit,
                                    String status, String search) {
        // Filter by court level
        if (courtLevel != null && !courtLevel.isEmpty()) {
            if (judge.getCourtType() == null) return false;
            if (!judge.getCourtType().toLowerCase().contains(courtLevel.toLowerCase())) {
                return false;
            }
        }

        // Filter by circuit
        if (circuit != null && !circuit.isEmpty()) {
            if (judge.getCourtName() == null) return false;
            if (!judge.getCourtName().toLowerCase().contains(circuit.toLowerCase())) {
                return false;
            }
        }

        // Filter by status
        if (status != null && !status.isEmpty()) {
            if (!status.equalsIgnoreCase("ALL")) {
                String judgeStatus = judge.getJudicialStatus();
                if (judgeStatus == null || !judgeStatus.equalsIgnoreCase(status)) {
                    return false;
                }
            }
        }

        // Filter by search term
        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            boolean matches = false;
            if (judge.getFullName() != null && judge.getFullName().toLowerCase().contains(searchLower)) {
                matches = true;
            }
            if (judge.getCourtName() != null && judge.getCourtName().toLowerCase().contains(searchLower)) {
                matches = true;
            }
            if (!matches) return false;
        }

        return true;
    }
}
