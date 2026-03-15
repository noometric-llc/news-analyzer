/**
 * Presidential Sync React Query Hooks
 *
 * React Query hooks for presidential data sync and display operations.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { SyncJobStatus } from '@/types/sync';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || '';

// ============================================================================
// Types
// ============================================================================

export interface PresidencySyncResult {
  presidenciesAdded: number;
  presidenciesUpdated: number;
  individualsAdded: number;
  individualsUpdated: number;
  vpHoldingsAdded: number;
  errors: number;
  errorMessages: string[];
}

export interface PresidencySyncStatus {
  inProgress: boolean;
  totalPresidencies: number;
  lastSync?: {
    presidenciesAdded: number;
    presidenciesUpdated: number;
    totalPresidencies: number;
    individualsAdded: number;
    individualsUpdated: number;
    vpHoldingsAdded: number;
    errors: number;
    errorMessages?: string[];
  };
}

export interface VicePresidentDTO {
  individualId: string;
  fullName: string;
  firstName: string;
  lastName: string;
  startDate: string;
  endDate: string | null;
  termLabel: string;
}

export interface PresidencyDTO {
  id: string;
  number: number;
  ordinalLabel: string;
  individualId: string;
  presidentFullName: string;
  presidentFirstName: string;
  presidentLastName: string;
  imageUrl: string | null;
  birthDate: string | null;
  deathDate: string | null;
  birthPlace: string | null;
  party: string;
  startDate: string;
  endDate: string | null;
  termLabel: string;
  termDays: number | null;
  electionYear: number | null;
  endReason: string | null;
  executiveOrderCount: number | null;
  vicePresidents: VicePresidentDTO[] | null;
  predecessorId: string | null;
  successorId: string | null;
  current: boolean;
  living: boolean;
}

export interface PresidencyPage {
  content: PresidencyDTO[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface OfficeholderDTO {
  holdingId: string;
  individualId: string;
  fullName: string;
  firstName: string;
  lastName: string;
  positionTitle: string;
  startDate: string;
  endDate: string | null;
  termLabel: string;
  imageUrl: string | null;
}

export interface CabinetMemberDTO {
  holdingId: string;
  individualId: string;
  fullName: string;
  positionTitle: string;
  departmentName: string;
  departmentId: string;
  startDate: string;
  endDate: string | null;
}

export interface ExecutiveOrderDTO {
  id: string;
  presidencyId: string;
  eoNumber: number;
  title: string;
  signingDate: string;        // "yyyy-MM-dd" format
  summary: string | null;
  federalRegisterCitation: string | null;
  federalRegisterUrl: string | null;
  status: string | null;       // e.g. "ACTIVE", "REVOKED"
  revokedByEo: number | null;
}

export interface ExecutiveOrderPage {
  content: ExecutiveOrderDTO[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;  // current page (0-based)
}

export interface PresidencyAdministrationDTO {
  presidencyId: string;
  presidencyNumber: number;
  presidencyLabel: string;
  vicePresidents: OfficeholderDTO[];
  chiefsOfStaff: OfficeholderDTO[];
  cabinetMembers: CabinetMemberDTO[];
}

// ============================================================================
// Query Keys
// ============================================================================

export const presidencyKeys = {
  all: ['presidency'] as const,
  syncStatus: () => [...presidencyKeys.all, 'sync-status'] as const,
  lastResult: () => [...presidencyKeys.all, 'last-result'] as const,
  list: (page: number, size: number) => [...presidencyKeys.all, 'list', page, size] as const,
  current: () => [...presidencyKeys.all, 'current'] as const,
  detail: (id: string) => [...presidencyKeys.all, 'detail', id] as const,
  byNumber: (number: number) => [...presidencyKeys.all, 'number', number] as const,
  administration: (id: string) => [...presidencyKeys.all, 'administration', id] as const,
};

// ============================================================================
// API Functions
// ============================================================================

async function fetchPresidencySyncStatus(): Promise<PresidencySyncStatus> {
  const response = await fetch(`${API_BASE}/api/admin/sync/presidencies/status`);
  if (!response.ok) {
    throw new Error('Failed to fetch presidency sync status');
  }
  return response.json();
}

async function fetchPresidencyLastResult(): Promise<PresidencySyncResult | null> {
  const response = await fetch(`${API_BASE}/api/admin/sync/presidencies/last-result`);
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error('Failed to fetch presidency last result');
  }
  return response.json();
}

async function triggerPresidencySync(): Promise<SyncJobStatus> {
  const response = await fetch(`${API_BASE}/api/admin/sync/presidencies`, {
    method: 'POST',
  });
  if (response.status === 409) {
    throw new Error('Presidential sync already in progress');
  }
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || 'Presidential sync failed');
  }
  return response.json();
}

async function fetchPresidencies(page: number, size: number): Promise<PresidencyPage> {
  const response = await fetch(`${API_BASE}/api/presidencies?page=${page}&size=${size}`);
  if (!response.ok) {
    throw new Error('Failed to fetch presidencies');
  }
  return response.json();
}

async function fetchCurrentPresidency(): Promise<PresidencyDTO | null> {
  const response = await fetch(`${API_BASE}/api/presidencies/current`);
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error('Failed to fetch current presidency');
  }
  return response.json();
}

async function fetchPresidencyAdministration(id: string): Promise<PresidencyAdministrationDTO> {
  const response = await fetch(`${API_BASE}/api/presidencies/${id}/administration`);
  if (!response.ok) {
    throw new Error('Failed to fetch presidency administration');
  }
  return response.json();
}

async function fetchAllPresidencies(): Promise<PresidencyDTO[]> {
  // Fetch all presidencies (use large page size)
  const response = await fetch(`${API_BASE}/api/presidencies?page=0&size=100`);
  if (!response.ok) {
    throw new Error('Failed to fetch presidencies');
  }
  const page: PresidencyPage = await response.json();
  return page.content;
}

async function fetchPresidencyExecutiveOrders(
  presidencyId: string,
  page: number,
  size: number
): Promise<ExecutiveOrderPage> {
  const response = await fetch(
    `${API_BASE}/api/presidencies/${presidencyId}/executive-orders?page=${page}&size=${size}`
  );
  if (!response.ok) {
    throw new Error('Failed to fetch executive orders');
  }
  return response.json();
}

// ============================================================================
// Hooks
// ============================================================================

/**
 * Hook to fetch presidency sync status
 */
