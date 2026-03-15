package org.newsanalyzer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminPresidencyService (KB-2.5).
 */
@ExtendWith(MockitoExtension.class)
class AdminPresidencyServiceTest {

    @Mock
    private PresidencyRepository presidencyRepository;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private PositionHoldingRepository positionHoldingRepository;

    @Mock
    private GovernmentPositionRepository governmentPositionRepository;

    @InjectMocks
    private AdminPresidencyService adminPresidencyService;

    // =====================================================================
    // updatePresidency
    // =====================================================================

    @Nested
    @DisplayName("updatePresidency")
    class UpdatePresidencyTests {

        @Test
        @DisplayName("Should update presidency fields")
        void updatesFields() {
            UUID id = UUID.randomUUID();
            Presidency existing = Presidency.builder()
                    .id(id)
                    .number(45)
                    .party("Republican")
                    .startDate(LocalDate.of(2017, 1, 20))
                    .endDate(LocalDate.of(2021, 1, 20))
                    .individualId(UUID.randomUUID())
                    .dataSource(DataSource.WHITE_HOUSE_HISTORICAL)
                    .build();

            when(presidencyRepository.findById(id)).thenReturn(Optional.of(existing));
            when(presidencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PresidencyUpdateDTO dto = new PresidencyUpdateDTO(
                    "Democrat", null, null, null, "term_end");

            Presidency result = adminPresidencyService.updatePresidency(id, dto);

            assertThat(result.getParty()).isEqualTo("Democrat");
            assertThat(result.getEndReason()).isEqualTo(PresidencyEndReason.TERM_END);
            // Unchanged fields preserved
            assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2017, 1, 20));
            verify(presidencyRepository).save(existing);
        }

        @Test
        @DisplayName("Should throw NOT_FOUND when presidency doesn't exist")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(presidencyRepository.findById(id)).thenReturn(Optional.empty());

            PresidencyUpdateDTO dto = new PresidencyUpdateDTO("Republican", null, null, null, null);

