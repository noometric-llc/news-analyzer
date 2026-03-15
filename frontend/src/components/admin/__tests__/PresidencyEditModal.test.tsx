import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PresidencyEditModal } from '../PresidencyEditModal';
import type { PresidencyDTO } from '@/hooks/usePresidencySync';

const mockMutateAsync = vi.fn();
vi.mock('@/hooks/useAdminPresidency', () => ({
  useUpdatePresidency: vi.fn(() => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  })),
}));

const mockToast = vi.fn();
vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

const mockPresidency: PresidencyDTO = {
  id: 'pres-45',
  number: 45,
  ordinalLabel: '45th',
  individualId: 'ind-trump-45',
  presidentFullName: 'Donald J. Trump',
  presidentFirstName: 'Donald',
  presidentLastName: 'Trump',
  imageUrl: null,
  birthDate: '1946-06-14',
  deathDate: null,
  birthPlace: 'Queens, New York',
  party: 'Republican',
  startDate: '2017-01-20',
  endDate: '2021-01-20',
  termLabel: '2017-2021',
  termDays: 1461,
  electionYear: 2016,
  endReason: null,
  executiveOrderCount: null,
  vicePresidents: [],
  predecessorId: null,
  successorId: null,
  current: false,
  living: true,
};

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  );
}

describe('PresidencyEditModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders modal with presidency data', () => {
    renderWithQueryClient(
      <PresidencyEditModal presidency={mockPresidency} open={true} onOpenChange={vi.fn()} />
    );

    expect(screen.getByText(/edit presidency.*45th/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue('Republican')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2017-01-20')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2021-01-20')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2016')).toBeInTheDocument();
  });

  it('does not render when open is false', () => {
    renderWithQueryClient(
      <PresidencyEditModal presidency={mockPresidency} open={false} onOpenChange={vi.fn()} />
    );

    expect(screen.queryByText(/edit presidency/i)).not.toBeInTheDocument();
  });

  it('calls onOpenChange when cancel clicked', async () => {
    const onOpenChange = vi.fn();
    renderWithQueryClient(
      <PresidencyEditModal presidency={mockPresidency} open={true} onOpenChange={onOpenChange} />
    );

    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('calls mutateAsync with correct data on save', async () => {
    mockMutateAsync.mockResolvedValue({});

    renderWithQueryClient(
      <PresidencyEditModal presidency={mockPresidency} open={true} onOpenChange={vi.fn()} />
    );

    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({
        id: 'pres-45',
        data: expect.objectContaining({
          party: 'Republican',
          startDate: '2017-01-20',
          endDate: '2021-01-20',
          electionYear: 2016,
        }),
      });
    });
  });

  it('shows success toast on save', async () => {
    mockMutateAsync.mockResolvedValue({});

    renderWithQueryClient(
      <PresidencyEditModal presidency={mockPresidency} open={true} onOpenChange={vi.fn()} />
    );

    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Presidency Updated',
          variant: 'success',
        })
      );
    });
  });

  it('shows error toast on save failure', async () => {
    mockMutateAsync.mockRejectedValue(new Error('Update failed'));

    renderWithQueryClient(
      <PresidencyEditModal presidency={mockPresidency} open={true} onOpenChange={vi.fn()} />
    );

    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Update Failed',
          variant: 'destructive',
        })
      );
    });
  });
});
