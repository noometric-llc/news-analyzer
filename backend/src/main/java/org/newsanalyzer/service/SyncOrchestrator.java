package org.newsanalyzer.service;

import org.newsanalyzer.scheduler.EnrichmentScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrator for async sync operations.
 *
 * Each method is annotated with @Async("syncExecutor") so it runs on the
 * dedicated sync thread pool. The pattern is:
 * 1. Call the actual sync service (blocking within the async thread)
 * 2. On success: registry.completeJob(jobId, result)
 * 3. On failure: registry.failJob(jobId, errorMessage)
 *
 * Important Spring @Async detail: these methods MUST be called from a different
 * bean (e.g., a controller) for the AOP proxy to intercept. Calling them from
 * within this class would execute synchronously.
 *
 * @since 2.0.0
 */
@Service
public class SyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SyncOrchestrator.class);

    private final SyncJobRegistry registry;
    private final MemberSyncService memberSyncService;
    private final CommitteeSyncService committeeSyncService;
    private final CommitteeMembershipSyncService membershipSyncService;
    private final GovernmentOrgSyncService govOrgSyncService;
    private final PlumCsvImportService plumImportService;
    private final UsCodeImportService usCodeImportService;
    private final PresidentialSyncService presidentialSyncService;
    private final ExecutiveOrderSyncService executiveOrderSyncService;
    private final EnrichmentScheduler enrichmentScheduler;
    private final TermSyncService termSyncService;

    public SyncOrchestrator(SyncJobRegistry registry,
                            MemberSyncService memberSyncService,
                            CommitteeSyncService committeeSyncService,
                            CommitteeMembershipSyncService membershipSyncService,
                            GovernmentOrgSyncService govOrgSyncService,
                            PlumCsvImportService plumImportService,
                            UsCodeImportService usCodeImportService,
                            PresidentialSyncService presidentialSyncService,
                            ExecutiveOrderSyncService executiveOrderSyncService,
                            EnrichmentScheduler enrichmentScheduler,
                            TermSyncService termSyncService) {
        this.registry = registry;
        this.memberSyncService = memberSyncService;
        this.committeeSyncService = committeeSyncService;
        this.membershipSyncService = membershipSyncService;
        this.govOrgSyncService = govOrgSyncService;
        this.plumImportService = plumImportService;
        this.usCodeImportService = usCodeImportService;
        this.presidentialSyncService = presidentialSyncService;
        this.executiveOrderSyncService = executiveOrderSyncService;
        this.enrichmentScheduler = enrichmentScheduler;
        this.termSyncService = termSyncService;
    }

    @Async("syncExecutor")
    public void runMemberSync(String jobId) {
        try {
            log.info("Starting async member sync (jobId={})", jobId);
            MemberSyncService.SyncResult result = memberSyncService.syncAllCurrentMembers();
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Member sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runCommitteeSync(String jobId) {
        try {
            log.info("Starting async committee sync (jobId={})", jobId);
            CommitteeSyncService.SyncResult result = committeeSyncService.syncAllCommittees();
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Committee sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runMembershipSync(String jobId, int congress) {
        try {
            log.info("Starting async membership sync (jobId={}, congress={})", jobId, congress);
            CommitteeMembershipSyncService.SyncResult result = membershipSyncService.syncAllMemberships(congress);
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Membership sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runGovOrgSync(String jobId) {
        try {
            log.info("Starting async gov org sync (jobId={})", jobId);
            GovernmentOrgSyncService.SyncResult result = govOrgSyncService.syncFromFederalRegister();
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Gov org sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runPlumImport(String jobId, Integer offset, Integer limit) {
        try {
            log.info("Starting async PLUM import (jobId={}, offset={}, limit={})", jobId, offset, limit);
            var result = plumImportService.importFromUrl(offset, limit);
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("PLUM import failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runUsCodeImportAll(String jobId, String releasePoint) {
        try {
            log.info("Starting async US Code full import (jobId={})", jobId);
            var result = usCodeImportService.importAllTitles(releasePoint);
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("US Code import failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runUsCodeImportTitle(String jobId, int titleNumber, String releasePoint) {
        try {
            log.info("Starting async US Code Title {} import (jobId={})", titleNumber, jobId);
            var result = usCodeImportService.importTitle(titleNumber, releasePoint);
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("US Code Title {} import failed (jobId={}): {}", titleNumber, jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runPresidentialSync(String jobId) {
        try {
            log.info("Starting async presidential sync (jobId={})", jobId);
            var result = presidentialSyncService.syncFromSeedFile();
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Presidential sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runExecutiveOrderSync(String jobId) {
        try {
            log.info("Starting async executive order sync (jobId={})", jobId);
            var result = executiveOrderSyncService.syncAllExecutiveOrders();
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Executive order sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runExecutiveOrderSyncForPresidency(String jobId, int presidencyNumber) {
        try {
            log.info("Starting async EO sync for presidency #{} (jobId={})", presidencyNumber, jobId);
            var result = executiveOrderSyncService.syncExecutiveOrdersForPresidency(presidencyNumber);
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("EO sync for presidency #{} failed (jobId={}): {}", presidencyNumber, jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runEnrichmentSync(String jobId, boolean force) {
        try {
            log.info("Starting async enrichment sync (jobId={}, force={})", jobId, force);
            var result = enrichmentScheduler.triggerManualSync(force);
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Enrichment sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }

    @Async("syncExecutor")
    public void runTermSync(String jobId) {
        try {
            log.info("Starting async term sync (jobId={})", jobId);
            var result = termSyncService.syncAllCurrentMemberTerms();
            registry.completeJob(jobId, result);
        } catch (Exception e) {
            log.error("Term sync failed (jobId={}): {}", jobId, e.getMessage(), e);
            registry.failJob(jobId, e.getMessage());
        }
    }
}
