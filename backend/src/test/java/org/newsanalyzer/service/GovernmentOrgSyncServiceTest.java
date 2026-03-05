package org.newsanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.dto.FederalRegisterAgency;
import org.newsanalyzer.model.GovernmentOrganization;
import org.newsanalyzer.model.GovernmentOrganization.GovernmentBranch;
import org.newsanalyzer.model.GovernmentOrganization.OrganizationType;
import org.newsanalyzer.repository.GovernmentOrganizationRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GovernmentOrgSyncService.
 *
 * @author James (Dev Agent)
 */
@ExtendWith(MockitoExtension.class)
class GovernmentOrgSyncServiceTest {

    @Mock
    private FederalRegisterClient federalRegisterClient;

    @Mock
    private GovernmentOrganizationRepository repository;

    private ObjectMapper objectMapper;
    private GovernmentOrgSyncService syncService;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        syncService = new GovernmentOrgSyncService(federalRegisterClient, repository, objectMapper);
        // Inject @Value field via reflection (not auto-injected in unit tests)
        java.lang.reflect.Field maxNewOrgsField = GovernmentOrgSyncService.class.getDeclaredField("maxNewOrgs");
        maxNewOrgsField.setAccessible(true);
        maxNewOrgsField.setInt(syncService, 50);
    }

    // =========================================================================
    // Type Inference Tests
    // =========================================================================

    @Test
    @DisplayName("Should infer DEPARTMENT type from 'Department of' prefix")
    void inferOrgType_departmentOf_returnsDepartment() {
        assertThat(syncService.inferOrgType("Department of Agriculture"))
                .isEqualTo(OrganizationType.DEPARTMENT);
        assertThat(syncService.inferOrgType("Department of Defense"))
                .isEqualTo(OrganizationType.DEPARTMENT);
        assertThat(syncService.inferOrgType("Department of the Treasury"))
                .isEqualTo(OrganizationType.DEPARTMENT);
    }

    @Test
    @DisplayName("Should infer INDEPENDENT_AGENCY type from 'agency' or 'administration'")
    void inferOrgType_agency_returnsIndependentAgency() {
        assertThat(syncService.inferOrgType("Environmental Protection Agency"))
                .isEqualTo(OrganizationType.INDEPENDENT_AGENCY);
        assertThat(syncService.inferOrgType("National Aeronautics and Space Administration"))
                .isEqualTo(OrganizationType.INDEPENDENT_AGENCY);
        assertThat(syncService.inferOrgType("Federal Aviation Administration"))
                .isEqualTo(OrganizationType.INDEPENDENT_AGENCY);
    }

    @Test
    @DisplayName("Should infer BUREAU type from 'bureau'")
    void inferOrgType_bureau_returnsBureau() {
        assertThat(syncService.inferOrgType("Bureau of Land Management"))
                .isEqualTo(OrganizationType.BUREAU);
        assertThat(syncService.inferOrgType("Federal Bureau of Investigation"))
                .isEqualTo(OrganizationType.BUREAU);
    }

    @Test
    @DisplayName("Should infer OFFICE type from 'office'")
    void inferOrgType_office_returnsOffice() {
        assertThat(syncService.inferOrgType("Office of Management and Budget"))
                .isEqualTo(OrganizationType.OFFICE);
        assertThat(syncService.inferOrgType("Office of Personnel Management"))
                .isEqualTo(OrganizationType.OFFICE);
    }

    @Test
    @DisplayName("Should infer COMMISSION type from 'commission'")
    void inferOrgType_commission_returnsCommission() {
        assertThat(syncService.inferOrgType("Federal Trade Commission"))
                .isEqualTo(OrganizationType.COMMISSION);
        assertThat(syncService.inferOrgType("Securities and Exchange Commission"))
                .isEqualTo(OrganizationType.COMMISSION);
    }

    @Test
    @DisplayName("Should infer BOARD type from 'board'")
    void inferOrgType_board_returnsBoard() {
        assertThat(syncService.inferOrgType("National Labor Relations Board"))
                .isEqualTo(OrganizationType.BOARD);
        assertThat(syncService.inferOrgType("Federal Reserve Board"))
                .isEqualTo(OrganizationType.BOARD);
    }

    @Test
    @DisplayName("Should default to INDEPENDENT_AGENCY when no pattern matches")
    void inferOrgType_noMatch_returnsIndependentAgency() {
        assertThat(syncService.inferOrgType("National Institutes of Health"))
                .isEqualTo(OrganizationType.INDEPENDENT_AGENCY);
        assertThat(syncService.inferOrgType("Smithsonian Institution"))
                .isEqualTo(OrganizationType.INDEPENDENT_AGENCY);
    }

    @Test
    @DisplayName("Should handle null name gracefully")
    void inferOrgType_nullName_returnsIndependentAgency() {
        assertThat(syncService.inferOrgType(null))
                .isEqualTo(OrganizationType.INDEPENDENT_AGENCY);
    }

    @Test
    @DisplayName("Type inference should be case-insensitive")
    void inferOrgType_caseInsensitive() {
        assertThat(syncService.inferOrgType("DEPARTMENT OF COMMERCE"))
                .isEqualTo(OrganizationType.DEPARTMENT);
        assertThat(syncService.inferOrgType("environmental protection agency"))
                .isEqualTo(OrganizationType.INDEPENDENT_AGENCY);
    }

    // =========================================================================
    // Sync Tests
    // =========================================================================

    @Test
    @DisplayName("Should return error result when API returns no agencies")
    void syncFromFederalRegister_noAgencies_returnsErrorResult() {
        // Given
        when(federalRegisterClient.fetchAllAgencies()).thenReturn(Collections.emptyList());

        // When
        GovernmentOrgSyncService.SyncResult result = syncService.syncFromFederalRegister();

        // Then
        assertThat(result.getAdded()).isEqualTo(0);
        assertThat(result.getUpdated()).isEqualTo(0);
        assertThat(result.getSkipped()).isEqualTo(0);
        assertThat(result.getErrors()).isEqualTo(1);
        assertThat(result.getErrorMessages()).contains("No agencies returned from Federal Register API");
    }

    @Test
    @DisplayName("Should create new organization when no match found")
    void syncFromFederalRegister_newAgency_createsRecord() {
        // Given
        FederalRegisterAgency agency = new FederalRegisterAgency();
        agency.setId(1);
        agency.setName("Department of Agriculture");
        agency.setShortName("USDA");
        agency.setUrl("https://www.federalregister.gov/agencies/agriculture-department");
        agency.setDescription("Provides leadership on food and agriculture.");

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(List.of(agency));
        when(repository.findByAcronymIgnoreCase("USDA")).thenReturn(Optional.empty());
        when(repository.findByOfficialNameIgnoreCase("Department of Agriculture")).thenReturn(Optional.empty());
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> {
            GovernmentOrganization org = invocation.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });

        // When
        GovernmentOrgSyncService.SyncResult result = syncService.syncFromFederalRegister();

        // Then
        assertThat(result.getAdded()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(0);
        assertThat(result.getErrors()).isEqualTo(0);

        ArgumentCaptor<GovernmentOrganization> orgCaptor = ArgumentCaptor.forClass(GovernmentOrganization.class);
        verify(repository, times(1)).save(orgCaptor.capture());

        GovernmentOrganization savedOrg = orgCaptor.getValue();
        assertThat(savedOrg.getOfficialName()).isEqualTo("Department of Agriculture");
        assertThat(savedOrg.getAcronym()).isEqualTo("USDA");
        assertThat(savedOrg.getBranch()).isEqualTo(GovernmentBranch.EXECUTIVE);
        assertThat(savedOrg.getOrgType()).isEqualTo(OrganizationType.DEPARTMENT);
        assertThat(savedOrg.getDescription()).isEqualTo("Provides leadership on food and agriculture.");
        assertThat(savedOrg.getCreatedBy()).isEqualTo("federal-register-sync");
    }

    @Test
    @DisplayName("Should match by acronym and update existing organization")
    void syncFromFederalRegister_matchByAcronym_updatesRecord() {
        // Given
        FederalRegisterAgency agency = new FederalRegisterAgency();
        agency.setId(1);
        agency.setName("Department of Agriculture");
        agency.setShortName("USDA");
        agency.setUrl("https://www.federalregister.gov/agencies/agriculture-department");
        agency.setDescription("New description from API");

        GovernmentOrganization existingOrg = new GovernmentOrganization();
        existingOrg.setId(UUID.randomUUID());
        existingOrg.setOfficialName("Department of Agriculture");
        existingOrg.setAcronym("USDA");
        existingOrg.setDescription(null); // Will be filled from API

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(List.of(agency));
        when(repository.findByAcronymIgnoreCase("USDA")).thenReturn(Optional.of(existingOrg));
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GovernmentOrgSyncService.SyncResult result = syncService.syncFromFederalRegister();

        // Then
        assertThat(result.getAdded()).isEqualTo(0);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getErrors()).isEqualTo(0);

        verify(repository).save(existingOrg);
        assertThat(existingOrg.getDescription()).isEqualTo("New description from API");
        assertThat(existingOrg.getUpdatedBy()).isEqualTo("federal-register-sync");
    }

    @Test
    @DisplayName("Should match by official name when acronym not found")
    void syncFromFederalRegister_matchByOfficialName_updatesRecord() {
        // Given
        FederalRegisterAgency agency = new FederalRegisterAgency();
        agency.setId(1);
        agency.setName("Environmental Protection Agency");
        agency.setShortName("EPA");
        agency.setUrl("https://www.federalregister.gov/agencies/epa");

        GovernmentOrganization existingOrg = new GovernmentOrganization();
        existingOrg.setId(UUID.randomUUID());
        existingOrg.setOfficialName("Environmental Protection Agency");
        existingOrg.setAcronym(null); // Will be filled from API

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(List.of(agency));
        when(repository.findByAcronymIgnoreCase("EPA")).thenReturn(Optional.empty());
        when(repository.findByOfficialNameIgnoreCase("Environmental Protection Agency")).thenReturn(Optional.of(existingOrg));
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GovernmentOrgSyncService.SyncResult result = syncService.syncFromFederalRegister();

        // Then
        assertThat(result.getAdded()).isEqualTo(0);
        assertThat(result.getUpdated()).isEqualTo(1);

        verify(repository).save(existingOrg);
        assertThat(existingOrg.getAcronym()).isEqualTo("EPA");
    }

    @Test
    @DisplayName("Should preserve manually curated description")
    void syncFromFederalRegister_preservesCuratedDescription() {
        // Given
        FederalRegisterAgency agency = new FederalRegisterAgency();
        agency.setId(1);
        agency.setName("Department of Commerce");
        agency.setShortName("DOC");
        agency.setDescription("API description");

        GovernmentOrganization existingOrg = new GovernmentOrganization();
        existingOrg.setId(UUID.randomUUID());
        existingOrg.setOfficialName("Department of Commerce");
        existingOrg.setAcronym("DOC");
        existingOrg.setDescription("Manually curated description"); // Should NOT be overwritten

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(List.of(agency));
        when(repository.findByAcronymIgnoreCase("DOC")).thenReturn(Optional.of(existingOrg));
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        syncService.syncFromFederalRegister();

        // Then
        verify(repository).save(existingOrg);
        assertThat(existingOrg.getDescription()).isEqualTo("Manually curated description");
    }

    @Test
    @DisplayName("Should handle sync errors gracefully and continue")
    void syncFromFederalRegister_handlesErrors_continuesProcessing() {
        // Given
        FederalRegisterAgency agency1 = new FederalRegisterAgency();
        agency1.setId(1);
        agency1.setName("Department of Agriculture");
        agency1.setShortName("USDA");

        FederalRegisterAgency agency2 = new FederalRegisterAgency();
        agency2.setId(2);
        agency2.setName("Department of Commerce");
        agency2.setShortName("DOC");

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(Arrays.asList(agency1, agency2));
        when(repository.findByAcronymIgnoreCase("USDA")).thenThrow(new RuntimeException("Database error"));
        when(repository.findByAcronymIgnoreCase("DOC")).thenReturn(Optional.empty());
        when(repository.findByOfficialNameIgnoreCase("Department of Commerce")).thenReturn(Optional.empty());
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> {
            GovernmentOrganization org = invocation.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });

        // When
        GovernmentOrgSyncService.SyncResult result = syncService.syncFromFederalRegister();

        // Then
        assertThat(result.getAdded()).isEqualTo(1);
        assertThat(result.getErrors()).isEqualTo(1);
        assertThat(result.getErrorMessages()).anyMatch(msg -> msg.contains("USDA") || msg.contains("Department of Agriculture"));
    }

    @Test
    @DisplayName("Should link parent organizations in second pass")
    void syncFromFederalRegister_linksParentOrganizations() {
        // Given - Parent agency
        FederalRegisterAgency parentAgency = new FederalRegisterAgency();
        parentAgency.setId(1);
        parentAgency.setName("Department of Agriculture");
        parentAgency.setShortName("USDA");
        parentAgency.setParentId(null);

        // Given - Child agency
        FederalRegisterAgency childAgency = new FederalRegisterAgency();
        childAgency.setId(2);
        childAgency.setName("Agricultural Marketing Service");
        childAgency.setShortName("AMS");
        childAgency.setParentId(1); // Points to USDA

        UUID parentDbId = UUID.randomUUID();
        UUID childDbId = UUID.randomUUID();

        GovernmentOrganization parentOrg = new GovernmentOrganization();
        parentOrg.setId(parentDbId);
        parentOrg.setOfficialName("Department of Agriculture");
        parentOrg.setAcronym("USDA");

        GovernmentOrganization childOrg = new GovernmentOrganization();
        childOrg.setId(childDbId);
        childOrg.setOfficialName("Agricultural Marketing Service");
        childOrg.setAcronym("AMS");
        childOrg.setParentId(null); // Should be set to parent

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(Arrays.asList(parentAgency, childAgency));
        when(repository.findByAcronymIgnoreCase("USDA")).thenReturn(Optional.of(parentOrg));
        when(repository.findByAcronymIgnoreCase("AMS")).thenReturn(Optional.of(childOrg));
        when(repository.findById(childDbId)).thenReturn(Optional.of(childOrg));
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GovernmentOrgSyncService.SyncResult result = syncService.syncFromFederalRegister();

        // Then
        assertThat(result.getErrors()).isEqualTo(0);
        assertThat(childOrg.getParentId()).isEqualTo(parentDbId);
        assertThat(childOrg.getOrgLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should skip new orgs when max-new-orgs limit is reached")
    void syncFromFederalRegister_maxNewOrgsLimit_skipsRemainingNew() throws Exception {
        // Set limit to 1
        java.lang.reflect.Field maxNewOrgsField = GovernmentOrgSyncService.class.getDeclaredField("maxNewOrgs");
        maxNewOrgsField.setAccessible(true);
        maxNewOrgsField.setInt(syncService, 1);

        // Given - two new agencies (neither exists in DB)
        FederalRegisterAgency agency1 = new FederalRegisterAgency();
        agency1.setId(1);
        agency1.setName("Department of Agriculture");
        agency1.setShortName("USDA");

        FederalRegisterAgency agency2 = new FederalRegisterAgency();
        agency2.setId(2);
        agency2.setName("Department of Commerce");
        agency2.setShortName("DOC");

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(Arrays.asList(agency1, agency2));
        when(repository.findByAcronymIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findByOfficialNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> {
            GovernmentOrganization org = invocation.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });

        // When
        GovernmentOrgSyncService.SyncResult result = syncService.syncFromFederalRegister();

        // Then - first one added, second skipped due to limit
        assertThat(result.getAdded()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should clear oversized acronym on existing org during update")
    void syncFromFederalRegister_oversizedAcronym_clearsIt() {
        // Given - use a >50 char value that was set as acronym in a prior bad sync
        String oversizedAcronym = "A".repeat(51);

        FederalRegisterAgency agency = new FederalRegisterAgency();
        agency.setId(1);
        agency.setName("Some Long Named Agency");
        agency.setShortName(oversizedAcronym);

        GovernmentOrganization existingOrg = new GovernmentOrganization();
        existingOrg.setId(UUID.randomUUID());
        existingOrg.setOfficialName("Some Long Named Agency");
        existingOrg.setAcronym(oversizedAcronym); // Bad data from prior sync

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(List.of(agency));
        // shortName is > 50 chars so acronym lookup won't match, but name will
        when(repository.findByOfficialNameIgnoreCase("Some Long Named Agency"))
                .thenReturn(Optional.of(existingOrg));
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        syncService.syncFromFederalRegister();

        // Then - oversized acronym should be cleared
        verify(repository).save(existingOrg);
        assertThat(existingOrg.getAcronym()).isNull();
    }

    @Test
    @DisplayName("Should not set acronym when shortName exceeds 50 characters on new org")
    void syncFromFederalRegister_longShortName_skipsAcronym() {
        // Given
        FederalRegisterAgency agency = new FederalRegisterAgency();
        agency.setId(1);
        agency.setName("Civil Rights Cold Case Records Review Board");
        agency.setShortName("Civil Rights Cold Case Records Review Board"); // 45 chars — but test with > 50

        // Use a truly >50 char short name
        agency.setShortName("A".repeat(51));

        when(federalRegisterClient.fetchAllAgencies()).thenReturn(List.of(agency));
        when(repository.findByOfficialNameIgnoreCase(agency.getName())).thenReturn(Optional.empty());
        when(repository.save(any(GovernmentOrganization.class))).thenAnswer(invocation -> {
            GovernmentOrganization org = invocation.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });

        // When
        syncService.syncFromFederalRegister();

        // Then
        ArgumentCaptor<GovernmentOrganization> captor = ArgumentCaptor.forClass(GovernmentOrganization.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAcronym()).isNull();
    }

    // =========================================================================
    // Status Tests
    // =========================================================================

    @Test
    @DisplayName("Should return status with organization counts")
    void getStatus_returnsStatistics() {
        // Given
        when(repository.countActive()).thenReturn(300L);
        when(repository.countByBranch()).thenReturn(Arrays.asList(
                new Object[]{GovernmentBranch.EXECUTIVE, 280L},
                new Object[]{GovernmentBranch.LEGISLATIVE, 15L},
                new Object[]{GovernmentBranch.JUDICIAL, 5L}
        ));
        when(federalRegisterClient.isApiAvailable()).thenReturn(true);

        // When
        GovernmentOrgSyncService.SyncStatus status = syncService.getStatus();

        // Then
        assertThat(status.getTotalOrganizations()).isEqualTo(300L);
        assertThat(status.isFederalRegisterAvailable()).isTrue();
        assertThat(status.getCountByBranch()).containsEntry("executive", 280L);
        assertThat(status.getCountByBranch()).containsEntry("legislative", 15L);
        assertThat(status.getCountByBranch()).containsEntry("judicial", 5L);
    }

    @Test
    @DisplayName("Should report Federal Register unavailable when API is down")
    void getStatus_apiUnavailable_reportsCorrectly() {
        // Given
        when(repository.countActive()).thenReturn(0L);
        when(repository.countByBranch()).thenReturn(Collections.emptyList());
        when(federalRegisterClient.isApiAvailable()).thenReturn(false);

        // When
        GovernmentOrgSyncService.SyncStatus status = syncService.getStatus();

        // Then
        assertThat(status.isFederalRegisterAvailable()).isFalse();
    }
}