export function usePresidencySyncStatus() {
  return useQuery({
    queryKey: presidencyKeys.syncStatus(),
    queryFn: fetchPresidencySyncStatus,
    staleTime: 30 * 1000, // 30 seconds
    refetchOnWindowFocus: true,
    refetchInterval: (query) => {
      // Poll more frequently if sync is in progress
      const data = query.state.data as PresidencySyncStatus | undefined;
      return data?.inProgress ? 5000 : false;
    },
  });
}

/**
 * Hook to fetch last presidency sync result
 */
export function usePresidencyLastResult() {
  return useQuery({
    queryKey: presidencyKeys.lastResult(),
    queryFn: fetchPresidencyLastResult,
    staleTime: 60 * 1000, // 60 seconds
  });
}

/**
 * Hook to trigger presidency sync (admin only)
 * Returns SyncJobStatus with jobId. The existing status polling
 * (usePresidencySyncStatus with refetchInterval) handles progress tracking.
 */
export function usePresidencySync() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: triggerPresidencySync,
    onSuccess: () => {
      // Immediately refetch sync status so polling picks up the running state
      queryClient.invalidateQueries({ queryKey: presidencyKeys.syncStatus() });
    },
  });
}

/**
 * Hook to fetch paginated list of presidencies
 */
export function usePresidencies(page: number = 0, size: number = 20) {
  return useQuery({
    queryKey: presidencyKeys.list(page, size),
    queryFn: () => fetchPresidencies(page, size),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch current presidency
 */
export function useCurrentPresidency() {
  return useQuery({
    queryKey: presidencyKeys.current(),
    queryFn: fetchCurrentPresidency,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch all presidencies (non-paginated)
 */
export function useAllPresidencies() {
  return useQuery({
    queryKey: [...presidencyKeys.all, 'all-list'] as const,
    queryFn: fetchAllPresidencies,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch presidency administration (VP, CoS, Cabinet)
 */
export function usePresidencyAdministration(presidencyId: string | null) {
  return useQuery({
    queryKey: presidencyKeys.administration(presidencyId || ''),
    queryFn: () => fetchPresidencyAdministration(presidencyId!),
    enabled: !!presidencyId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch paginated executive orders for a presidency
 */
export function usePresidencyExecutiveOrders(
  presidencyId: string | null,
  page: number = 0,
  size: number = 10
) {
  return useQuery({
    queryKey: [...presidencyKeys.all, 'executive-orders', presidencyId, page, size] as const,
    queryFn: () => fetchPresidencyExecutiveOrders(presidencyId!, page, size),
    enabled: !!presidencyId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

// ============================================================================
// EO Sync Types, API Functions & Hooks
// ============================================================================

export interface EOSyncStatus {
  inProgress: boolean;
  eoCounts: Record<string, number>; // presidencyNumber → count
  lastSync?: Record<string, unknown>;
}

const eoSyncKeys = {
  all: ['eo-sync'] as const,
  status: () => [...eoSyncKeys.all, 'status'] as const,
  lastResult: () => [...eoSyncKeys.all, 'last-result'] as const,
};

async function fetchEOSyncStatus(): Promise<EOSyncStatus> {
  const response = await fetch(`${API_BASE}/api/admin/sync/executive-orders/status`);
  if (!response.ok) {
    throw new Error('Failed to fetch EO sync status');
  }
  return response.json();
}

async function triggerEOSync(): Promise<SyncJobStatus> {
  const response = await fetch(`${API_BASE}/api/admin/sync/executive-orders`, {
    method: 'POST',
  });
  if (response.status === 409) {
    throw new Error('Executive Order sync already in progress');
  }
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || 'Executive Order sync failed');
  }
  return response.json();
}

/**
 * Hook to fetch EO sync status (polling when in progress)
 */
export function useEOSyncStatus() {
  return useQuery({
    queryKey: eoSyncKeys.status(),
    queryFn: fetchEOSyncStatus,
    staleTime: 30 * 1000,
    refetchOnWindowFocus: true,
    refetchInterval: (query) => {
      const data = query.state.data as EOSyncStatus | undefined;
      return data?.inProgress ? 5000 : false;
    },
  });
}

/**
 * Hook to trigger EO sync (admin only)
 */
export function useEOSync() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: triggerEOSync,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: eoSyncKeys.status() });
    },
  });
}
