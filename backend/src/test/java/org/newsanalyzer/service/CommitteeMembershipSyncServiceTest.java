package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.model.Committee;
import org.newsanalyzer.model.CommitteeChamber;
import org.newsanalyzer.model.CommitteeMembership;
import org.newsanalyzer.model.CommitteeType;
import org.newsanalyzer.model.CongressionalMember;
import org.newsanalyzer.model.MembershipRole;
import org.newsanalyzer.repository.CommitteeMembershipRepository;
import org.newsanalyzer.repository.CommitteeRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommitteeMembershipSyncService.
 *
 * Updated to reflect the switch from Congress.gov API (which doesn't expose
 * committee membership data) to the unitedstates/congress-legislators GitHub repo.
 *
 * @author James (Dev Agent)
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
class CommitteeMembershipSyncServiceTest {

    @Mock
    private LegislatorsRepoClient legislatorsRepoClient;

    @Mock
    private CommitteeMembershipRepository membershipRepository;

    @Mock
    private CommitteeRepository committeeRepository;

    @Mock
    private CongressionalMemberService congressionalMemberService;

    @InjectMocks
    private CommitteeMembershipSyncService syncService;

    private Committee testCommittee;
    private CongressionalMember testMember;

    @BeforeEach
    void setUp() {
        testCommittee = new Committee();
        testCommittee.setCommitteeCode("hsju00");
        testCommittee.setName("Committee on the Judiciary");
        testCommittee.setChamber(CommitteeChamber.HOUSE);
        testCommittee.setCommitteeType(CommitteeType.STANDING);

        testMember = new CongressionalMember();
        testMember.setId(UUID.randomUUID());
        testMember.setBioguideId("S000033");
        testMember.setIndividualId(UUID.randomUUID());
    }

    // =========================================================================
    // Thomas ID Conversion Tests
    // =========================================================================

    @Nested
    @DisplayName("thomasIdToCommitteeCode")
    class ThomasIdConversionTests {

        @ParameterizedTest
        @CsvSource({
                "SSJU, ssju00",
                "HSJU, hsju00",
                "SSAF, ssaf00",
                "JSLC, jslc00",
                "SSJU13, ssju13",
                "HSAP01, hsap01",
                "SSAF14, ssaf14"
        })
        @DisplayName("Should convert Thomas ID to committee code")
        void convertsCorrectly(String thomasId, String expectedCode) {
            assertThat(CommitteeMembershipSyncService.thomasIdToCommitteeCode(thomasId))
                    .isEqualTo(expectedCode);
        }
    }

    // =========================================================================
    // syncAllMemberships Tests
    // =========================================================================

    @Nested
    @DisplayName("syncAllMemberships")
    class SyncAllMembershipsTests {

        @Test
        @DisplayName("Should return error when GitHub repo returns empty data")
        void emptyData_returnsError() {
            // Given
            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent())
                    .thenReturn(Collections.emptyMap());

            // When
            CommitteeMembershipSyncService.SyncResult result = syncService.syncAllMemberships();

            // Then
            assertThat(result.getErrors()).isEqualTo(1);
            assertThat(result.getAdded()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should skip committee when not found in database")
        void committeeNotFound_skips() {
            // Given
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            data.put("HSJU", List.of(createMemberRecord("S000033", "Chair")));

            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent()).thenReturn(data);
            when(committeeRepository.findByCommitteeCode("hsju00")).thenReturn(Optional.empty());

            // When
            CommitteeMembershipSyncService.SyncResult result = syncService.syncAllMemberships();

            // Then
            assertThat(result.getSkipped()).isEqualTo(1);
            assertThat(result.getAdded()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should add new membership from YAML data")
        void newMembership_addsToDatabase() {
            // Given
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            data.put("HSJU", List.of(createMemberRecord("S000033", "Member")));

            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent()).thenReturn(data);
            when(committeeRepository.findByCommitteeCode("hsju00")).thenReturn(Optional.of(testCommittee));
            when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.of(testMember));
            when(membershipRepository.findByCongressionalMember_IdAndCommittee_CommitteeCodeAndCongress(
                    testMember.getId(), "hsju00", 119)).thenReturn(Optional.empty());
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            CommitteeMembershipSyncService.SyncResult result = syncService.syncAllMemberships();

            // Then
            assertThat(result.getAdded()).isEqualTo(1);

            ArgumentCaptor<CommitteeMembership> captor = ArgumentCaptor.forClass(CommitteeMembership.class);
            verify(membershipRepository).save(captor.capture());
            assertThat(captor.getValue().getCongressionalMember()).isEqualTo(testMember);
            assertThat(captor.getValue().getCommittee()).isEqualTo(testCommittee);
            assertThat(captor.getValue().getCongress()).isEqualTo(119);
            assertThat(captor.getValue().getDataSource()).isEqualTo("LEGISLATORS_REPO");
        }

