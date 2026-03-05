package org.newsanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.newsanalyzer.dto.FederalRegisterAgency;
import org.newsanalyzer.model.GovernmentOrganization;
import org.newsanalyzer.model.GovernmentOrganization.GovernmentBranch;
import org.newsanalyzer.model.GovernmentOrganization.OrganizationType;
import org.newsanalyzer.repository.GovernmentOrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for synchronizing government organization data from Federal Register API.
 *
 * Handles full sync with merge strategy that preserves manually curated fields
 * while updating from external API data.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@Service
@Transactional
public class GovernmentOrgSyncService {

    private static final Logger log = LoggerFactory.getLogger(GovernmentOrgSyncService.class);

    private static final int ACRONYM_MAX_LENGTH = 50;

    private final FederalRegisterClient federalRegisterClient;
    private final GovernmentOrganizationRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${gov-org.sync.max-new-orgs:50}")
    private int maxNewOrgs;

    private LocalDateTime lastSyncTime;

    public GovernmentOrgSyncService(FederalRegisterClient federalRegisterClient,
                                     GovernmentOrganizationRepository repository,
                                     ObjectMapper objectMapper) {
        this.federalRegisterClient = federalRegisterClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Sync result statistics.
     */
    public static class SyncResult {
        private int added;
        private int updated;
        private int skipped;
        private int errors;
        private List<String> errorMessages = new ArrayList<>();

        public int getAdded() { return added; }
        public int getUpdated() { return updated; }
        public int getSkipped() { return skipped; }
        public int getErrors() { return errors; }
        public List<String> getErrorMessages() { return errorMessages; }

        public void setAdded(int added) { this.added = added; }
        public void setUpdated(int updated) { this.updated = updated; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        public void setErrors(int errors) { this.errors = errors; }
        public void setErrorMessages(List<String> errorMessages) { this.errorMessages = errorMessages; }

        public void addError(String message) {
            this.errors++;
            this.errorMessages.add(message);
        }

        public int getTotal() {
            return added + updated + skipped + errors;
        }

        @Override
        public String toString() {
            return String.format("SyncResult{added=%d, updated=%d, skipped=%d, errors=%d}",
                    added, updated, skipped, errors);
        }
    }

    /**
     * Sync status information.
     */
    public static class SyncStatus {
        private LocalDateTime lastSync;
        private long totalOrganizations;
        private Map<String, Long> countByBranch;
        private boolean federalRegisterAvailable;
        private int maxNewOrgs;

        public LocalDateTime getLastSync() { return lastSync; }
        public long getTotalOrganizations() { return totalOrganizations; }
        public Map<String, Long> getCountByBranch() { return countByBranch; }
        public boolean isFederalRegisterAvailable() { return federalRegisterAvailable; }
        public int getMaxNewOrgs() { return maxNewOrgs; }

        public void setLastSync(LocalDateTime lastSync) { this.lastSync = lastSync; }
        public void setTotalOrganizations(long totalOrganizations) { this.totalOrganizations = totalOrganizations; }
        public void setCountByBranch(Map<String, Long> countByBranch) { this.countByBranch = countByBranch; }
        public void setFederalRegisterAvailable(boolean federalRegisterAvailable) { this.federalRegisterAvailable = federalRegisterAvailable; }
        public void setMaxNewOrgs(int maxNewOrgs) { this.maxNewOrgs = maxNewOrgs; }
    }

    /**
     * Perform full sync from Federal Register API.
     *
     * @return SyncResult with statistics
     */
    public SyncResult syncFromFederalRegister() {
        SyncResult result = new SyncResult();

        log.info("Starting sync from Federal Register API");
        LocalDateTime syncStartTime = LocalDateTime.now();

        List<FederalRegisterAgency> agencies = federalRegisterClient.fetchAllAgencies();

        if (agencies.isEmpty()) {
            log.warn("No agencies returned from Federal Register API");
            result.addError("No agencies returned from Federal Register API");
            return result;
        }

        log.info("Fetched {} agencies from Federal Register, beginning sync", agencies.size());

        // Build a map of Federal Register ID to agency for parent linking
        Map<Integer, FederalRegisterAgency> agencyMap = new HashMap<>();
        Map<Integer, UUID> frIdToDbId = new HashMap<>();

        for (FederalRegisterAgency agency : agencies) {
            agencyMap.put(agency.getId(), agency);
        }

        // First pass: sync all agencies
        boolean addLimitReached = false;
        for (FederalRegisterAgency agency : agencies) {
            try {
                SyncAction action = syncAgency(agency, result.getAdded() >= maxNewOrgs);
                switch (action.type) {
                    case ADDED:
                        result.setAdded(result.getAdded() + 1);
                        log.debug("Added new organization: {}", agency.getName());
                        if (result.getAdded() >= maxNewOrgs && !addLimitReached) {
                            addLimitReached = true;
                            log.info("Reached max-new-orgs limit ({}). Remaining unmatched agencies will be skipped.", maxNewOrgs);
                        }
                        break;
                    case UPDATED:
                        result.setUpdated(result.getUpdated() + 1);
                        log.debug("Updated organization: {}", agency.getName());
                        break;
                    case SKIPPED:
                        result.setSkipped(result.getSkipped() + 1);
                        log.debug("Skipped organization: {}", agency.getName());
                        break;
                }
                if (action.orgId != null && agency.getId() != null) {
                    frIdToDbId.put(agency.getId(), action.orgId);
                }
            } catch (Exception e) {
                result.addError(String.format("Failed to sync '%s': %s", agency.getName(), e.getMessage()));
                log.error("Failed to sync agency '{}': {}", agency.getName(), e.getMessage());
            }
        }

        // Second pass: link parent organizations using Federal Register parent_id
        int parentLinked = 0;
        for (FederalRegisterAgency agency : agencies) {
            if (agency.getParentId() != null) {
                UUID childDbId = frIdToDbId.get(agency.getId());
                UUID parentDbId = frIdToDbId.get(agency.getParentId());

                if (childDbId != null && parentDbId != null) {
                    try {
                        Optional<GovernmentOrganization> childOpt = repository.findById(childDbId);
                        if (childOpt.isPresent()) {
                            GovernmentOrganization child = childOpt.get();
                            // Only set parent if not already manually set
                            if (child.getParentId() == null) {
                                child.setParentId(parentDbId);
                                child.setOrgLevel(2); // Sub-agency
                                repository.save(child);
                                parentLinked++;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to link parent for '{}': {}", agency.getName(), e.getMessage());
                    }
                }
            }
        }

        if (parentLinked > 0) {
            log.info("Linked {} organizations to their parent organizations", parentLinked);
        }

        lastSyncTime = syncStartTime;
        log.info("Sync completed: {}", result);

        return result;
    }

    /**
     * Sync a single agency from Federal Register data.
     *
     * @param agency Federal Register agency data
     * @return SyncAction indicating what was done
     */
    private SyncAction syncAgency(FederalRegisterAgency agency, boolean addLimitReached) {
        // Try to match by acronym first (most reliable)
        Optional<GovernmentOrganization> existing = Optional.empty();

        if (agency.getShortName() != null && !agency.getShortName().isEmpty()) {
            existing = repository.findByAcronymIgnoreCase(agency.getShortName());
        }

        // If not found by acronym, try by official name
        if (existing.isEmpty()) {
            existing = repository.findByOfficialNameIgnoreCase(agency.getName());
        }

        if (existing.isPresent()) {
            GovernmentOrganization org = existing.get();
            boolean updated = updateOrganization(org, agency);
            repository.save(org);
            return new SyncAction(updated ? ActionType.UPDATED : ActionType.SKIPPED, org.getId());
        } else if (addLimitReached) {
            // Skip creating new org — limit reached
            log.debug("Skipped new organization (limit reached): {}", agency.getName());
            return new SyncAction(ActionType.SKIPPED, null);
        } else {
            // Create new organization
            GovernmentOrganization newOrg = createOrganization(agency);
            repository.save(newOrg);
            return new SyncAction(ActionType.ADDED, newOrg.getId());
        }
    }

    /**
     * Update an existing organization with Federal Register data.
     * Preserves manually curated fields.
     *
     * @param org Existing organization
     * @param agency Federal Register data
     * @return true if any field was updated
     */
    private boolean updateOrganization(GovernmentOrganization org, FederalRegisterAgency agency) {
        boolean updated = false;

        // Fix existing acronyms that exceed column limit (from prior bad sync data)
        if (org.getAcronym() != null && org.getAcronym().length() > ACRONYM_MAX_LENGTH) {
            log.warn("Clearing oversized acronym for '{}': '{}' ({} chars)", org.getOfficialName(), org.getAcronym(), org.getAcronym().length());
            org.setAcronym(null);
            updated = true;
        }

        // Update description only if currently null
        if (org.getDescription() == null && agency.getDescription() != null) {
            org.setDescription(agency.getDescription());
            updated = true;
        }

        // Always update metadata with Federal Register info
        ObjectNode metadata = org.getMetadata() != null
                ? (ObjectNode) org.getMetadata()
                : objectMapper.createObjectNode();

        metadata.put("federalRegisterId", agency.getId());
        metadata.put("federalRegisterUrl", agency.getUrl());
        if (agency.getSlug() != null) {
            metadata.put("federalRegisterSlug", agency.getSlug());
        }
        org.setMetadata(metadata);

        // Update acronym if we didn't have one (skip if shortName exceeds column limit — not a real acronym)
        if ((org.getAcronym() == null || org.getAcronym().isEmpty())
                && agency.getShortName() != null && !agency.getShortName().isEmpty()
                && agency.getShortName().length() <= ACRONYM_MAX_LENGTH) {
            org.setAcronym(agency.getShortName());
            updated = true;
        }

        org.setUpdatedBy("federal-register-sync");
        return updated || true; // Metadata always updates
    }

    /**
     * Create a new organization from Federal Register data.
     *
     * @param agency Federal Register data
     * @return New GovernmentOrganization entity
     */
    private GovernmentOrganization createOrganization(FederalRegisterAgency agency) {
        GovernmentOrganization org = new GovernmentOrganization();

        org.setOfficialName(agency.getName());
        // Only set acronym if it fits the column constraint (50 chars) — longer values are full names, not acronyms
        if (agency.getShortName() != null && agency.getShortName().length() <= ACRONYM_MAX_LENGTH) {
            org.setAcronym(agency.getShortName());
        }
        org.setDescription(agency.getDescription());

        // Federal Register only has executive branch agencies
        org.setBranch(GovernmentBranch.EXECUTIVE);
        org.setOrgType(inferOrgType(agency.getName()));

        // Default to top-level (will be updated in second pass if has parent)
        org.setOrgLevel(1);

        // Build metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("federalRegisterId", agency.getId());
        metadata.put("federalRegisterUrl", agency.getUrl());
        if (agency.getSlug() != null) {
            metadata.put("federalRegisterSlug", agency.getSlug());
        }
        org.setMetadata(metadata);

        org.setCreatedBy("federal-register-sync");
        org.setUpdatedBy("federal-register-sync");

        return org;
    }

    /**
     * Infer organization type from name patterns.
     *
     * @param name Organization name
     * @return Inferred OrganizationType
     */
    OrganizationType inferOrgType(String name) {
        if (name == null) {
            return OrganizationType.INDEPENDENT_AGENCY;
        }

        String lower = name.toLowerCase();

        if (lower.startsWith("department of")) {
            return OrganizationType.DEPARTMENT;
        }
        if (lower.contains("agency") || lower.contains("administration")) {
            return OrganizationType.INDEPENDENT_AGENCY;
        }
        if (lower.contains("bureau")) {
            return OrganizationType.BUREAU;
        }
        if (lower.contains("office")) {
            return OrganizationType.OFFICE;
        }
        if (lower.contains("commission")) {
            return OrganizationType.COMMISSION;
        }
        if (lower.contains("board")) {
            return OrganizationType.BOARD;
        }

        return OrganizationType.INDEPENDENT_AGENCY;
    }

    /**
     * Get current sync status.
     *
     * @return SyncStatus with current statistics
     */
    @Transactional(readOnly = true)
    public SyncStatus getStatus() {
        SyncStatus status = new SyncStatus();

        status.setLastSync(lastSyncTime);
        status.setTotalOrganizations(repository.countActive());
        status.setFederalRegisterAvailable(federalRegisterClient.isApiAvailable());
        status.setMaxNewOrgs(maxNewOrgs);

        // Count by branch
        Map<String, Long> countByBranch = new HashMap<>();
        List<Object[]> branchCounts = repository.countByBranch();
        for (Object[] row : branchCounts) {
            GovernmentBranch branch = (GovernmentBranch) row[0];
            Long count = (Long) row[1];
            countByBranch.put(branch.getValue(), count);
        }
        status.setCountByBranch(countByBranch);

        return status;
    }

    /**
     * Internal class to track sync action for each agency.
     */
    private static class SyncAction {
        final ActionType type;
        final UUID orgId;

        SyncAction(ActionType type, UUID orgId) {
            this.type = type;
            this.orgId = orgId;
        }
    }

    private enum ActionType {
        ADDED, UPDATED, SKIPPED
    }
}
