/**
 * PLUM Sync React Query Hooks
 *
 * React Query hooks for PLUM (executive branch appointees) sync operations.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { PlumSyncStatus, PlumImportResult } from '@/types/plum';
import type { SyncJobStatus } from '@/types/sync';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Query key factory for PLUM sync
 */
export const plumKeys = {
  all: ['plum'] as const,
  syncStatus: () => [...plumKeys.all, 'sync-status'] as const,
  lastResult: () => [...plumKeys.all, 'last-result'] as const,
};

/**
 * Fetch PLUM sync status from the API
 */
async function fetchPlumSyncStatus(): Promise<PlumSyncStatus> {
  const response = await fetch(`${API_BASE}/api/admin/sync/plum/status`);
  if (!response.ok) {
    throw new Error('Failed to fetch PLUM sync status');
  }
  return response.json();
}

/**
 * Fetch last PLUM import result
 */
async function fetchPlumLastResult(): Promise<PlumImportResult | null> {
  const response = await fetch(`${API_BASE}/api/admin/sync/plum/last-result`);
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error('Failed to fetch PLUM last result');
  }
  return response.json();
}

/**
 * Trigger PLUM sync from OPM CSV (async — returns job status)
 */
async function triggerPlumSync(): Promise<SyncJobStatus> {
  const response = await fetch(`${API_BASE}/api/admin/sync/plum`, {
    method: 'POST',
  });
  if (response.status === 409) {
    throw new Error('PLUM import already in progress');
  }
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || 'PLUM sync failed');
  }
  return response.json();
}

/**
 * Hook to fetch PLUM sync status
 */
export function usePlumSyncStatus() {
  return useQuery({
    queryKey: plumKeys.syncStatus(),
    queryFn: fetchPlumSyncStatus,
    staleTime: 30 * 1000, // 30 seconds
    refetchOnWindowFocus: true,
    refetchInterval: (query) => {
      // Poll more frequently if sync is in progress
      const data = query.state.data as PlumSyncStatus | undefined;
      return data?.inProgress ? 5000 : false;
    },
  });
}

/**
 * Hook to fetch last PLUM import result
 */
export function usePlumLastResult() {
  return useQuery({
    queryKey: plumKeys.lastResult(),
    queryFn: fetchPlumLastResult,
    staleTime: 60 * 1000, // 60 seconds
  });
}

/**
 * Hook to trigger PLUM sync (admin only)
 * Returns SyncJobStatus with jobId. The existing status polling
 * (usePlumSyncStatus with refetchInterval) handles progress tracking.
 */
export function usePlumSync() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: triggerPlumSync,
    onSuccess: () => {
      // Immediately refetch sync status so polling picks up the running state
      queryClient.invalidateQueries({ queryKey: plumKeys.syncStatus() });
    },
  });
}
