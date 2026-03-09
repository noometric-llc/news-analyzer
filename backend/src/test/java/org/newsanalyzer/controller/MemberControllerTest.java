package org.newsanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.CongressionalMember;
import org.newsanalyzer.model.CongressionalMember.Chamber;
import org.newsanalyzer.model.DataSource;
import org.newsanalyzer.model.Individual;
import org.newsanalyzer.model.PositionHolding;
import org.newsanalyzer.repository.CongressionalMemberRepository;
import org.newsanalyzer.repository.PositionHoldingRepository;
import org.newsanalyzer.scheduler.EnrichmentScheduler;
import org.newsanalyzer.service.CommitteeService;
import org.newsanalyzer.service.MemberService;
import org.newsanalyzer.service.MemberSyncService;
import org.newsanalyzer.service.SyncJobRegistry;
import org.newsanalyzer.service.SyncOrchestrator;
import org.newsanalyzer.service.TermSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for MemberController.
 *
 * Uses MockMvc to test HTTP endpoints with mocked services.
 *
 * Part of ARCH-1.7: Updated to use CongressionalMember instead of Person.
 *
 * @author James (Dev Agent)
 * @since 3.0.0
 */
@WebMvcTest(MemberController.class)
@WithMockUser
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemberService memberService;

    @MockBean
    private MemberSyncService memberSyncService;

    @MockBean
    private CommitteeService committeeService;

    @MockBean
    private EnrichmentScheduler enrichmentScheduler;

    @MockBean
    private TermSyncService termSyncService;

    @MockBean
    private PositionHoldingRepository positionHoldingRepository;

    @MockBean
    private CongressionalMemberRepository congressionalMemberRepository;

    @MockBean
    private SyncJobRegistry syncJobRegistry;

    @MockBean
    private SyncOrchestrator syncOrchestrator;

    private CongressionalMember testMember;
    private Individual testIndividual;
    private PositionHolding testHolding;

    @BeforeEach
    void setUp() {
        UUID individualId = UUID.randomUUID();

        testIndividual = new Individual();
        testIndividual.setId(individualId);
        testIndividual.setFirstName("Bernard");
        testIndividual.setLastName("Sanders");
        testIndividual.setParty("Independent");
        testIndividual.setBirthDate(LocalDate.of(1941, 9, 8));

        testMember = new CongressionalMember();
        testMember.setId(UUID.randomUUID());
        testMember.setBioguideId("S000033");
        testMember.setIndividualId(individualId);
        testMember.setIndividual(testIndividual);
        testMember.setState("VT");
        testMember.setChamber(Chamber.SENATE);

        testHolding = PositionHolding.builder()
                .id(UUID.randomUUID())
                .individualId(individualId)
                .positionId(UUID.randomUUID())
                .startDate(LocalDate.of(2023, 1, 3))
                .endDate(null)
                .congress(118)
                .dataSource(DataSource.CONGRESS_GOV)
                .build();
    }

    // =========================================================================
    // List Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/members - Should return paginated list of members")
    void listAll_returnsPagedMembers() throws Exception {
        // Given - Controller uses findAllWithIndividual() which returns List
        when(memberService.findAllWithIndividual()).thenReturn(List.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bioguideId", is("S000033")));
    }

    @Test
    @DisplayName("GET /api/members - Should support pagination parameters")
    void listAll_withPagination_returnsCorrectPage() throws Exception {
        // Given
        when(memberService.findAllWithIndividual()).thenReturn(List.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "bioguideId,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // =========================================================================
    // Lookup Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/members/{bioguideId} - Should return member when found")
    void getByBioguideId_found_returnsMember() throws Exception {
        // Given - Controller uses findByBioguideIdWithIndividual
        when(memberService.findByBioguideIdWithIndividual("S000033")).thenReturn(Optional.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members/S000033"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bioguideId", is("S000033")))
                .andExpect(jsonPath("$.state", is("VT")))
                .andExpect(jsonPath("$.chamber", is("SENATE")));
    }

    @Test
    @DisplayName("GET /api/members/{bioguideId} - Should return 404 when not found")
    void getByBioguideId_notFound_returns404() throws Exception {
        // Given
        when(memberService.findByBioguideIdWithIndividual("INVALID")).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/members/INVALID"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Search Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/members/search - Should search by name")
    void searchByName_validName_returnsResults() throws Exception {
        // Given
        Page<CongressionalMember> page = new PageImpl<>(List.of(testMember));
        when(memberService.searchByName(eq("Sanders"), any(Pageable.class))).thenReturn(page);
        when(congressionalMemberRepository.findByIdWithIndividual(testMember.getId()))
                .thenReturn(Optional.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members/search")
                        .param("name", "Sanders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/members/search - Should return 400 for empty name")
    void searchByName_emptyName_returns400() throws Exception {
        mockMvc.perform(get("/api/members/search")
                        .param("name", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/members/search - Should return 400 for whitespace name")
    void searchByName_whitespaceName_returns400() throws Exception {
        mockMvc.perform(get("/api/members/search")
                        .param("name", "   "))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Filter Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/members/by-state/{state} - Should filter by state")
    void getByState_validState_returnsMembers() throws Exception {
        // Given - Controller uses congressionalMemberRepository.findByStateWithIndividual
        when(congressionalMemberRepository.findByStateWithIndividual("VT")).thenReturn(List.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members/by-state/VT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].state", is("VT")));
    }

    @Test
    @DisplayName("GET /api/members/by-state/{state} - Should handle lowercase state")
    void getByState_lowercaseState_convertsToUppercase() throws Exception {
        // Given
        when(congressionalMemberRepository.findByStateWithIndividual("VT")).thenReturn(List.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members/by-state/vt"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/members/by-state/{state} - Should return 400 for invalid state code")
    void getByState_invalidStateCode_returns400() throws Exception {
        mockMvc.perform(get("/api/members/by-state/VERMONT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/members/by-chamber/{chamber} - Should filter by SENATE")
    void getByChamber_senate_returnsSenators() throws Exception {
        // Given - Controller uses congressionalMemberRepository.findByChamberWithIndividual
        when(congressionalMemberRepository.findByChamberWithIndividual(Chamber.SENATE)).thenReturn(List.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members/by-chamber/SENATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].chamber", is("SENATE")));
    }

    @Test
    @DisplayName("GET /api/members/by-chamber/{chamber} - Should filter by HOUSE")
    void getByChamber_house_returnsRepresentatives() throws Exception {
        // Given
        CongressionalMember houseMember = new CongressionalMember();
        houseMember.setId(UUID.randomUUID());
        houseMember.setBioguideId("P000197");
        houseMember.setChamber(Chamber.HOUSE);
        houseMember.setState("CA");
        houseMember.setIndividualId(UUID.randomUUID());
        Individual houseIndividual = new Individual();
        houseIndividual.setFirstName("Nancy");
        houseIndividual.setLastName("Pelosi");
        houseMember.setIndividual(houseIndividual);

        when(congressionalMemberRepository.findByChamberWithIndividual(Chamber.HOUSE)).thenReturn(List.of(houseMember));

        // When/Then
        mockMvc.perform(get("/api/members/by-chamber/HOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].chamber", is("HOUSE")));
    }

    @Test
    @DisplayName("GET /api/members/by-chamber/{chamber} - Should handle lowercase chamber")
    void getByChamber_lowercaseChamber_convertsToUppercase() throws Exception {
        // Given
        when(congressionalMemberRepository.findByChamberWithIndividual(Chamber.SENATE)).thenReturn(List.of(testMember));

        // When/Then
        mockMvc.perform(get("/api/members/by-chamber/senate"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/members/by-chamber/{chamber} - Should return 400 for invalid chamber")
    void getByChamber_invalidChamber_returns400() throws Exception {
        mockMvc.perform(get("/api/members/by-chamber/INVALID"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Statistics Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/members/count - Should return total count")
    void count_returnsTotal() throws Exception {
        // Given
        when(memberService.count()).thenReturn(535L);

        // When/Then
        mockMvc.perform(get("/api/members/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("535"));
    }

    @Test
    @DisplayName("GET /api/members/stats/party - Should return party distribution")
    void getPartyDistribution_returnsStats() throws Exception {
        // Given
        List<Object[]> stats = List.of(
                new Object[]{"Democratic", 213L},
                new Object[]{"Republican", 220L},
                new Object[]{"Independent", 2L}
        );
        when(memberService.getPartyDistribution()).thenReturn(stats);

        // When/Then
        mockMvc.perform(get("/api/members/stats/party"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/members/stats/state - Should return state distribution")
    void getStateDistribution_returnsStats() throws Exception {
        // Given
        List<Object[]> stats = List.of(
                new Object[]{"CA", 54L},
                new Object[]{"TX", 38L},
                new Object[]{"VT", 3L}
        );
        when(memberService.getStateDistribution()).thenReturn(stats);

        // When/Then
        mockMvc.perform(get("/api/members/stats/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // =========================================================================
    // Admin Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/members/sync - Should trigger sync and return result")
    void triggerSync_returnsSyncResult() throws Exception {
        // Given
        MemberSyncService.SyncResult result = new MemberSyncService.SyncResult();
        result.setTotal(535);
        result.setAdded(10);
        result.setUpdated(525);
        result.setErrors(0);
        when(memberSyncService.syncAllCurrentMembers()).thenReturn(result);

        // When/Then
        mockMvc.perform(post("/api/members/sync")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(535)))
                .andExpect(jsonPath("$.added", is(10)))
                .andExpect(jsonPath("$.updated", is(525)))
                .andExpect(jsonPath("$.errors", is(0)));
    }

    // =========================================================================
    // Response Time Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/members - Should respond within 500ms")
    void listAll_respondsWithin500ms() throws Exception {
        // Given
        when(memberService.findAllWithIndividual()).thenReturn(List.of(testMember));

        // When
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk());
        long endTime = System.currentTimeMillis();

        // Then
        long responseTime = endTime - startTime;
        org.assertj.core.api.Assertions.assertThat(responseTime).isLessThan(500);
    }

    @Test
    @DisplayName("GET /api/members/{bioguideId} - Should respond within 500ms")
    void getByBioguideId_respondsWithin500ms() throws Exception {
        // Given
        when(memberService.findByBioguideIdWithIndividual("S000033")).thenReturn(Optional.of(testMember));

        // When
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/members/S000033"))
                .andExpect(status().isOk());
        long endTime = System.currentTimeMillis();

        // Then
        long responseTime = endTime - startTime;
        org.assertj.core.api.Assertions.assertThat(responseTime).isLessThan(500);
    }

    // =========================================================================
    // Term History Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/members/{bioguideId}/terms - Should return member's terms")
    void getMemberTerms_found_returnsTerms() throws Exception {
        // Given
        when(memberService.getByBioguideId("S000033")).thenReturn(testMember);
        when(positionHoldingRepository.findByIndividualIdOrderByStartDateDesc(testMember.getIndividualId()))
                .thenReturn(List.of(testHolding));

        // When/Then
        mockMvc.perform(get("/api/members/S000033/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].congress", is(118)));
    }

    @Test
    @DisplayName("GET /api/members/{bioguideId}/terms - Should return 404 for unknown member")
    void getMemberTerms_notFound_returns404() throws Exception {
        // Given
        when(memberService.getByBioguideId("INVALID"))
                .thenThrow(new ResourceNotFoundException("Member not found: INVALID"));

        // When/Then
        mockMvc.perform(get("/api/members/INVALID/terms"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/members/on-date/{date} - Should return members on date")
    void getMembersOnDate_validDate_returnsMembers() throws Exception {
        // Given
        Page<PositionHolding> page = new PageImpl<>(List.of(testHolding));
        when(positionHoldingRepository.findAllActiveOnDate(eq(LocalDate.of(2024, 1, 15)), any(Pageable.class)))
                .thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/members/on-date/2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/members/on-date/{date} - Should return 400 for invalid date")
    void getMembersOnDate_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/api/members/on-date/not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/members/sync-terms - Should trigger term sync")
    void triggerTermSync_returnsSyncResult() throws Exception {
        // Given
        TermSyncService.SyncResult result = new TermSyncService.SyncResult(535, 1070, 0, 0);
        when(termSyncService.syncAllCurrentMemberTerms()).thenReturn(result);

        // When/Then
        mockMvc.perform(post("/api/members/sync-terms")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersProcessed", is(535)))
                .andExpect(jsonPath("$.termsAdded", is(1070)));
    }
}
