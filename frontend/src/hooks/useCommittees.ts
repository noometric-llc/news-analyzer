/**
 * Committees React Query Hooks
 *
 * React Query hooks for committee-related API calls.
 */

import { useQuery, useMutation } from '@tanstack/react-query';
import { committeesApi, type CommitteeListParams } from '@/lib/api/committees';
import type { CommitteeChamber } from '@/types/committee';
import type { PaginationParams } from '@/types/pagination';

/**
 * Query key factory for committees
 */
export const committeeKeys = {
  all: ['committees'] as const,
  lists: () => [...committeeKeys.all, 'list'] as const,
  list: (params: CommitteeListParams) => [...committeeKeys.lists(), params] as const,
  details: () => [...committeeKeys.all, 'detail'] as const,
  detail: (code: string) => [...committeeKeys.details(), code] as const,
  members: (code: string, params?: PaginationParams) =>
    [...committeeKeys.detail(code), 'members', params] as const,
  subcommittees: (code: string, params?: PaginationParams) =>
    [...committeeKeys.detail(code), 'subcommittees', params] as const,
  byChamber: (chamber: CommitteeChamber, params?: PaginationParams) =>
    [...committeeKeys.all, 'by-chamber', chamber, params] as const,
  search: (name: string, params?: PaginationParams) =>
    [...committeeKeys.all, 'search', { name, ...params }] as const,
  count: () => [...committeeKeys.all, 'count'] as const,
};

/**
 * Hook to fetch paginated list of committees
 */
export function useCommittees(params: CommitteeListParams = {}) {
  return useQuery({
    queryKey: committeeKeys.list(params),
    queryFn: () => committeesApi.list(params),
  });
}

/**
 * Hook to fetch a single committee by code
 */
export function useCommittee(code: string) {
  return useQuery({
    queryKey: committeeKeys.detail(code),
    queryFn: () => committeesApi.getByCode(code),
    enabled: !!code,
  });
}

/**
 * Hook to fetch committee members
 */
export function useCommitteeMembers(code: string, params: PaginationParams = {}) {
  return useQuery({
    queryKey: committeeKeys.members(code, params),
    queryFn: () => committeesApi.getMembers(code, params),
    enabled: !!code,
  });
}

/**
 * Hook to fetch committee subcommittees
 */
export function useCommitteeSubcommittees(
  code: string,
  params: PaginationParams = {}
) {
  return useQuery({
    queryKey: committeeKeys.subcommittees(code, params),
    queryFn: () => committeesApi.getSubcommittees(code, params),
    enabled: !!code,
  });
}

/**
 * Hook to fetch committees by chamber
 */
export function useCommitteesByChamber(
  chamber: CommitteeChamber,
  params: PaginationParams = {}
) {
  return useQuery({
    queryKey: committeeKeys.byChamber(chamber, params),
    queryFn: () => committeesApi.getByChamber(chamber, params),
    enabled: !!chamber,
  });
}

/**
 * Hook to search committees by name
 */
export function useCommitteeSearch(name: string, params: PaginationParams = {}) {
  return useQuery({
    queryKey: committeeKeys.search(name, params),
    queryFn: () => committeesApi.search(name, params),
    enabled: !!name && name.length >= 2,
  });
}

/**
 * Hook to fetch total committee count
 */
export function useCommitteeCount() {
  return useQuery({
    queryKey: committeeKeys.count(),
    queryFn: () => committeesApi.getCount(),
  });
}

/**
 * Hook to trigger committee sync (admin only)
 * Returns SyncJobStatus with jobId for polling via useSyncJob.
 * Query invalidation is handled by SyncButton on job completion.
 */
export function useCommitteeSync() {
  return useMutation({
    mutationFn: () => committeesApi.triggerSync(),
  });
}

/**
 * Hook to trigger membership sync (admin only)
 * Returns SyncJobStatus with jobId for polling via useSyncJob.
 * Query invalidation is handled by SyncButton on job completion.
 */
export function useMembershipSync() {
  return useMutation({
    mutationFn: (congress?: number) => committeesApi.triggerMembershipSync(congress),
  });
}
