/**
 * Government Organizations React Query Hooks
 *
 * React Query hooks for government organization queries and sync operations.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { Page } from '@/types/pagination';
import type {
  GovOrgSyncStatus,
  CsvImportResult,
  GovernmentOrganization,
  GovernmentBranch,
} from '@/types/government-org';
import type { SyncJobStatus } from '@/types/sync';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Query params for paginated list
 */
export interface GovOrgListParams {
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
  branch?: string;
  type?: string;
  active?: string;
  search?: string;
}

/**
 * Query key factory for government organizations
 */
export const govOrgKeys = {
  all: ['government-organizations'] as const,
  lists: () => [...govOrgKeys.all, 'list'] as const,
  list: (params: GovOrgListParams) => [...govOrgKeys.lists(), params] as const,
  detail: (id: number) => [...govOrgKeys.all, 'detail', id] as const,
  byBranch: (branch: GovernmentBranch) => [...govOrgKeys.lists(), 'by-branch', branch] as const,
  search: (query: string) => [...govOrgKeys.all, 'search', query] as const,
  hierarchy: (branch?: GovernmentBranch) => [...govOrgKeys.all, 'hierarchy', branch] as const,
  topLevel: () => [...govOrgKeys.all, 'top-level'] as const,
  syncStatus: () => [...govOrgKeys.all, 'sync-status'] as const,
};

// =====================================================================
// Organization Fetch Functions
// =====================================================================

/**
 * Fetch a single government organization by ID
 */
async function fetchGovernmentOrg(id: number): Promise<GovernmentOrganization> {
  const response = await fetch(`${API_BASE}/api/government-organizations/${id}`);

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Government organization not found');
    }
    throw new Error('Failed to fetch government organization');
  }

  return response.json();
}

/**
 * Fetch paginated list of government organizations
 */
async function fetchGovernmentOrgsList(params: GovOrgListParams): Promise<Page<GovernmentOrganization>> {
  const searchParams = new URLSearchParams();

  if (params.page !== undefined) searchParams.set('page', String(params.page));
  if (params.size !== undefined) searchParams.set('size', String(params.size));
  if (params.sort) searchParams.set('sort', `${params.sort},${params.direction || 'asc'}`);
  if (params.branch) searchParams.set('branch', params.branch);
  if (params.type) searchParams.set('type', params.type);
  if (params.active) searchParams.set('active', params.active);
  if (params.search) searchParams.set('q', params.search);

  const url = `${API_BASE}/api/government-organizations${searchParams.toString() ? `?${searchParams.toString()}` : ''}`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error('Failed to fetch government organizations');
  }

  return response.json();
}

/**
 * Fetch government organizations by branch
 */
async function fetchGovernmentOrgsByBranch(branch: GovernmentBranch): Promise<GovernmentOrganization[]> {
  const response = await fetch(`${API_BASE}/api/government-organizations/by-branch?branch=${branch}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch ${branch} branch organizations`);
  }
  return response.json();
}

/**
 * Search government organizations by name
 */
async function searchGovernmentOrgs(query: string): Promise<GovernmentOrganization[]> {
  const response = await fetch(`${API_BASE}/api/government-organizations/search?query=${encodeURIComponent(query)}`);
  if (!response.ok) {
    throw new Error('Failed to search government organizations');
  }
  return response.json();
}

/**
 * Fetch top-level government organizations (no parent)
 */
async function fetchTopLevelOrgs(): Promise<GovernmentOrganization[]> {
  const response = await fetch(`${API_BASE}/api/government-organizations/top-level`);
  if (!response.ok) {
    throw new Error('Failed to fetch top-level organizations');
  }
  return response.json();
}

/**
 * Organization with nested children for hierarchy view
 * Includes index signature for compatibility with HierarchyNode
 */
export type GovernmentOrgHierarchyNode = GovernmentOrganization & {
  children?: GovernmentOrgHierarchyNode[];
  [key: string]: unknown;
};

/**
 * Fetch all organizations and build hierarchy tree client-side
 */
async function fetchGovernmentOrgsHierarchy(
  branch?: GovernmentBranch
): Promise<GovernmentOrgHierarchyNode[]> {
  // Fetch all organizations (unpaginated)
  const url = branch
    ? `${API_BASE}/api/government-organizations/by-branch?branch=${branch}`
    : `${API_BASE}/api/government-organizations?size=1000`;

  const response = await fetch(url);
  if (!response.ok) {
    throw new Error('Failed to fetch organization hierarchy');
  }

  const data = await response.json();
  // Handle paginated response or array
  const orgs: GovernmentOrganization[] = Array.isArray(data) ? data : data.content;

  // Build hierarchy tree
  return buildHierarchyTree(orgs);
}

/**
 * Build a hierarchy tree from flat organization list
 */
