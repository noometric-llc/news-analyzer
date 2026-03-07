package org.newsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * DTO representing the status of an async sync job.
 *
 * Returned by all sync POST endpoints (HTTP 202) and by the job polling
 * endpoints (GET /api/admin/sync/jobs/{jobId}).
 *
 * The {@code result} field holds the service-specific result object
 * (e.g., MemberSyncService.SyncResult) and is only populated on COMPLETED.
 *
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncJobStatus {

    public enum State {
        RUNNING, COMPLETED, FAILED
    }

    private String jobId;
    private String syncType;
    private State state;
    private Instant startedAt;
    private Instant completedAt;
    private Object result;
    private String errorMessage;

    public SyncJobStatus() {}

    public SyncJobStatus(String jobId, String syncType, State state, Instant startedAt) {
        this.jobId = jobId;
        this.syncType = syncType;
        this.state = state;
        this.startedAt = startedAt;
    }

    // Getters and setters

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getSyncType() {
        return syncType;
    }

    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
