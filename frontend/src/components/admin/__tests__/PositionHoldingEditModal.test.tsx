import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PositionHoldingEditModal, DeleteStaffDialog } from '../PositionHoldingEditModal';
import type { StaffRow } from '../AdministrationDataSection';

const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();

vi.mock('@/hooks/useAdminPresidency', () => ({
  useCreatePositionHolding: vi.fn(() => ({
    mutateAsync: mockCreateMutateAsync,
    isPending: false,
  })),
  useUpdatePositionHolding: vi.fn(() => ({
    mutateAsync: mockUpdateMutateAsync,
    isPending: false,
  })),
  useDeletePositionHolding: vi.fn(() => ({
    mutateAsync: mockDeleteMutateAsync,
    isPending: false,
  })),
}));

const mockToast = vi.fn();
vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

const mockStaffRow: StaffRow = {
  holdingId: 'hold-1',
  individualId: 'ind-vance',
  fullName: 'JD Vance',
  positionTitle: 'Vice President',
  startDate: '2025-01-20',
  endDate: null,
  category: 'Vice President',
};

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  );
}

describe('PositionHoldingEditModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Edit mode', () => {
    it('renders modal with staff row data', () => {
      renderWithQueryClient(
        <PositionHoldingEditModal
          staffRow={mockStaffRow}
          presidencyId={null}
          open={true}
          onOpenChange={vi.fn()}
        />
      );

      expect(screen.getByText(/edit position.*jd vance/i)).toBeInTheDocument();
      expect(screen.getByDisplayValue('2025-01-20')).toBeInTheDocument();
    });

    it('calls update mutation on save', async () => {
      mockUpdateMutateAsync.mockResolvedValue({});

      renderWithQueryClient(
        <PositionHoldingEditModal
          staffRow={mockStaffRow}
          presidencyId={null}
          open={true}
          onOpenChange={vi.fn()}
        />
      );

      await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith({
          id: 'hold-1',
          data: expect.objectContaining({
            startDate: '2025-01-20',
          }),
        });
      });
    });

    it('shows success toast on update', async () => {
      mockUpdateMutateAsync.mockResolvedValue({});

      renderWithQueryClient(
        <PositionHoldingEditModal
          staffRow={mockStaffRow}
          presidencyId={null}
          open={true}
          onOpenChange={vi.fn()}
        />
      );

      await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

      await waitFor(() => {
        expect(mockToast).toHaveBeenCalledWith(
          expect.objectContaining({
            title: 'Position Holding Updated',
            variant: 'success',
          })
        );
      });
    });
  });

  describe('Create mode', () => {
    it('renders create form when staffRow is null', () => {
      renderWithQueryClient(
        <PositionHoldingEditModal
          staffRow={null}
          presidencyId="pres-47"
          open={true}
          onOpenChange={vi.fn()}
        />
      );

      expect(screen.getByText('Add Staff Position')).toBeInTheDocument();
      expect(screen.getByLabelText(/individual id/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/position id/i)).toBeInTheDocument();
    });

    it('shows validation error when required fields missing', async () => {
      renderWithQueryClient(
        <PositionHoldingEditModal
          staffRow={null}
          presidencyId="pres-47"
          open={true}
          onOpenChange={vi.fn()}
        />
      );

      await userEvent.click(screen.getByRole('button', { name: /create/i }));

      await waitFor(() => {
        expect(mockToast).toHaveBeenCalledWith(
          expect.objectContaining({
            title: 'Validation Error',
            variant: 'destructive',
          })
        );
      });
    });

    it('calls create mutation with correct data', async () => {
      mockCreateMutateAsync.mockResolvedValue({});

      renderWithQueryClient(
        <PositionHoldingEditModal
          staffRow={null}
          presidencyId="pres-47"
          open={true}
          onOpenChange={vi.fn()}
        />
      );

      await userEvent.type(screen.getByLabelText(/individual id/i), 'ind-new');
      await userEvent.type(screen.getByLabelText(/position id/i), 'pos-new');
      await userEvent.type(screen.getByLabelText(/start date/i), '2025-01-20');

      await userEvent.click(screen.getByRole('button', { name: /create/i }));

      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith({
          individualId: 'ind-new',
          positionId: 'pos-new',
          presidencyId: 'pres-47',
          startDate: '2025-01-20',
          endDate: null,
        });
      });
    });
  });

  it('does not render when open is false', () => {
    renderWithQueryClient(
      <PositionHoldingEditModal
        staffRow={mockStaffRow}
        presidencyId={null}
        open={false}
        onOpenChange={vi.fn()}
      />
    );

    expect(screen.queryByText(/edit position/i)).not.toBeInTheDocument();
  });

  it('shows error toast on failure', async () => {
    mockUpdateMutateAsync.mockRejectedValue(new Error('Network error'));

    renderWithQueryClient(
      <PositionHoldingEditModal
        staffRow={mockStaffRow}
        presidencyId={null}
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

describe('DeleteStaffDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders delete confirmation with staff details', () => {
    renderWithQueryClient(
      <DeleteStaffDialog
        staffRow={mockStaffRow}
        open={true}
        onOpenChange={vi.fn()}
      />
    );

    expect(screen.getByText(/delete position holding/i)).toBeInTheDocument();
    expect(screen.getByText(/jd vance/i)).toBeInTheDocument();
  });

  it('does not render when open is false', () => {
    renderWithQueryClient(
      <DeleteStaffDialog
        staffRow={mockStaffRow}
        open={false}
        onOpenChange={vi.fn()}
      />
    );

    expect(screen.queryByText(/delete position holding/i)).not.toBeInTheDocument();
  });

  it('calls delete mutation when confirmed', async () => {
    mockDeleteMutateAsync.mockResolvedValue(undefined);

    renderWithQueryClient(
      <DeleteStaffDialog
        staffRow={mockStaffRow}
        open={true}
        onOpenChange={vi.fn()}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));

    await waitFor(() => {
      expect(mockDeleteMutateAsync).toHaveBeenCalledWith('hold-1');
    });
  });

  it('shows success toast on delete', async () => {
    mockDeleteMutateAsync.mockResolvedValue(undefined);

    renderWithQueryClient(
      <DeleteStaffDialog
        staffRow={mockStaffRow}
        open={true}
        onOpenChange={vi.fn()}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Position Holding Deleted',
          variant: 'success',
        })
      );
    });
  });

  it('shows error toast on delete failure', async () => {
    mockDeleteMutateAsync.mockRejectedValue(new Error('Delete failed'));

    renderWithQueryClient(
      <DeleteStaffDialog
        staffRow={mockStaffRow}
        open={true}
        onOpenChange={vi.fn()}
      />
    );

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Delete Failed',
          variant: 'destructive',
        })
      );
    });
  });
});
