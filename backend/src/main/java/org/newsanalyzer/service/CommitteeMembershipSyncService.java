package org.newsanalyzer.service;

import org.newsanalyzer.model.Committee;
import org.newsanalyzer.model.CommitteeMembership;
import org.newsanalyzer.model.CongressionalMember;
import org.newsanalyzer.model.MembershipRole;
import org.newsanalyzer.repository.CommitteeMembershipRepository;
import org.newsanalyzer.repository.CommitteeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for synchronizing committee membership data from the
 * unitedstates/congress-legislators GitHub repository.
 *
 * The Congress.gov API v3 does not expose committee membership data,
 * so this service uses the community-maintained YAML files instead.
 *
 * Part of ARCH-1.6: Updated to use CongressionalMember instead of Person.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 * @see <a href="https://github.com/unitedstates/congress-legislators">Data source</a>
 */
@Service
public class CommitteeMembershipSyncService {

    private static final Logger log = LoggerFactory.getLogger(CommitteeMembershipSyncService.class);
    private static final String DATA_SOURCE = "LEGISLATORS_REPO";

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    private final LegislatorsRepoClient legislatorsRepoClient;
    private final CommitteeMembershipRepository membershipRepository;
    private final CommitteeRepository committeeRepository;
    private final CongressionalMemberService congressionalMemberService;

    // Default to current Congress (can be configured)
    private static final int CURRENT_CONGRESS = 119;

    public CommitteeMembershipSyncService(LegislatorsRepoClient legislatorsRepoClient,
                                          CommitteeMembershipRepository membershipRepository,
                                          CommitteeRepository committeeRepository,
                                          CongressionalMemberService congressionalMemberService) {
        this.legislatorsRepoClient = legislatorsRepoClient;
        this.membershipRepository = membershipRepository;
        this.committeeRepository = committeeRepository;
        this.congressionalMemberService = congressionalMemberService;
    }

    /**
     * Sync result statistics
     */
    public static class SyncResult {
        private int added;
        private int updated;
        private int skipped;
        private int errors;
        private int total;

        public int getAdded() { return added; }
        public int getUpdated() { return updated; }
        public int getSkipped() { return skipped; }
        public int getErrors() { return errors; }
        public int getTotal() { return total; }

        @Override
        public String toString() {
            return String.format("SyncResult{added=%d, updated=%d, skipped=%d, errors=%d, total=%d}",
                    added, updated, skipped, errors, total);
        }
    }

    /**
     * Sync memberships for all committees in the current Congress.
     *
     * @return SyncResult with statistics
     */
    public SyncResult syncAllMemberships() {
        return syncAllMemberships(CURRENT_CONGRESS);
    }