        @Test
        @DisplayName("Should update existing membership")
        void existingMembership_updates() {
            // Given
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            data.put("HSJU", List.of(createMemberRecord("S000033", "Chairman")));

            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent()).thenReturn(data);
            when(committeeRepository.findByCommitteeCode("hsju00")).thenReturn(Optional.of(testCommittee));
            when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.of(testMember));

            CommitteeMembership existing = new CommitteeMembership();
            existing.setId(UUID.randomUUID());
            existing.setRole(MembershipRole.MEMBER);
            when(membershipRepository.findByCongressionalMember_IdAndCommittee_CommitteeCodeAndCongress(
                    testMember.getId(), "hsju00", 119)).thenReturn(Optional.of(existing));
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            CommitteeMembershipSyncService.SyncResult result = syncService.syncAllMemberships();

            // Then
            assertThat(result.getUpdated()).isEqualTo(1);
            assertThat(result.getAdded()).isEqualTo(0);

            ArgumentCaptor<CommitteeMembership> captor = ArgumentCaptor.forClass(CommitteeMembership.class);
            verify(membershipRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(MembershipRole.CHAIR);
        }

        @Test
        @DisplayName("Should skip member when congressional member not found")
        void memberNotFound_skipsMember() {
            // Given
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            data.put("HSJU", List.of(createMemberRecord("UNKNOWN", "Member")));

            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent()).thenReturn(data);
            when(committeeRepository.findByCommitteeCode("hsju00")).thenReturn(Optional.of(testCommittee));
            when(congressionalMemberService.findByBioguideId("UNKNOWN")).thenReturn(Optional.empty());

            // When
            CommitteeMembershipSyncService.SyncResult result = syncService.syncAllMemberships();

            // Then
            assertThat(result.getSkipped()).isEqualTo(1);
            assertThat(result.getAdded()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle multiple committees and members")
        void multipleCommitteesAndMembers_syncsAll() {
            // Given
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            data.put("HSJU", List.of(
                    createMemberRecord("S000033", "Chairman"),
                    createMemberRecord("P000197", "Ranking Member")
            ));
            data.put("SSJU", List.of(
                    createMemberRecord("M000355", null)
            ));

            Committee senateCommittee = new Committee();
            senateCommittee.setCommitteeCode("ssju00");
            senateCommittee.setName("Senate Judiciary");
            senateCommittee.setChamber(CommitteeChamber.SENATE);

            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent()).thenReturn(data);
            when(committeeRepository.findByCommitteeCode("hsju00")).thenReturn(Optional.of(testCommittee));
            when(committeeRepository.findByCommitteeCode("ssju00")).thenReturn(Optional.of(senateCommittee));

            CongressionalMember member1 = createMember("S000033");
            CongressionalMember member2 = createMember("P000197");
            CongressionalMember member3 = createMember("M000355");
            when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.of(member1));
            when(congressionalMemberService.findByBioguideId("P000197")).thenReturn(Optional.of(member2));
            when(congressionalMemberService.findByBioguideId("M000355")).thenReturn(Optional.of(member3));
            when(membershipRepository.findByCongressionalMember_IdAndCommittee_CommitteeCodeAndCongress(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            CommitteeMembershipSyncService.SyncResult result = syncService.syncAllMemberships();

            // Then
            assertThat(result.getAdded()).isEqualTo(3);
            verify(membershipRepository, times(3)).save(any());
        }

        @Test
        @DisplayName("Should handle subcommittee Thomas IDs")
        void subcommitteeCode_mapsCorrectly() {
            // Given
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            data.put("SSJU13", List.of(createMemberRecord("S000033", "Member")));

            Committee subcommittee = new Committee();
            subcommittee.setCommitteeCode("ssju13");
            subcommittee.setName("Subcommittee on Privacy");
            subcommittee.setChamber(CommitteeChamber.SENATE);

            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent()).thenReturn(data);
            when(committeeRepository.findByCommitteeCode("ssju13")).thenReturn(Optional.of(subcommittee));
            when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.of(testMember));
            when(membershipRepository.findByCongressionalMember_IdAndCommittee_CommitteeCodeAndCongress(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            CommitteeMembershipSyncService.SyncResult result = syncService.syncAllMemberships();

            // Then
            assertThat(result.getAdded()).isEqualTo(1);
        }
    }

