package org.newsanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.model.CongressionalMember;
import org.newsanalyzer.model.Chamber;
import org.newsanalyzer.model.Individual;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemberSyncService.
 *
 * Part of ARCH-1.7: Updated to use CongressionalMemberService and IndividualService
 * instead of PersonRepository.
 *
 * @author James (Dev Agent)
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
class MemberSyncServiceTest {

    @Mock
    private CongressApiClient congressApiClient;

    @Mock
    private CongressionalMemberService congressionalMemberService;

    @Mock
    private IndividualService individualService;

    private ObjectMapper objectMapper;
    private MemberSyncService syncService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        syncService = new MemberSyncService(
                congressApiClient,
                congressionalMemberService,
                individualService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should return empty result when API not configured")
    void syncAllCurrentMembers_apiNotConfigured_returnsEmptyResult() {
        // Given
        when(congressApiClient.isConfigured()).thenReturn(false);

        // When
        MemberSyncService.SyncResult result = syncService.syncAllCurrentMembers();

        // Then
        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getAdded()).isEqualTo(0);
        assertThat(result.getUpdated()).isEqualTo(0);
        verify(congressApiClient, never()).fetchAllCurrentMembers();
    }

    @Test
    @DisplayName("Should sync new member correctly")
    void syncMember_newMember_createsRecord() throws Exception {
        // Given - API returns "name" in "LastName, FirstName" format, "partyName", and terms.item array
        String memberJson = """
            {
              "bioguideId": "S000033",
              "name": "Sanders, Bernard",
              "partyName": "Independent",
              "state": "VT",
              "terms": {"item": [{"chamber": "Senate"}]}
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        UUID individualId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        Individual individual = new Individual();
        individual.setId(individualId);
        individual.setFirstName("Bernard");
        individual.setLastName("Sanders");

        CongressionalMember member = new CongressionalMember();
        member.setId(memberId);
        member.setBioguideId("S000033");
        member.setIndividualId(individualId);
        member.setIndividual(individual);
        member.setChamber(Chamber.SENATE);
        member.setState("VT");

        when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.empty());
        when(congressionalMemberService.findOrCreate(
                eq("S000033"),
                eq("Bernard"),
                eq("Sanders"),
                any(),
                eq(Chamber.SENATE),
                eq("VT"),
                eq("Independent")
        )).thenReturn(member);
        // Note: individualService.findById not stubbed because no imageUrl/middleName/gender in test data
        when(congressionalMemberService.save(any(CongressionalMember.class))).thenReturn(member);

        // When
        boolean isNew = syncService.syncMember(memberData);

        // Then
        assertThat(isNew).isTrue();
        verify(congressionalMemberService).findOrCreate(
                eq("S000033"),
                eq("Bernard"),
                eq("Sanders"),
                any(),
                eq(Chamber.SENATE),
                eq("VT"),
                eq("Independent")
        );
    }

    @Test
    @DisplayName("Should update existing member")
    void syncMember_existingMember_updatesRecord() throws Exception {
        // Given - API returns "name" in "LastName, FirstName" format
        String memberJson = """
            {
              "bioguideId": "S000033",
              "name": "Sanders, Bernard",
              "partyName": "Independent",
              "state": "VT"
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        UUID individualId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        Individual individual = new Individual();
        individual.setId(individualId);
        individual.setFirstName("Bernie"); // Old name

        CongressionalMember existingMember = new CongressionalMember();
        existingMember.setId(memberId);
        existingMember.setBioguideId("S000033");
        existingMember.setIndividualId(individualId);
        existingMember.setIndividual(individual);

        when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.of(existingMember));
        when(congressionalMemberService.findOrCreate(
                anyString(), anyString(), anyString(), any(), any(), anyString(), anyString()
        )).thenReturn(existingMember);
        // Note: individualService.findById not stubbed because no imageUrl/middleName/gender in test data
        when(congressionalMemberService.save(any(CongressionalMember.class))).thenReturn(existingMember);

        // When
        boolean isNew = syncService.syncMember(memberData);

        // Then
        assertThat(isNew).isFalse();
    }

