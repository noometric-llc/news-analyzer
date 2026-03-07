/**
 * useSyncJob - Polling hook for async sync job status
 *
 * Polls GET /api/admin/sync/jobs/{jobId} every 2 seconds while the job
 * is in RUNNING state. Stops polling automatically on COMPLETED or FAILED.
 *
 * Usage:
 *   const { data: job } = useSyncJob(activeJobId);
 *   // job?.state === 'COMPLETED' | 'RUNNING' | 'FAILED'
 */

import { useQuery } from '@tanstack/react-query';
import type { SyncJobStatus } from '@/types/sync';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

async function fetchSyncJob(jobId: string): Promise<SyncJobStatus> {
  const response = await fetch(`${API_BASE}/api/admin/sync/jobs/${jobId}`);
  if (!response.ok) {
    throw new Error('Failed to fetch sync job status');
  }
  return response.json();
}

export function useSyncJob(jobId: string | null) {
  return useQuery({
    queryKey: ['sync-job', jobId],
    queryFn: () => fetchSyncJob(jobId!),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const data = query.state.data as SyncJobStatus | undefined;
      // Poll every 2s while RUNNING, stop once completed/failed
      return data?.state === 'RUNNING' ? 2000 : false;
    },
  });
}