            assertThatThrownBy(() -> adminPresidencyService.updatePresidency(id, dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Presidency not found");
        }

        @Test
        @DisplayName("Should throw BAD_REQUEST for invalid end reason")
        void invalidEndReason() {
            UUID id = UUID.randomUUID();
            Presidency existing = Presidency.builder()
                    .id(id).number(1).individualId(UUID.randomUUID())
                    .startDate(LocalDate.of(1789, 4, 30))
                    .dataSource(DataSource.WHITE_HOUSE_HISTORICAL)
                    .build();

            when(presidencyRepository.findById(id)).thenReturn(Optional.of(existing));

            PresidencyUpdateDTO dto = new PresidencyUpdateDTO(null, null, null, null, "invalid_reason");

            assertThatThrownBy(() -> adminPresidencyService.updatePresidency(id, dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Invalid end reason");
        }
    }

    // =====================================================================
    // updateIndividual
    // =====================================================================

    @Nested
    @DisplayName("updateIndividual")
    class UpdateIndividualTests {

        @Test
        @DisplayName("Should update individual fields")
        void updatesFields() {
            UUID id = UUID.randomUUID();
            Individual existing = Individual.builder()
                    .id(id)
                    .firstName("Donald")
                    .lastName("Trump")
                    .build();

            when(individualRepository.findById(id)).thenReturn(Optional.of(existing));
            when(individualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IndividualUpdateDTO dto = new IndividualUpdateDTO(
                    "Donald", "J. Trump", LocalDate.of(1946, 6, 14),
                    null, "Queens, New York", "https://example.com/photo.jpg");

            Individual result = adminPresidencyService.updateIndividual(id, dto);

            assertThat(result.getLastName()).isEqualTo("J. Trump");
            assertThat(result.getBirthPlace()).isEqualTo("Queens, New York");
            assertThat(result.getImageUrl()).isEqualTo("https://example.com/photo.jpg");
            verify(individualRepository).save(existing);
        }

        @Test
        @DisplayName("Should throw NOT_FOUND when individual doesn't exist")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(individualRepository.findById(id)).thenReturn(Optional.empty());

            IndividualUpdateDTO dto = new IndividualUpdateDTO("John", "Doe", null, null, null, null);

            assertThatThrownBy(() -> adminPresidencyService.updateIndividual(id, dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Individual not found");
        }
    }

    // =====================================================================
    // createPositionHolding
    // =====================================================================

    @Nested
    @DisplayName("createPositionHolding")
    class CreatePositionHoldingTests {

        @Test
        @DisplayName("Should create position holding when all FKs valid")
        void createsHolding() {
            UUID indId = UUID.randomUUID();
            UUID posId = UUID.randomUUID();
            UUID presId = UUID.randomUUID();

            when(individualRepository.existsById(indId)).thenReturn(true);
            when(governmentPositionRepository.existsById(posId)).thenReturn(true);
            when(presidencyRepository.existsById(presId)).thenReturn(true);
            when(positionHoldingRepository.save(any())).thenAnswer(inv -> {
                PositionHolding ph = inv.getArgument(0);
                ph.setId(UUID.randomUUID());
                return ph;
            });

            PositionHoldingCreateDTO dto = new PositionHoldingCreateDTO(
                    indId, posId, presId, LocalDate.of(2025, 1, 20), null);

            PositionHolding result = adminPresidencyService.createPositionHolding(dto);

            assertThat(result.getIndividualId()).isEqualTo(indId);
            assertThat(result.getPositionId()).isEqualTo(posId);
            assertThat(result.getPresidencyId()).isEqualTo(presId);
            assertThat(result.getDataSource()).isEqualTo(DataSource.MANUAL);
            verify(positionHoldingRepository).save(any());
        }

        @Test
        @DisplayName("Should throw BAD_REQUEST when individual doesn't exist")
        void invalidIndividual() {
            UUID indId = UUID.randomUUID();
            when(individualRepository.existsById(indId)).thenReturn(false);

            PositionHoldingCreateDTO dto = new PositionHoldingCreateDTO(
                    indId, UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.of(2025, 1, 20), null);

            assertThatThrownBy(() -> adminPresidencyService.createPositionHolding(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Individual not found");
        }

        @Test
        @DisplayName("Should throw BAD_REQUEST when position doesn't exist")
        void invalidPosition() {
            UUID indId = UUID.randomUUID();
            UUID posId = UUID.randomUUID();

            when(individualRepository.existsById(indId)).thenReturn(true);
            when(governmentPositionRepository.existsById(posId)).thenReturn(false);

            PositionHoldingCreateDTO dto = new PositionHoldingCreateDTO(
                    indId, posId, UUID.randomUUID(), LocalDate.of(2025, 1, 20), null);

            assertThatThrownBy(() -> adminPresidencyService.createPositionHolding(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Government position not found");
        }

        @Test
        @DisplayName("Should throw BAD_REQUEST when presidency doesn't exist")
        void invalidPresidency() {
            UUID indId = UUID.randomUUID();
            UUID posId = UUID.randomUUID();
            UUID presId = UUID.randomUUID();

            when(individualRepository.existsById(indId)).thenReturn(true);
            when(governmentPositionRepository.existsById(posId)).thenReturn(true);
            when(presidencyRepository.existsById(presId)).thenReturn(false);

            PositionHoldingCreateDTO dto = new PositionHoldingCreateDTO(
                    indId, posId, presId, LocalDate.of(2025, 1, 20), null);

            assertThatThrownBy(() -> adminPresidencyService.createPositionHolding(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Presidency not found");
        }
    }

    // =====================================================================
    // updatePositionHolding
    // =====================================================================

    @Nested
    @DisplayName("updatePositionHolding")
    class UpdatePositionHoldingTests {

        @Test
        @DisplayName("Should update position holding dates")
        void updatesDates() {
            UUID id = UUID.randomUUID();
            PositionHolding existing = PositionHolding.builder()
                    .id(id)
                    .individualId(UUID.randomUUID())
                    .positionId(UUID.randomUUID())
                    .startDate(LocalDate.of(2025, 1, 20))
                    .dataSource(DataSource.MANUAL)
                    .build();

            when(positionHoldingRepository.findById(id)).thenReturn(Optional.of(existing));
            when(positionHoldingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PositionHoldingUpdateDTO dto = new PositionHoldingUpdateDTO(
                    LocalDate.of(2025, 1, 20), LocalDate.of(2029, 1, 20));

            PositionHolding result = adminPresidencyService.updatePositionHolding(id, dto);

            assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2029, 1, 20));
            verify(positionHoldingRepository).save(existing);
        }

        @Test
        @DisplayName("Should throw NOT_FOUND when holding doesn't exist")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(positionHoldingRepository.findById(id)).thenReturn(Optional.empty());

            PositionHoldingUpdateDTO dto = new PositionHoldingUpdateDTO(
                    LocalDate.of(2025, 1, 20), null);

            assertThatThrownBy(() -> adminPresidencyService.updatePositionHolding(id, dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Position holding not found");
        }
    }

    // =====================================================================
    // deletePositionHolding
    // =====================================================================

    @Nested
    @DisplayName("deletePositionHolding")
    class DeletePositionHoldingTests {

        @Test
        @DisplayName("Should delete position holding")
        void deletesHolding() {
            UUID id = UUID.randomUUID();
            when(positionHoldingRepository.existsById(id)).thenReturn(true);

            adminPresidencyService.deletePositionHolding(id);

            verify(positionHoldingRepository).deleteById(id);
        }

        @Test
        @DisplayName("Should throw NOT_FOUND when holding doesn't exist")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(positionHoldingRepository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> adminPresidencyService.deletePositionHolding(id))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Position holding not found");
        }
    }
}