function buildHierarchyTree(orgs: GovernmentOrganization[]): GovernmentOrgHierarchyNode[] {
  // Create a map for quick lookup
  const orgMap = new Map<string, GovernmentOrgHierarchyNode>();

  // Initialize all orgs with empty children arrays
  for (const org of orgs) {
    orgMap.set(org.id, { ...org, children: [] });
  }

  // Build the tree structure
  const roots: GovernmentOrgHierarchyNode[] = [];

  for (const org of orgs) {
    const node = orgMap.get(org.id)!;

    if (org.parentId && orgMap.has(org.parentId)) {
      // Add as child to parent
      const parent = orgMap.get(org.parentId)!;
      parent.children = parent.children || [];
      parent.children.push(node);
    } else {
      // No parent or parent not in list - this is a root
      roots.push(node);
    }
  }

  // Sort children by name at each level
  function sortChildren(nodes: GovernmentOrgHierarchyNode[]) {
    nodes.sort((a, b) => a.officialName.localeCompare(b.officialName));
    for (const node of nodes) {
      if (node.children && node.children.length > 0) {
        sortChildren(node.children);
      }
    }
  }

  sortChildren(roots);

  return roots;
}

// =====================================================================
// Organization Query Hooks
// =====================================================================

/**
 * Hook to fetch paginated list of government organizations
 */
export function useGovernmentOrgsList(params: GovOrgListParams = {}) {
  return useQuery({
    queryKey: govOrgKeys.list(params),
    queryFn: () => fetchGovernmentOrgsList(params),
  });
}

/**
 * Hook to fetch a single government organization by ID
 */
export function useGovernmentOrg(id: number | null) {
  return useQuery({
    queryKey: govOrgKeys.detail(id!),
    queryFn: () => fetchGovernmentOrg(id!),
    enabled: id !== null && id !== undefined,
  });
}

/**
 * Hook to fetch government organizations by branch
 */
export function useGovernmentOrgsByBranch(branch: GovernmentBranch) {
  return useQuery({
    queryKey: govOrgKeys.byBranch(branch),
    queryFn: () => fetchGovernmentOrgsByBranch(branch),
  });
}

/**
 * Hook to search government organizations
 */
export function useGovernmentOrgsSearch(query: string) {
  return useQuery({
    queryKey: govOrgKeys.search(query),
    queryFn: () => searchGovernmentOrgs(query),
    enabled: !!query && query.length >= 2,
  });
}

/**
 * Hook to fetch top-level government organizations
 */
export function useTopLevelGovernmentOrgs() {
  return useQuery({
    queryKey: govOrgKeys.topLevel(),
    queryFn: fetchTopLevelOrgs,
  });
}

/**
 * Hook to fetch government organization hierarchy
 * Builds a tree structure from flat organization list
 */
export function useGovernmentOrgsHierarchy(branch?: GovernmentBranch) {
  return useQuery({
    queryKey: govOrgKeys.hierarchy(branch),
    queryFn: () => fetchGovernmentOrgsHierarchy(branch),
    staleTime: 5 * 60 * 1000, // 5 minutes - hierarchy data changes infrequently
  });
}

// =====================================================================
// Sync Functions
// =====================================================================

/**
 * Fetch government organization sync status from the API
 */
async function fetchGovOrgSyncStatus(): Promise<GovOrgSyncStatus> {
  const response = await fetch(`${API_BASE}/api/government-organizations/sync/status`);
  if (!response.ok) {
    throw new Error('Failed to fetch government organization sync status');
  }
  return response.json();
}

/**
 * Trigger government organization sync from Federal Register API (async — returns job status)
 */
async function triggerGovOrgSync(): Promise<SyncJobStatus> {
  const response = await fetch(`${API_BASE}/api/government-organizations/sync/federal-register`, {
    method: 'POST',
  });
  if (!response.ok) {
    if (response.status === 409) {
      throw new Error('Government organization sync already in progress');
    }
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.errorMessages?.[0] || 'Government organization sync failed');
  }
  return response.json();
}

/**
 * Hook to fetch government organization sync status
 */
export function useGovernmentOrgSyncStatus() {
  return useQuery({
    queryKey: govOrgKeys.syncStatus(),
    queryFn: fetchGovOrgSyncStatus,
    staleTime: 60 * 1000, // 60 seconds
    refetchOnWindowFocus: true,
  });
}

/**
 * Hook to trigger government organization sync (admin only)
 * Returns SyncJobStatus with jobId for polling via useSyncJob.
 * Query invalidation is handled by SyncButton on job completion.
 */
export function useGovernmentOrgSync() {
  return useMutation({
    mutationFn: triggerGovOrgSync,
  });
}

/**
 * Import government organizations from CSV file
 */
async function importGovOrgsFromCsv(file: File): Promise<CsvImportResult> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_BASE}/api/government-organizations/import/csv`, {
    method: 'POST',
    body: formData,
  });

  const data = await response.json();

  if (!response.ok) {
    // Return the result which contains validation errors
    return data as CsvImportResult;
  }

  return data as CsvImportResult;
}

/**
 * Hook to import government organizations from CSV (admin only)
 */
export function useGovOrgCsvImport() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: importGovOrgsFromCsv,
    onSuccess: (data) => {
      if (data.success) {
        // Invalidate sync status to refresh counts
        queryClient.invalidateQueries({ queryKey: govOrgKeys.syncStatus() });
        // Also invalidate any government organization list queries
        queryClient.invalidateQueries({ queryKey: govOrgKeys.all });
      }
    },
  });
}