    /**
     * Sync memberships for all committees in a specific Congress.
     *
     * Fetches committee-membership-current.yaml from the unitedstates/congress-legislators
     * GitHub repository and creates CommitteeMembership records by matching:
     * - Thomas committee IDs → Committee entities (via committee_code)
     * - BioGuide IDs → CongressionalMember entities
     *
     * @param congress Congress session number (e.g., 119)
     * @return SyncResult with statistics
     */
    @Transactional(noRollbackFor = Exception.class)
    public SyncResult syncAllMemberships(int congress) {
        SyncResult result = new SyncResult();

        if (!syncInProgress.compareAndSet(false, true)) {
            log.warn("Membership sync already in progress, skipping duplicate request");
            result.errors++;
            return result;
        }

        try {
            log.info("Starting sync of committee memberships for Congress {}", congress);

            // Fetch committee membership data from GitHub repo
            Map<String, List<Map<String, Object>>> membershipData =
                    legislatorsRepoClient.fetchCommitteeMembershipCurrent();

            if (membershipData.isEmpty()) {
                log.error("Failed to fetch committee membership data from GitHub repo");
                result.errors++;
                return result;
            }

            result.total = membershipData.size();
            log.info("Fetched membership data for {} committees from GitHub repo", membershipData.size());

            for (Map.Entry<String, List<Map<String, Object>>> entry : membershipData.entrySet()) {
                String thomasId = entry.getKey();
                List<Map<String, Object>> members = entry.getValue();

                try {
                    // Convert Thomas ID to committee code and find committee
                    String committeeCode = thomasIdToCommitteeCode(thomasId);
                    Optional<Committee> committeeOpt = committeeRepository.findByCommitteeCode(committeeCode);

                    if (committeeOpt.isEmpty()) {
                        log.debug("Committee not found for Thomas ID {} (tried code: {})", thomasId, committeeCode);
                        result.skipped++;
                        continue;
                    }

                    Committee committee = committeeOpt.get();

                    for (Map<String, Object> memberRecord : members) {
                        try {
                            boolean isNew = syncMembershipFromYaml(memberRecord, committee, congress);
                            if (isNew) {
                                result.added++;
                            } else {
                                result.updated++;
                            }
                        } catch (Exception e) {
                            log.debug("Skipped member on {}: {}", thomasId, e.getMessage());
                            result.skipped++;
                        }
                    }

                } catch (Exception e) {
                    result.errors++;
                    log.error("Failed to sync memberships for committee {}: {}", thomasId, e.getMessage());
                }
            }

            log.info("Membership sync completed: {}", result);
            return result;
        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * Sync a single membership from YAML record data.
     *
     * YAML record format: {name: "...", bioguide: "X000123", party: "majority", rank: 1, title: "Chair"}
     *
     * @param record YAML member record
     * @param committee Committee entity
     * @param congress Congress session number
     * @return true if new membership created, false if updated
     */
    private boolean syncMembershipFromYaml(Map<String, Object> record, Committee committee, int congress) {
        String bioguideId = (String) record.get("bioguide");

        if (bioguideId == null || bioguideId.isEmpty()) {
            throw new IllegalArgumentException("Member record missing bioguide ID");
        }

        // Find the CongressionalMember
        Optional<CongressionalMember> memberOpt = congressionalMemberService.findByBioguideId(bioguideId);
        if (memberOpt.isEmpty()) {
            throw new IllegalArgumentException("CongressionalMember not found: " + bioguideId);
        }

        CongressionalMember member = memberOpt.get();

        // Check if membership already exists
        Optional<CommitteeMembership> existing = membershipRepository
                .findByCongressionalMember_IdAndCommittee_CommitteeCodeAndCongress(
                        member.getId(),
                        committee.getCommitteeCode(),
                        congress
                );

        CommitteeMembership membership = existing.orElse(new CommitteeMembership());
        boolean isNew = existing.isEmpty();

        // Map data
        membership.setCongressionalMember(member);
        membership.setCommittee(committee);
        membership.setCongress(congress);
        membership.setCongressLastSync(LocalDateTime.now());
        membership.setDataSource(DATA_SOURCE);

        // Map role from title field (e.g., "Chairman", "Ranking Member", "Vice Chair")
        String title = record.get("title") != null ? record.get("title").toString() : null;
        membership.setRole(mapRole(title));

        membershipRepository.save(membership);

        if (isNew) {
            log.debug("Added membership: {} on {} ({})",
                    bioguideId, committee.getName(), membership.getRole());
        }

        return isNew;
    }

    /**
     * Convert a Thomas committee ID to a Congress.gov system code.
     *
     * Thomas IDs use uppercase with no trailing digits for parent committees:
     *   SSJU → ssju00, HSJU → hsju00
     * Subcommittees include the number:
     *   SSJU13 → ssju13, HSAP01 → hsap01
     */
    static String thomasIdToCommitteeCode(String thomasId) {
        if (thomasId == null || thomasId.isEmpty()) {
            throw new IllegalArgumentException("Thomas ID cannot be null or empty");
        }

        String lower = thomasId.toLowerCase();

        // Check if it ends with digits (subcommittee)
        int firstDigitIdx = -1;
        for (int i = 0; i < lower.length(); i++) {
            if (Character.isDigit(lower.charAt(i))) {
                firstDigitIdx = i;
                break;
            }
        }

        if (firstDigitIdx == -1) {
            // No digits — parent committee, append "00"
            return lower + "00";
        }

        // Has digits — already a complete code
        return lower;
    }

    /**
     * Map role/title string to MembershipRole enum.
     */
    private MembershipRole mapRole(String roleStr) {
        if (roleStr == null || roleStr.isEmpty()) {
            return MembershipRole.MEMBER;
        }

        String normalized = roleStr.toUpperCase();
        if (normalized.contains("CHAIR") && !normalized.contains("VICE")) {
            return MembershipRole.CHAIR;
        } else if (normalized.contains("VICE") && normalized.contains("CHAIR")) {
            return MembershipRole.VICE_CHAIR;
        } else if (normalized.contains("RANKING")) {
            return MembershipRole.RANKING_MEMBER;
        } else if (normalized.contains("EX OFFICIO") || normalized.contains("EX-OFFICIO")) {
            return MembershipRole.EX_OFFICIO;
        }
        return MembershipRole.MEMBER;
    }

    /**
     * Get count of synced memberships.
     */
    public long getMembershipCount() {
        return membershipRepository.count();
    }

    /**
     * Get count of memberships for a specific congress.
     */
    public long getMembershipCountByCongress(int congress) {
        return membershipRepository.countByCongress(congress);
    }
}
