import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { IndividualEditModal } from '../IndividualEditModal';

const mockMutateAsync = vi.fn();
vi.mock('@/hooks/useAdminPresidency', () => ({
  useUpdateIndividual: vi.fn(() => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  })),
}));

const mockToast = vi.fn();
vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  );
}

describe('IndividualEditModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders modal with individual name', () => {
    renderWithQueryClient(
      <IndividualEditModal
        individualId="ind-1"
        fullName="John Adams"
        open={true}
        onOpenChange={vi.fn()}
      />
    );

    expect(screen.getByText(/edit individual.*john adams/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue('John')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Adams')).toBeInTheDocument();
  });

  it('does not render when open is false', () => {
    renderWithQueryClient(
      <IndividualEditModal
        individualId="ind-1"
        fullName="John Adams"
        open={false}
        onOpenChange={vi.fn()}
      />
    );

    expect(screen.queryByText(/edit individual/i)).not.toBeInTheDocument();
  });

  it('calls onOpenChange when cancel clicked', async () => {
    const onOpenChange = vi.fn();
    renderWithQueryClient(
      <IndividualEditModal
        individualId="ind-1"
        fullName="John Adams"
        open={true}
        onOpenChange={onOpenChange}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('calls mutateAsync on save', async () => {
    mockMutateAsync.mockResolvedValue({});

    renderWithQueryClient(
      <IndividualEditModal
        individualId="ind-1"
        fullName="John Adams"
        open={true}
        onOpenChange={vi.fn()}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({
        id: 'ind-1',
        data: expect.objectContaining({
          firstName: 'John',
          lastName: 'Adams',
        }),
      });
    });
  });

  it('shows success toast on save', async () => {
    mockMutateAsync.mockResolvedValue({});

    renderWithQueryClient(
      <IndividualEditModal
        individualId="ind-1"
        fullName="John Adams"
        open={true}
        onOpenChange={vi.fn()}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Individual Updated',
          variant: 'success',
        })
      );
    });
  });

  it('shows error toast on save failure', async () => {
    mockMutateAsync.mockRejectedValue(new Error('Update failed'));

    renderWithQueryClient(
      <IndividualEditModal
        individualId="ind-1"
        fullName="John Adams"
        open={true}
        onOpenChange={vi.fn()}
      />
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
