package org.newsanalyzer.service;

import org.newsanalyzer.dto.SyncJobStatus;
import org.newsanalyzer.dto.SyncJobStatus.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized registry tracking all async sync job state.
 *
 * Replaces the scattered AtomicBoolean + lastResult fields across
 * AdminSyncController, MemberSyncService, GovernmentOrgSyncService, etc.
 *
 * Thread-safe: uses ConcurrentHashMap and synchronized blocks where needed.
 * Keeps last 20 completed/failed jobs in memory for polling after completion.
 *
 * @since 2.0.0
 */
@Service
public class SyncJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(SyncJobRegistry.class);
    private static final int MAX_COMPLETED_JOBS = 20;

    private final ConcurrentHashMap<String, SyncJobStatus> jobs = new ConcurrentHashMap<>();

    /**
     * Start a new sync job. Enforces one-sync-per-type constraint.
     *
     * @param syncType identifier like "members", "committees", "plum", etc.
     * @return the new job's status
     * @throws IllegalStateException if a job of this type is already RUNNING
     */
    public synchronized SyncJobStatus startJob(String syncType) {
        // Check if there's already a running job of this type
        boolean alreadyRunning = jobs.values().stream()
                .anyMatch(j -> j.getSyncType().equals(syncType) && j.getState() == State.RUNNING);
        if (alreadyRunning) {
            throw new IllegalStateException("Sync already in progress for type: " + syncType);
        }

        String jobId = UUID.randomUUID().toString();
        SyncJobStatus status = new SyncJobStatus(jobId, syncType, State.RUNNING, Instant.now());
        jobs.put(jobId, status);

        evictOldCompletedJobs();
        log.info("Started sync job {} (type={})", jobId, syncType);
        return status;
    }

    /**
     * Mark a job as completed with its result.
     */
    public void completeJob(String jobId, Object result) {
        SyncJobStatus status = jobs.get(jobId);
        if (status == null) {
            log.warn("Attempted to complete unknown job: {}", jobId);
            return;
        }
        status.setState(State.COMPLETED);
        status.setCompletedAt(Instant.now());
        status.setResult(result);
        log.info("Completed sync job {} (type={})", jobId, status.getSyncType());
    }

    /**
     * Mark a job as failed with an error message.
     */
    public void failJob(String jobId, String errorMessage) {
        SyncJobStatus status = jobs.get(jobId);
        if (status == null) {
            log.warn("Attempted to fail unknown job: {}", jobId);
            return;
        }
        status.setState(State.FAILED);
        status.setCompletedAt(Instant.now());
        status.setErrorMessage(errorMessage);
        log.error("Failed sync job {} (type={}): {}", jobId, status.getSyncType(), errorMessage);
    }

    /**
     * Get a specific job's status.
     */
    public Optional<SyncJobStatus> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * Check if a sync of the given type is currently running.
     */
    public boolean isRunning(String syncType) {
        return jobs.values().stream()
                .anyMatch(j -> j.getSyncType().equals(syncType) && j.getState() == State.RUNNING);
    }

    /**
     * Get all jobs (most recent first).
     */
    public List<SyncJobStatus> getAllJobs() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(SyncJobStatus::getStartedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Evict oldest completed/failed jobs beyond the retention limit.
     */
    private void evictOldCompletedJobs() {
        List<Map.Entry<String, SyncJobStatus>> completedJobs = jobs.entrySet().stream()
                .filter(e -> e.getValue().getState() != State.RUNNING)
                .sorted(Comparator.comparing(e -> e.getValue().getStartedAt()))
                .collect(Collectors.toList());

        while (completedJobs.size() > MAX_COMPLETED_JOBS) {
            Map.Entry<String, SyncJobStatus> oldest = completedJobs.remove(0);
            jobs.remove(oldest.getKey());
            log.debug("Evicted old sync job: {}", oldest.getKey());
        }
    }
}
