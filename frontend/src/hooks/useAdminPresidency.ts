/**
 * Admin Presidency Mutation Hooks (KB-2.4)
 *
 * CRUD mutation hooks for the KB-2.5 admin endpoints.
 * These endpoints don't exist yet — hooks are built ahead of the backend story.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { presidencyKeys } from './usePresidencySync';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || '';

// ============================================================================
// Types
// ============================================================================

export interface UpdatePresidencyPayload {
  party?: string;
  startDate?: string;
  endDate?: string | null;
  electionYear?: number | null;
  endReason?: string | null;
}

export interface UpdateIndividualPayload {
  firstName?: string;
  lastName?: string;
  birthDate?: string | null;
  deathDate?: string | null;
  birthPlace?: string | null;
  imageUrl?: string | null;
}

export interface CreatePositionHoldingPayload {
  individualId: string;
  positionId: string;
  presidencyId: string;
  startDate: string;
  endDate?: string | null;
}

export interface UpdatePositionHoldingPayload {
  startDate?: string;
  endDate?: string | null;
}

// ============================================================================
// API Functions
// ============================================================================

async function updatePresidency(id: string, data: UpdatePresidencyPayload) {
  const response = await fetch(`${API_BASE}/api/admin/presidencies/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to update presidency');
  }
  return response.json();
}

async function updateIndividual(id: string, data: UpdateIndividualPayload) {
  const response = await fetch(`${API_BASE}/api/admin/individuals/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to update individual');
  }
  return response.json();
}

async function createPositionHolding(data: CreatePositionHoldingPayload) {
  const response = await fetch(`${API_BASE}/api/admin/position-holdings`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to create position holding');
  }
  return response.json();
}

async function updatePositionHolding(id: string, data: UpdatePositionHoldingPayload) {
  const response = await fetch(`${API_BASE}/api/admin/position-holdings/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to update position holding');
  }
  return response.json();
}

async function deletePositionHolding(id: string) {
  const response = await fetch(`${API_BASE}/api/admin/position-holdings/${id}`, {
    method: 'DELETE',
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to delete position holding');
  }
  // 204 No Content is typical for DELETE
  if (response.status === 204) return;
  return response.json();
}

// ============================================================================
// Hooks
// ============================================================================

export function useUpdatePresidency() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdatePresidencyPayload }) =>
      updatePresidency(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: presidencyKeys.all });
    },
  });
}

export function useUpdateIndividual() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateIndividualPayload }) =>
      updateIndividual(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: presidencyKeys.all });
    },
  });
}

export function useCreatePositionHolding() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreatePositionHoldingPayload) =>
      createPositionHolding(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: presidencyKeys.all });
    },
  });
}

export function useUpdatePositionHolding() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdatePositionHoldingPayload }) =>
      updatePositionHolding(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: presidencyKeys.all });
    },
  });
}

export function useDeletePositionHolding() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => deletePositionHolding(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: presidencyKeys.all });
    },
  });
}
