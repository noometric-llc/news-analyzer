package org.newsanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.model.Committee;
import org.newsanalyzer.model.CommitteeChamber;
import org.newsanalyzer.model.CommitteeMembership;
import org.newsanalyzer.model.CommitteeType;
import org.newsanalyzer.model.MembershipRole;
import org.newsanalyzer.model.CongressionalMember;
import org.newsanalyzer.service.CommitteeMembershipSyncService;
import org.newsanalyzer.service.CommitteeService;
import org.newsanalyzer.service.CommitteeSyncService;
import org.newsanalyzer.service.SyncJobRegistry;
import org.newsanalyzer.service.SyncOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

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
 * Integration tests for CommitteeController.
 *
 * Uses MockMvc to test HTTP endpoints with mocked services.
 *
 * @author James (Dev Agent)
 */
@WebMvcTest(CommitteeController.class)
@WithMockUser
class CommitteeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommitteeService committeeService;

    @MockBean
    private CommitteeSyncService committeeSyncService;

    @MockBean
    private CommitteeMembershipSyncService membershipSyncService;

    @MockBean
    private SyncJobRegistry syncJobRegistry;

    @MockBean
    private SyncOrchestrator syncOrchestrator;

    private Committee testCommittee;
    private Committee testSubcommittee;
    private CommitteeMembership testMembership;

    @BeforeEach
    void setUp() {
        testCommittee = new Committee();
        testCommittee.setCommitteeCode("hsju00");
        testCommittee.setName("Committee on the Judiciary");
        testCommittee.setChamber(CommitteeChamber.HOUSE);
        testCommittee.setCommitteeType(CommitteeType.STANDING);

        testSubcommittee = new Committee();
        testSubcommittee.setCommitteeCode("hsju01");
        testSubcommittee.setName("Subcommittee on Immigration Integrity");
        testSubcommittee.setChamber(CommitteeChamber.HOUSE);
        testSubcommittee.setCommitteeType(CommitteeType.SUBCOMMITTEE);
        testSubcommittee.setParentCommittee(testCommittee);

        CongressionalMember testMember = new CongressionalMember();
        testMember.setId(UUID.randomUUID());
        testMember.setBioguideId("S000033");
        testMember.setIndividualId(UUID.randomUUID());
        testMember.setChamber(CongressionalMember.Chamber.SENATE);
        testMember.setState("VT");

        testMembership = new CommitteeMembership();
        testMembership.setId(UUID.randomUUID());
        testMembership.setCongressionalMember(testMember);
        testMembership.setCommittee(testCommittee);
        testMembership.setRole(MembershipRole.MEMBER);
        testMembership.setCongress(118);
    }

    // =========================================================================
    // List Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees - Should return paginated list of committees")
    void listAll_returnsPagedCommittees() throws Exception {
        // Given
        Page<Committee> page = new PageImpl<>(List.of(testCommittee));
        when(committeeService.findAll(any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].committeeCode", is("hsju00")))
                .andExpect(jsonPath("$.content[0].name", is("Committee on the Judiciary")));
    }

    @Test
    @DisplayName("GET /api/committees - Should support pagination parameters")
    void listAll_withPagination_returnsCorrectPage() throws Exception {
        // Given
        Page<Committee> page = new PageImpl<>(List.of(testCommittee));
        when(committeeService.findAll(any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // =========================================================================
    // Lookup Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees/{code} - Should return committee when found")
    void getByCode_found_returnsCommittee() throws Exception {
        // Given
        when(committeeService.findByCode("hsju00")).thenReturn(Optional.of(testCommittee));

        // When/Then
        mockMvc.perform(get("/api/committees/hsju00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committeeCode", is("hsju00")))
                .andExpect(jsonPath("$.name", is("Committee on the Judiciary")))
                .andExpect(jsonPath("$.chamber", is("HOUSE")))
                .andExpect(jsonPath("$.committeeType", is("STANDING")));
    }

    @Test
    @DisplayName("GET /api/committees/{code} - Should return 404 when not found")
    void getByCode_notFound_returns404() throws Exception {
        // Given
        when(committeeService.findByCode("INVALID")).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/committees/INVALID"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Members Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees/{code}/members - Should return committee members")
    void getMembers_returnsPagedMembers() throws Exception {
        // Given
        when(committeeService.getByCode("hsju00")).thenReturn(testCommittee);
        Page<CommitteeMembership> page = new PageImpl<>(List.of(testMembership));
        when(committeeService.findMembers(eq("hsju00"), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/hsju00/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/committees/{code}/members - Should filter by congress")
    void getMembers_withCongress_filtersResults() throws Exception {
        // Given
        when(committeeService.getByCode("hsju00")).thenReturn(testCommittee);
        Page<CommitteeMembership> page = new PageImpl<>(List.of(testMembership));
        when(committeeService.findMembersByCongress(eq("hsju00"), eq(118), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/hsju00/members")
                        .param("congress", "118"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // =========================================================================
    // Subcommittee Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees/{code}/subcommittees - Should return subcommittees")
    void getSubcommittees_returnsPagedSubcommittees() throws Exception {
        // Given
        when(committeeService.getByCode("hsju00")).thenReturn(testCommittee);
        Page<Committee> page = new PageImpl<>(List.of(testSubcommittee));
        when(committeeService.findSubcommittees(eq("hsju00"), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/hsju00/subcommittees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].committeeCode", is("hsju01")))
                .andExpect(jsonPath("$.content[0].committeeType", is("SUBCOMMITTEE")));
    }

    // =========================================================================
    // Filter Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees/by-chamber/{chamber} - Should filter by HOUSE")
    void getByChamber_house_returnsHouseCommittees() throws Exception {
        // Given
        Page<Committee> page = new PageImpl<>(List.of(testCommittee));
        when(committeeService.findByChamber(eq(CommitteeChamber.HOUSE), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/by-chamber/HOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].chamber", is("HOUSE")));
    }

    @Test
    @DisplayName("GET /api/committees/by-chamber/{chamber} - Should filter by SENATE")
    void getByChamber_senate_returnsSenateCommittees() throws Exception {
        // Given
        Committee senateCommittee = new Committee();
        senateCommittee.setCommitteeCode("ssju00");
        senateCommittee.setName("Committee on the Judiciary");
        senateCommittee.setChamber(CommitteeChamber.SENATE);
        senateCommittee.setCommitteeType(CommitteeType.STANDING);

        Page<Committee> page = new PageImpl<>(List.of(senateCommittee));
        when(committeeService.findByChamber(eq(CommitteeChamber.SENATE), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/by-chamber/SENATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].chamber", is("SENATE")));
    }

    @Test
    @DisplayName("GET /api/committees/by-chamber/{chamber} - Should filter by JOINT")
    void getByChamber_joint_returnsJointCommittees() throws Exception {
        // Given
        Committee jointCommittee = new Committee();
        jointCommittee.setCommitteeCode("jslc00");
        jointCommittee.setName("Joint Select Committee on Solvency");
        jointCommittee.setChamber(CommitteeChamber.JOINT);
        jointCommittee.setCommitteeType(CommitteeType.JOINT);

        Page<Committee> page = new PageImpl<>(List.of(jointCommittee));
        when(committeeService.findByChamber(eq(CommitteeChamber.JOINT), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/by-chamber/JOINT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].chamber", is("JOINT")));
    }

    @Test
    @DisplayName("GET /api/committees/by-chamber/{chamber} - Should handle lowercase chamber")
    void getByChamber_lowercaseChamber_convertsToUppercase() throws Exception {
        // Given
        Page<Committee> page = new PageImpl<>(List.of(testCommittee));
        when(committeeService.findByChamber(eq(CommitteeChamber.HOUSE), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/by-chamber/house"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/committees/by-chamber/{chamber} - Should return 400 for invalid chamber")
    void getByChamber_invalidChamber_returns400() throws Exception {
        mockMvc.perform(get("/api/committees/by-chamber/INVALID"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Search Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees/search - Should search by name")
    void searchByName_validName_returnsResults() throws Exception {
        // Given
        Page<Committee> page = new PageImpl<>(List.of(testCommittee));
        when(committeeService.searchByName(eq("Judiciary"), any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/committees/search")
                        .param("name", "Judiciary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", containsString("Judiciary")));
    }

    @Test
    @DisplayName("GET /api/committees/search - Should return 400 for empty name")
    void searchByName_emptyName_returns400() throws Exception {
        mockMvc.perform(get("/api/committees/search")
                        .param("name", ""))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Statistics Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees/count - Should return total count")
    void count_returnsTotal() throws Exception {
        // Given
        when(committeeService.count()).thenReturn(250L);

        // When/Then
        mockMvc.perform(get("/api/committees/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("250"));
    }

    @Test
    @DisplayName("GET /api/committees/stats/type - Should return type distribution")
    void getTypeDistribution_returnsStats() throws Exception {
        // Given
        List<Object[]> stats = List.of(
                new Object[]{CommitteeType.STANDING, 40L},
                new Object[]{CommitteeType.SUBCOMMITTEE, 200L},
                new Object[]{CommitteeType.JOINT, 10L}
        );
        when(committeeService.getTypeDistribution()).thenReturn(stats);

        // When/Then
        mockMvc.perform(get("/api/committees/stats/type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/committees/stats/chamber - Should return chamber distribution")
    void getChamberDistribution_returnsStats() throws Exception {
        // Given
        List<Object[]> stats = List.of(
                new Object[]{CommitteeChamber.HOUSE, 120L},
                new Object[]{CommitteeChamber.SENATE, 100L},
                new Object[]{CommitteeChamber.JOINT, 30L}
        );
        when(committeeService.getChamberDistribution()).thenReturn(stats);

        // When/Then
        mockMvc.perform(get("/api/committees/stats/chamber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // =========================================================================
    // Admin Endpoints Tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/committees/sync - Should trigger sync and return result")
    void triggerSync_returnsSyncResult() throws Exception {
        // Given
        CommitteeSyncService.SyncResult result = new CommitteeSyncService.SyncResult();
        when(committeeSyncService.syncAllCommittees()).thenReturn(result);

        // When/Then
        mockMvc.perform(post("/api/committees/sync")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").exists())
                .andExpect(jsonPath("$.updated").exists())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/committees/sync/memberships - Should trigger membership sync")
    void triggerMembershipSync_returnsSyncResult() throws Exception {
        // Given
        CommitteeMembershipSyncService.SyncResult result = new CommitteeMembershipSyncService.SyncResult();
        when(membershipSyncService.syncAllMemberships(118)).thenReturn(result);

        // When/Then
        mockMvc.perform(post("/api/committees/sync/memberships")
                        .with(csrf())
                        .param("congress", "118"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").exists())
                .andExpect(jsonPath("$.errors").exists());
    }

    // =========================================================================
    // Response Time Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/committees - Should respond within 500ms")
    void listAll_respondsWithin500ms() throws Exception {
        // Given
        Page<Committee> page = new PageImpl<>(List.of(testCommittee));
        when(committeeService.findAll(any(Pageable.class))).thenReturn(page);

        // When
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/committees"))
                .andExpect(status().isOk());
        long endTime = System.currentTimeMillis();

        // Then
        long responseTime = endTime - startTime;
        org.assertj.core.api.Assertions.assertThat(responseTime).isLessThan(500);
    }

    @Test
    @DisplayName("GET /api/committees/{code} - Should respond within 500ms")
    void getByCode_respondsWithin500ms() throws Exception {
        // Given
        when(committeeService.findByCode("hsju00")).thenReturn(Optional.of(testCommittee));

        // When
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/committees/hsju00"))
                .andExpect(status().isOk());
        long endTime = System.currentTimeMillis();

        // Then
        long responseTime = endTime - startTime;
        org.assertj.core.api.Assertions.assertThat(responseTime).isLessThan(500);
    }
}