    @Test
    @DisplayName("Should throw exception for missing bioguideId")
    void syncMember_missingBioguideId_throwsException() throws Exception {
        // Given
        String memberJson = """
            {
              "firstName": "John",
              "lastName": "Doe"
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        // When/Then
        assertThatThrownBy(() -> syncService.syncMember(memberData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bioguideId");
    }

    @Test
    @DisplayName("Should map HOUSE chamber correctly")
    void syncMember_houseMember_mapsChamberCorrectly() throws Exception {
        // Given - API returns terms.item array structure
        String memberJson = """
            {
              "bioguideId": "P000197",
              "name": "Pelosi, Nancy",
              "terms": {"item": [{"chamber": "House of Representatives"}]}
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        UUID individualId = UUID.randomUUID();
        Individual individual = new Individual();
        individual.setId(individualId);
        individual.setFirstName("Nancy");
        individual.setLastName("Pelosi");

        CongressionalMember member = new CongressionalMember();
        member.setId(UUID.randomUUID());
        member.setBioguideId("P000197");
        member.setIndividualId(individualId);
        member.setIndividual(individual);
        member.setChamber(Chamber.HOUSE);

        when(congressionalMemberService.findByBioguideId("P000197")).thenReturn(Optional.empty());
        when(congressionalMemberService.findOrCreate(
                eq("P000197"),
                eq("Nancy"),
                eq("Pelosi"),
                any(),
                eq(Chamber.HOUSE),
                any(),
                any()
        )).thenReturn(member);
        // Note: individualService.findById not stubbed because no imageUrl/middleName/gender in test data
        when(congressionalMemberService.save(any(CongressionalMember.class))).thenReturn(member);

        // When
        syncService.syncMember(memberData);

        // Then
        verify(congressionalMemberService).findOrCreate(
                eq("P000197"),
                eq("Nancy"),
                eq("Pelosi"),
                any(),
                eq(Chamber.HOUSE),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should parse birth year correctly")
    void syncMember_withBirthYear_parsesBirthDate() throws Exception {
        // Given
        String memberJson = """
            {
              "bioguideId": "S000033",
              "name": "Sanders, Bernard",
              "birthYear": "1941"
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        UUID individualId = UUID.randomUUID();
        Individual individual = new Individual();
        individual.setId(individualId);
        individual.setFirstName("Bernard");
        individual.setLastName("Sanders");

        CongressionalMember member = new CongressionalMember();
        member.setId(UUID.randomUUID());
        member.setBioguideId("S000033");
        member.setIndividualId(individualId);
        member.setIndividual(individual);

        when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.empty());
        when(congressionalMemberService.findOrCreate(
                eq("S000033"),
                eq("Bernard"),
                eq("Sanders"),
                eq(LocalDate.of(1941, 1, 1)),
                any(),
                any(),
                any()
        )).thenReturn(member);
        // Note: individualService.findById not stubbed because no imageUrl/middleName/gender in test data
        when(congressionalMemberService.save(any(CongressionalMember.class))).thenReturn(member);

        // When
        syncService.syncMember(memberData);

        // Then
        verify(congressionalMemberService).findOrCreate(
                eq("S000033"),
                eq("Bernard"),
                eq("Sanders"),
                eq(LocalDate.of(1941, 1, 1)),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should extract image URL from depiction")
    void syncMember_withDepiction_extractsImageUrl() throws Exception {
        // Given
        String memberJson = """
            {
              "bioguideId": "S000033",
              "name": "Sanders, Bernard",
              "depiction": {
                "imageUrl": "https://example.com/sanders.jpg"
              }
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        UUID individualId = UUID.randomUUID();
        Individual individual = new Individual();
        individual.setId(individualId);
        individual.setFirstName("Bernard");
        individual.setLastName("Sanders");
        individual.setImageUrl(null); // Not set yet

        CongressionalMember member = new CongressionalMember();
        member.setId(UUID.randomUUID());
        member.setBioguideId("S000033");
        member.setIndividualId(individualId);
        member.setIndividual(individual);

        when(congressionalMemberService.findByBioguideId("S000033")).thenReturn(Optional.empty());
        when(congressionalMemberService.findOrCreate(
                anyString(), anyString(), anyString(), any(), any(), any(), any()
        )).thenReturn(member);
        when(individualService.findById(individualId)).thenReturn(Optional.of(individual));
        when(congressionalMemberService.save(any(CongressionalMember.class))).thenReturn(member);

        // When
        syncService.syncMember(memberData);

        // Then
        // Verify that individual was saved with the imageUrl
        ArgumentCaptor<Individual> individualCaptor = ArgumentCaptor.forClass(Individual.class);
        verify(individualService).save(individualCaptor.capture());
        assertThat(individualCaptor.getValue().getImageUrl()).isEqualTo("https://example.com/sanders.jpg");
    }

    @Test
    @DisplayName("Should sync member with terms as direct array format")
    void syncMember_directArrayTerms_mapsChamberCorrectly() throws Exception {
        // Given - API returns terms as direct array (not nested in "item" object)
        String memberJson = """
            {
              "bioguideId": "W000817",
              "name": "Warren, Elizabeth",
              "partyName": "Democratic",
              "state": "MA",
              "terms": [{"chamber": "Senate", "congress": 118, "startYear": 2023}]
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        UUID individualId = UUID.randomUUID();
        Individual individual = new Individual();
        individual.setId(individualId);
        individual.setFirstName("Elizabeth");
        individual.setLastName("Warren");

        CongressionalMember member = new CongressionalMember();
        member.setId(UUID.randomUUID());
        member.setBioguideId("W000817");
        member.setIndividualId(individualId);
        member.setIndividual(individual);
        member.setChamber(Chamber.SENATE);

        when(congressionalMemberService.findByBioguideId("W000817")).thenReturn(Optional.empty());
        when(congressionalMemberService.findOrCreate(
                eq("W000817"),
                eq("Elizabeth"),
                eq("Warren"),
                any(),
                eq(Chamber.SENATE),
                eq("MA"),
                eq("Democratic")
        )).thenReturn(member);
        when(congressionalMemberService.save(any(CongressionalMember.class))).thenReturn(member);

        // When
        boolean isNew = syncService.syncMember(memberData);

        // Then
        assertThat(isNew).isTrue();
        verify(congressionalMemberService).findOrCreate(
                eq("W000817"), eq("Elizabeth"), eq("Warren"),
                any(), eq(Chamber.SENATE), eq("MA"), eq("Democratic")
        );
    }

    @Test
    @DisplayName("Should handle missing terms node gracefully")
    void syncMember_noTerms_handlesGracefully() throws Exception {
        // Given - API response with no terms at all
        String memberJson = """
            {
              "bioguideId": "T000001",
              "name": "Test, Member",
              "partyName": "Republican",
              "state": "TX"
            }
            """;
        JsonNode memberData = objectMapper.readTree(memberJson);

        UUID individualId = UUID.randomUUID();
        Individual individual = new Individual();
        individual.setId(individualId);

        CongressionalMember member = new CongressionalMember();
        member.setId(UUID.randomUUID());
        member.setBioguideId("T000001");
        member.setIndividualId(individualId);
        member.setIndividual(individual);

        when(congressionalMemberService.findByBioguideId("T000001")).thenReturn(Optional.empty());
        when(congressionalMemberService.findOrCreate(
                eq("T000001"), eq("Member"), eq("Test"),
                any(), isNull(), eq("TX"), eq("Republican")
        )).thenReturn(member);
        when(congressionalMemberService.save(any(CongressionalMember.class))).thenReturn(member);

        // When
        boolean isNew = syncService.syncMember(memberData);

        // Then - chamber should be null since no terms
        assertThat(isNew).isTrue();
        verify(congressionalMemberService).findOrCreate(
                eq("T000001"), eq("Member"), eq("Test"),
                any(), isNull(), eq("TX"), eq("Republican")
        );
    }

    @Test
    @DisplayName("Should return member count")
    void getMemberCount_returnsTotalCount() {
        // Given
        when(congressionalMemberService.count()).thenReturn(535L);

        // When
        long count = syncService.getMemberCount();

        // Then
        assertThat(count).isEqualTo(535L);
    }
}