    // =========================================================================
    // Role Mapping Tests
    // =========================================================================

    @Nested
    @DisplayName("Role Mapping")
    class RoleMappingTests {

        @ParameterizedTest
        @CsvSource({
                "Chairman, CHAIR",
                "Chair, CHAIR",
                "Vice Chairman, VICE_CHAIR",
                "Vice Chair, VICE_CHAIR",
                "Ranking Member, RANKING_MEMBER",
                "Ex Officio, EX_OFFICIO",
                "Ex-Officio, EX_OFFICIO",
                "'', MEMBER"
        })
        @DisplayName("Should map title string to correct role enum")
        void mapsRoleCorrectly(String title, String expectedRole) {
            // Given
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            data.put("HSJU", List.of(createMemberRecord("S000033", title)));

            when(legislatorsRepoClient.fetchCommitteeMembershipCurrent()).thenReturn(data);
            when(committeeRepository.findByCommitteeCode("hsju00")).thenReturn(Optional.of(testCommittee));
            when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.of(testMember));
            when(membershipRepository.findByCongressionalMember_IdAndCommittee_CommitteeCodeAndCongress(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            syncService.syncAllMemberships();

            // Then
            ArgumentCaptor<CommitteeMembership> captor = ArgumentCaptor.forClass(CommitteeMembership.class);
            verify(membershipRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(MembershipRole.valueOf(expectedRole));
        }
    }

    // =========================================================================
    // Count Methods Tests
    // =========================================================================

    @Nested
    @DisplayName("Count Methods")
    class CountMethodsTests {

        @Test
        @DisplayName("getMembershipCount - Should return repository count")
        void getMembershipCount_returnsCount() {
            when(membershipRepository.count()).thenReturn(500L);
            assertThat(syncService.getMembershipCount()).isEqualTo(500L);
        }

        @Test
        @DisplayName("getMembershipCountByCongress - Should return count for congress")
        void getMembershipCountByCongress_returnsCount() {
            when(membershipRepository.countByCongress(119)).thenReturn(450L);
            assertThat(syncService.getMembershipCountByCongress(119)).isEqualTo(450L);
            verify(membershipRepository).countByCongress(119);
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Map<String, Object> createMemberRecord(String bioguideId, String title) {
        Map<String, Object> record = new HashMap<>();
        record.put("bioguide", bioguideId);
        record.put("name", "Test Member");
        record.put("party", "majority");
        record.put("rank", 1);
        if (title != null && !title.isEmpty()) {
            record.put("title", title);
        }
        return record;
    }

    private CongressionalMember createMember(String bioguideId) {
        CongressionalMember member = new CongressionalMember();
        member.setId(UUID.randomUUID());
        member.setBioguideId(bioguideId);
        member.setIndividualId(UUID.randomUUID());
        return member;
    }
}
