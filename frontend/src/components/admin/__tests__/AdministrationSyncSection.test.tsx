import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AdministrationSyncSection } from '../AdministrationSyncSection';

// Mock PresidencySyncCard (already has its own tests)
vi.mock('../PresidencySyncCard', () => ({
  PresidencySyncCard: () => <div data-testid="presidency-sync-card">PresidencySyncCard</div>,
}));

// Mock EO sync hooks
const mockRefetch = vi.fn();
const mockMutateAsync = vi.fn();

vi.mock('@/hooks/usePresidencySync', () => ({
  useEOSyncStatus: vi.fn(() => ({
    data: null,
    isLoading: false,
    error: null,
    refetch: mockRefetch,
  })),
  useEOSync: vi.fn(() => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  })),
}));

const mockToast = vi.fn();
vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

import { useEOSyncStatus, useEOSync } from '@/hooks/usePresidencySync';
const mockUseEOSyncStatus = vi.mocked(useEOSyncStatus);
const mockUseEOSync = vi.mocked(useEOSync);

describe('AdministrationSyncSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseEOSyncStatus.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    } as unknown as ReturnType<typeof useEOSyncStatus>);
    mockUseEOSync.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useEOSync>);
  });

  it('renders PresidencySyncCard', () => {
    render(<AdministrationSyncSection />);
    expect(screen.getByTestId('presidency-sync-card')).toBeInTheDocument();
  });

  it('renders EO sync card with title', () => {
    render(<AdministrationSyncSection />);
    expect(screen.getByText('Executive Orders')).toBeInTheDocument();
  });

  it('renders "Never Synced" badge when no EO data', () => {
    render(<AdministrationSyncSection />);
    expect(screen.getByText('Never Synced')).toBeInTheDocument();
  });

  it('renders loading skeleton when EO status is loading', () => {
    mockUseEOSyncStatus.mockReturnValue({
      data: null,
      isLoading: true,
      error: null,
      refetch: mockRefetch,
    } as unknown as ReturnType<typeof useEOSyncStatus>);

    render(<AdministrationSyncSection />);
    // Should not show the sync button when loading
    expect(screen.queryByRole('button', { name: /sync executive orders/i })).not.toBeInTheDocument();
  });

  it('renders error state with retry button', async () => {
    mockUseEOSyncStatus.mockReturnValue({
      data: null,
      isLoading: false,
      error: new Error('Network failure'),
      refetch: mockRefetch,
    } as unknown as ReturnType<typeof useEOSyncStatus>);

    render(<AdministrationSyncSection />);
    expect(screen.getByText('Failed to load status')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(mockRefetch).toHaveBeenCalled();
  });

  it('renders EO counts when data is available', () => {
    mockUseEOSyncStatus.mockReturnValue({
      data: {
        inProgress: false,
        eoCounts: { '45': 220, '46': 162, '47': 45 },
      },
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    } as unknown as ReturnType<typeof useEOSyncStatus>);

    render(<AdministrationSyncSection />);
    expect(screen.getByText('427')).toBeInTheDocument(); // total
    expect(screen.getByText('3')).toBeInTheDocument(); // presidencies with EOs
    expect(screen.getByText('Ready')).toBeInTheDocument();
  });

  it('shows syncing badge when sync is in progress', () => {
    mockUseEOSyncStatus.mockReturnValue({
      data: { inProgress: true, eoCounts: {} },
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    } as unknown as ReturnType<typeof useEOSyncStatus>);

    render(<AdministrationSyncSection />);
    expect(screen.getByText('Syncing')).toBeInTheDocument();
  });

  it('opens confirmation dialog and triggers sync', async () => {
    mockMutateAsync.mockResolvedValue({ jobId: 'test', status: 'RUNNING' });

    render(<AdministrationSyncSection />);

    await userEvent.click(screen.getByRole('button', { name: /sync executive orders/i }));
    expect(screen.getByText(/sync executive orders\?/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /confirm sync/i }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalled();
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Executive Order Sync Started',
          variant: 'success',
        })
      );
    });
  });

  it('shows error toast on sync failure', async () => {
    mockMutateAsync.mockRejectedValue(new Error('EO sync failed'));

    render(<AdministrationSyncSection />);

    await userEvent.click(screen.getByRole('button', { name: /sync executive orders/i }));
    await userEvent.click(screen.getByRole('button', { name: /confirm sync/i }));

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Executive Order Sync Failed',
          variant: 'destructive',
        })
      );
    });
  });
});
