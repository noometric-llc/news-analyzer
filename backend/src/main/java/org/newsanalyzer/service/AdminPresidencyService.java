package org.newsanalyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.IndividualUpdateDTO;
import org.newsanalyzer.dto.PositionHoldingCreateDTO;
import org.newsanalyzer.dto.PositionHoldingUpdateDTO;
import org.newsanalyzer.dto.PresidencyUpdateDTO;
import org.newsanalyzer.model.DataSource;
import org.newsanalyzer.model.Individual;
import org.newsanalyzer.model.PositionHolding;
import org.newsanalyzer.model.Presidency;
import org.newsanalyzer.model.PresidencyEndReason;
import org.newsanalyzer.repository.GovernmentPositionRepository;
import org.newsanalyzer.repository.IndividualRepository;
import org.newsanalyzer.repository.PositionHoldingRepository;
import org.newsanalyzer.repository.PresidencyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Admin service for CRUD operations on presidency-related entities (KB-2.5).
 *
 * Provides mutation endpoints for the admin UI to update presidency,
 * individual, and position holding data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPresidencyService {

    private final PresidencyRepository presidencyRepository;
    private final IndividualRepository individualRepository;
    private final PositionHoldingRepository positionHoldingRepository;
    private final GovernmentPositionRepository governmentPositionRepository;

    /**
     * Update presidency fields (AC1).
     */
    @Transactional
    public Presidency updatePresidency(UUID id, PresidencyUpdateDTO dto) {
        Presidency presidency = presidencyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Presidency not found: " + id));

        if (dto.party() != null) {
            presidency.setParty(dto.party());
        }
        if (dto.startDate() != null) {
            presidency.setStartDate(dto.startDate());
        }
        if (dto.endDate() != null) {
            presidency.setEndDate(dto.endDate());
        }
        if (dto.electionYear() != null) {
            presidency.setElectionYear(dto.electionYear());
        }
        if (dto.endReason() != null) {
            try {
                presidency.setEndReason(PresidencyEndReason.fromValue(dto.endReason()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid end reason: " + dto.endReason());
            }
        }

        Presidency saved = presidencyRepository.save(presidency);
        log.info("Updated presidency {} (#{}) — fields: party={}, startDate={}, endDate={}, electionYear={}, endReason={}",
                id, saved.getNumber(), dto.party(), dto.startDate(), dto.endDate(), dto.electionYear(), dto.endReason());
        return saved;
    }

    /**
     * Update individual fields (AC2).
     */
    @Transactional
    public Individual updateIndividual(UUID id, IndividualUpdateDTO dto) {
        Individual individual = individualRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Individual not found: " + id));

        if (dto.firstName() != null) {
            individual.setFirstName(dto.firstName());
        }
        if (dto.lastName() != null) {
            individual.setLastName(dto.lastName());
        }
        // These fields can be explicitly set to null (to clear them)
        individual.setBirthDate(dto.birthDate());
        individual.setDeathDate(dto.deathDate());
        individual.setBirthPlace(dto.birthPlace());
        individual.setImageUrl(dto.imageUrl());

        Individual saved = individualRepository.save(individual);
        log.info("Updated individual {} — name: {} {}", id, dto.firstName(), dto.lastName());
        return saved;
    }

    /**
     * Create a new position holding (AC3).
     * Validates that referenced individual, position, and presidency exist.
     */
    @Transactional
    public PositionHolding createPositionHolding(PositionHoldingCreateDTO dto) {
        // FK validation
        if (!individualRepository.existsById(dto.individualId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Individual not found: " + dto.individualId());
        }
        if (!governmentPositionRepository.existsById(dto.positionId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Government position not found: " + dto.positionId());
        }
        if (!presidencyRepository.existsById(dto.presidencyId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Presidency not found: " + dto.presidencyId());
        }

        PositionHolding holding = PositionHolding.builder()
                .individualId(dto.individualId())
                .positionId(dto.positionId())
                .presidencyId(dto.presidencyId())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .dataSource(DataSource.MANUAL)
                .build();

        PositionHolding saved = positionHoldingRepository.save(holding);
        log.info("Created position holding {} — individual={}, position={}, presidency={}",
                saved.getId(), dto.individualId(), dto.positionId(), dto.presidencyId());
        return saved;
    }

    /**
     * Update an existing position holding (AC4).
     */
    @Transactional
    public PositionHolding updatePositionHolding(UUID id, PositionHoldingUpdateDTO dto) {
        PositionHolding holding = positionHoldingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Position holding not found: " + id));

        if (dto.startDate() != null) {
            holding.setStartDate(dto.startDate());
        }
        if (dto.endDate() != null) {
            holding.setEndDate(dto.endDate());
        }

        PositionHolding saved = positionHoldingRepository.save(holding);
        log.info("Updated position holding {} — startDate={}, endDate={}", id, dto.startDate(), dto.endDate());
        return saved;
    }

    /**
     * Delete a position holding (AC5).
     */
    @Transactional
    public void deletePositionHolding(UUID id) {
        if (!positionHoldingRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Position holding not found: " + id);
        }
        positionHoldingRepository.deleteById(id);
        log.info("Deleted position holding {}", id);
    }
}
