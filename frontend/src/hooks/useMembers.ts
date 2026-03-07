/**
 * Members React Query Hooks
 *
 * React Query hooks for member-related API calls.
 */

import { useQuery, useMutation } from '@tanstack/react-query';
import { membersApi, type MemberListParams } from '@/lib/api/members';
import type { PaginationParams } from '@/types/pagination';

/**
 * Query key factory for members
 */
export const memberKeys = {
  all: ['members'] as const,
  lists: () => [...memberKeys.all, 'list'] as const,
  list: (params: MemberListParams) => [...memberKeys.lists(), params] as const,
  details: () => [...memberKeys.all, 'detail'] as const,
  detail: (bioguideId: string) => [...memberKeys.details(), bioguideId] as const,
  terms: (bioguideId: string) => [...memberKeys.detail(bioguideId), 'terms'] as const,
  committees: (bioguideId: string, params?: PaginationParams) =>
    [...memberKeys.detail(bioguideId), 'committees', params] as const,
  search: (name: string, params?: PaginationParams) =>
    [...memberKeys.all, 'search', { name, ...params }] as const,
  stats: () => [...memberKeys.all, 'stats'] as const,
  partyStats: () => [...memberKeys.stats(), 'party'] as const,
  stateStats: () => [...memberKeys.stats(), 'state'] as const,
  count: () => [...memberKeys.all, 'count'] as const,
  enrichmentStatus: () => [...memberKeys.all, 'enrichment-status'] as const,
};

/**
 * Hook to fetch paginated list of members
 */
export function useMembers(params: MemberListParams = {}) {
  return useQuery({
    queryKey: memberKeys.list(params),
    queryFn: () => membersApi.list(params),
  });
}

/**
 * Hook to fetch a single member by bioguide ID
 */
export function useMember(bioguideId: string) {
  return useQuery({
    queryKey: memberKeys.detail(bioguideId),
    queryFn: () => membersApi.getByBioguideId(bioguideId),
    enabled: !!bioguideId,
  });
}

/**
 * Hook to search members by name
 */
export function useMemberSearch(name: string, params: PaginationParams = {}) {
  return useQuery({
    queryKey: memberKeys.search(name, params),
    queryFn: () => membersApi.search(name, params),
    enabled: !!name && name.length >= 2,
  });
}

/**
 * Hook to fetch member term history
 */
export function useMemberTerms(bioguideId: string) {
  return useQuery({
    queryKey: memberKeys.terms(bioguideId),
    queryFn: () => membersApi.getTerms(bioguideId),
    enabled: !!bioguideId,
  });
}

/**
 * Hook to fetch member committee assignments
 */
export function useMemberCommittees(
  bioguideId: string,
  params: PaginationParams = {}
) {
  return useQuery({
    queryKey: memberKeys.committees(bioguideId, params),
    queryFn: () => membersApi.getCommittees(bioguideId, params),
    enabled: !!bioguideId,
  });
}

/**
 * Hook to fetch member statistics (party and state distribution)
 */
export function useMemberStats() {
  const partyQuery = useQuery({
    queryKey: memberKeys.partyStats(),
    queryFn: () => membersApi.getPartyStats(),
  });

  const stateQuery = useQuery({
    queryKey: memberKeys.stateStats(),
    queryFn: () => membersApi.getStateStats(),
  });

  return {
    partyStats: partyQuery.data,
    stateStats: stateQuery.data,
    isLoading: partyQuery.isLoading || stateQuery.isLoading,
    error: partyQuery.error || stateQuery.error,
  };
}

/**
 * Hook to fetch total member count
 */
export function useMemberCount() {
  return useQuery({
    queryKey: memberKeys.count(),
    queryFn: () => membersApi.getCount(),
  });
}

/**
 * Hook to fetch enrichment status
 */
export function useEnrichmentStatus() {
  return useQuery({
    queryKey: memberKeys.enrichmentStatus(),
    queryFn: () => membersApi.getEnrichmentStatus(),
  });
}

/**
 * Hook to trigger member sync (admin only)
 * Returns SyncJobStatus with jobId for polling via useSyncJob.
 * Query invalidation is handled by SyncButton on job completion.
 */
export function useMemberSync() {
  return useMutation({
    mutationFn: () => membersApi.triggerSync(),
  });
}

/**
 * Hook to trigger enrichment sync (admin only)
 * Returns SyncJobStatus with jobId for polling via useSyncJob.
 * Query invalidation is handled by SyncButton on job completion.
 */
export function useEnrichmentSync() {
  return useMutation({
    mutationFn: (force: boolean = false) => membersApi.triggerEnrichmentSync(force),
  });
}
