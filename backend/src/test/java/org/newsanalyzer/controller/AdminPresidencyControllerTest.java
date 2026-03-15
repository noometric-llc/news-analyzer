package org.newsanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.dto.IndividualUpdateDTO;
import org.newsanalyzer.dto.PositionHoldingCreateDTO;
import org.newsanalyzer.dto.PositionHoldingUpdateDTO;
import org.newsanalyzer.dto.PresidencyUpdateDTO;
import org.newsanalyzer.model.DataSource;
import org.newsanalyzer.model.Individual;
import org.newsanalyzer.model.PositionHolding;
import org.newsanalyzer.model.Presidency;
import org.newsanalyzer.service.AdminPresidencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for AdminPresidencyController (KB-2.5).
 */
@WebMvcTest(AdminPresidencyController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminPresidencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminPresidencyService adminPresidencyService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private UUID presidencyId;
    private UUID individualId;
    private UUID holdingId;
    private UUID positionId;

    @BeforeEach
    void setUp() {
        presidencyId = UUID.randomUUID();
        individualId = UUID.randomUUID();
        holdingId = UUID.randomUUID();
        positionId = UUID.randomUUID();
    }

    // =====================================================================
    // PUT /api/admin/presidencies/{id}
    // =====================================================================

    @Nested
    @DisplayName("PUT /api/admin/presidencies/{id}")
    class UpdatePresidencyTests {

        @Test
        @DisplayName("Should update presidency and return 200")
        void updatePresidency_returns200() throws Exception {
            PresidencyUpdateDTO dto = new PresidencyUpdateDTO(
                    "Republican", LocalDate.of(2025, 1, 20), null, 2024, null);

            Presidency updated = Presidency.builder()
                    .id(presidencyId)
                    .number(47)
                    .party("Republican")
                    .startDate(LocalDate.of(2025, 1, 20))
                    .individualId(individualId)
                    .dataSource(DataSource.WHITE_HOUSE_HISTORICAL)
                    .build();

            when(adminPresidencyService.updatePresidency(eq(presidencyId), any()))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/admin/presidencies/{id}", presidencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.number", is(47)))
                    .andExpect(jsonPath("$.party", is("Republican")));
        }

        @Test
        @DisplayName("Should return 404 for unknown presidency ID")
        void updatePresidency_notFound() throws Exception {
            PresidencyUpdateDTO dto = new PresidencyUpdateDTO(
                    "Republican", null, null, null, null);

            when(adminPresidencyService.updatePresidency(eq(presidencyId), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Presidency not found"));

            mockMvc.perform(put("/api/admin/presidencies/{id}", presidencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    // PUT /api/admin/individuals/{id}
    // =====================================================================

    @Nested
    @DisplayName("PUT /api/admin/individuals/{id}")
    class UpdateIndividualTests {

        @Test
        @DisplayName("Should update individual and return 200")
        void updateIndividual_returns200() throws Exception {
            IndividualUpdateDTO dto = new IndividualUpdateDTO(
                    "Donald", "Trump", LocalDate.of(1946, 6, 14),
                    null, "Queens, New York", null);

            Individual updated = Individual.builder()
                    .id(individualId)
                    .firstName("Donald")
                    .lastName("Trump")
                    .birthDate(LocalDate.of(1946, 6, 14))
                    .birthPlace("Queens, New York")
                    .build();

            when(adminPresidencyService.updateIndividual(eq(individualId), any()))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/admin/individuals/{id}", individualId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName", is("Donald")))
                    .andExpect(jsonPath("$.lastName", is("Trump")));
        }

        @Test
        @DisplayName("Should return 404 for unknown individual ID")
        void updateIndividual_notFound() throws Exception {
            IndividualUpdateDTO dto = new IndividualUpdateDTO(
                    "John", "Doe", null, null, null, null);

            when(adminPresidencyService.updateIndividual(eq(individualId), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Individual not found"));

            mockMvc.perform(put("/api/admin/individuals/{id}", individualId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 for blank first name")
        void updateIndividual_validationError() throws Exception {
            // firstName is @NotBlank
            String json = "{\"firstName\":\"\",\"lastName\":\"Trump\"}";

            mockMvc.perform(put("/api/admin/individuals/{id}", individualId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // POST /api/admin/position-holdings
    // =====================================================================

    @Nested
    @DisplayName("POST /api/admin/position-holdings")
    class CreatePositionHoldingTests {

        @Test
        @DisplayName("Should create position holding and return 201")
        void createPositionHolding_returns201() throws Exception {
            PositionHoldingCreateDTO dto = new PositionHoldingCreateDTO(
                    individualId, positionId, presidencyId,
                    LocalDate.of(2025, 1, 20), null);

            PositionHolding created = PositionHolding.builder()
                    .id(holdingId)
                    .individualId(individualId)
                    .positionId(positionId)
                    .presidencyId(presidencyId)
                    .startDate(LocalDate.of(2025, 1, 20))
                    .dataSource(DataSource.MANUAL)
                    .build();

            when(adminPresidencyService.createPositionHolding(any()))
                    .thenReturn(created);

            mockMvc.perform(post("/api/admin/position-holdings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(holdingId.toString())));
        }

        @Test
        @DisplayName("Should return 400 for invalid FK")
        void createPositionHolding_invalidFK() throws Exception {
            PositionHoldingCreateDTO dto = new PositionHoldingCreateDTO(
                    individualId, positionId, presidencyId,
                    LocalDate.of(2025, 1, 20), null);

            when(adminPresidencyService.createPositionHolding(any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Individual not found"));

            mockMvc.perform(post("/api/admin/position-holdings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for missing required fields")
        void createPositionHolding_validationError() throws Exception {
            // Missing individualId
            String json = "{\"positionId\":\"" + positionId + "\",\"presidencyId\":\"" + presidencyId + "\",\"startDate\":\"2025-01-20\"}";

            mockMvc.perform(post("/api/admin/position-holdings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // PUT /api/admin/position-holdings/{id}
    // =====================================================================

    @Nested
    @DisplayName("PUT /api/admin/position-holdings/{id}")
    class UpdatePositionHoldingTests {

        @Test
        @DisplayName("Should update position holding and return 200")
        void updatePositionHolding_returns200() throws Exception {
            PositionHoldingUpdateDTO dto = new PositionHoldingUpdateDTO(
                    LocalDate.of(2025, 1, 20), LocalDate.of(2029, 1, 20));

            PositionHolding updated = PositionHolding.builder()
                    .id(holdingId)
                    .individualId(individualId)
                    .positionId(positionId)
                    .startDate(LocalDate.of(2025, 1, 20))
                    .endDate(LocalDate.of(2029, 1, 20))
                    .dataSource(DataSource.MANUAL)
                    .build();

            when(adminPresidencyService.updatePositionHolding(eq(holdingId), any()))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/admin/position-holdings/{id}", holdingId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 404 for unknown holding ID")
        void updatePositionHolding_notFound() throws Exception {
            PositionHoldingUpdateDTO dto = new PositionHoldingUpdateDTO(
                    LocalDate.of(2025, 1, 20), null);

            when(adminPresidencyService.updatePositionHolding(eq(holdingId), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Position holding not found"));

            mockMvc.perform(put("/api/admin/position-holdings/{id}", holdingId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    // DELETE /api/admin/position-holdings/{id}
    // =====================================================================

    @Nested
    @DisplayName("DELETE /api/admin/position-holdings/{id}")
    class DeletePositionHoldingTests {

        @Test
        @DisplayName("Should delete position holding and return 204")
        void deletePositionHolding_returns204() throws Exception {
            doNothing().when(adminPresidencyService).deletePositionHolding(holdingId);

            mockMvc.perform(delete("/api/admin/position-holdings/{id}", holdingId))
                    .andExpect(status().isNoContent());

            verify(adminPresidencyService).deletePositionHolding(holdingId);
        }

        @Test
        @DisplayName("Should return 404 for unknown holding ID")
        void deletePositionHolding_notFound() throws Exception {
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Position holding not found"))
                    .when(adminPresidencyService).deletePositionHolding(holdingId);

            mockMvc.perform(delete("/api/admin/position-holdings/{id}", holdingId))
                    .andExpect(status().isNotFound());
        }
    }
}
