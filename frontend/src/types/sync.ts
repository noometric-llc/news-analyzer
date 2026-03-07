/**
 * Sync Job Types
 *
 * TypeScript interfaces matching the backend SyncJobStatus DTO.
 * Used by the useSyncJob polling hook and SyncButton component.
 */

export type SyncJobState = 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface SyncJobStatus {
  jobId: string;
  syncType: string;
  state: SyncJobState;
  startedAt: string;
  completedAt?: string;
  result?: unknown;
  errorMessage?: string;
}
