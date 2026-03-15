import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AdministrationDataSection } from '../AdministrationDataSection';
import type { PresidencyDTO } from '@/hooks/usePresidencySync';

// Mock hooks
vi.mock('@/hooks/usePresidencySync', () => ({
  useAllPresidencies: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
  })),
  usePresidencyAdministration: vi.fn(() => ({
    data: null,
    isLoading: false,
  })),
}));

import { useAllPresidencies, usePresidencyAdministration } from '@/hooks/usePresidencySync';
const mockUseAllPresidencies = vi.mocked(useAllPresidencies);
const mockUsePresidencyAdministration = vi.mocked(usePresidencyAdministration);

const mockPresidency: PresidencyDTO = {
  id: 'pres-47',
  number: 47,
  ordinalLabel: '47th',
  individualId: 'ind-trump',
  presidentFullName: 'Donald J. Trump',
  presidentFirstName: 'Donald',
  presidentLastName: 'Trump',
  imageUrl: null,
  birthDate: '1946-06-14',
  deathDate: null,
  birthPlace: 'Queens, New York',
  party: 'Republican',
  startDate: '2025-01-20',
  endDate: null,
  termLabel: '2025-present',
  termDays: null,
  electionYear: 2024,
  endReason: null,
  executiveOrderCount: null,
  vicePresidents: [],
  predecessorId: null,
  successorId: null,
  current: true,
  living: true,
};

const defaultHandlers = {
  onEditPresidency: vi.fn(),
  onEditIndividual: vi.fn(),
  onEditStaff: vi.fn(),
  onAddStaff: vi.fn(),
  onDeleteStaff: vi.fn(),
};

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  );
}

describe('AdministrationDataSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAllPresidencies.mockReturnValue({
      data: [mockPresidency],
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useAllPresidencies>);
    mockUsePresidencyAdministration.mockReturnValue({
      data: {
        presidencyId: 'pres-47',
        presidencyNumber: 47,
        presidencyLabel: '47th Presidency',
        vicePresidents: [
          {
            holdingId: 'vp-1',
            individualId: 'ind-vance',
            fullName: 'JD Vance',
            firstName: 'JD',
            lastName: 'Vance',
            positionTitle: 'Vice President',
            startDate: '2025-01-20',
            endDate: null,
            termLabel: '2025-present',
            imageUrl: null,
          },
        ],
        chiefsOfStaff: [],
        cabinetMembers: [],
      },
      isLoading: false,
    } as unknown as ReturnType<typeof usePresidencyAdministration>);
  });

  it('renders loading skeleton when loading', () => {
    mockUseAllPresidencies.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as unknown as ReturnType<typeof useAllPresidencies>);

    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);
    expect(document.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('renders error state on failure', () => {
    mockUseAllPresidencies.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Network error'),
    } as unknown as ReturnType<typeof useAllPresidencies>);

    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);
    expect(screen.getByText('Failed to load presidencies')).toBeInTheDocument();
  });

  it('renders presidencies table with data', () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    expect(screen.getByText('47')).toBeInTheDocument();
    expect(screen.getByText('Donald J. Trump')).toBeInTheDocument();
    expect(screen.getByText('Republican')).toBeInTheDocument();
    expect(screen.getByText('2025-present')).toBeInTheDocument();
    expect(screen.getByText('Current')).toBeInTheDocument();
  });

  it('calls onEditPresidency when edit button clicked', async () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    const editButtons = screen.getAllByTitle('Edit presidency');
    await userEvent.click(editButtons[0]);

    expect(defaultHandlers.onEditPresidency).toHaveBeenCalledWith(mockPresidency);
  });

  it('calls onEditIndividual when president name clicked', async () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    await userEvent.click(screen.getByText('Donald J. Trump'));

    expect(defaultHandlers.onEditIndividual).toHaveBeenCalledWith('ind-trump', 'Donald J. Trump');
  });

  it('shows staff table when staff button clicked', async () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    await userEvent.click(screen.getByTitle('View staff'));

    expect(screen.getByText(/Staff — 47th/)).toBeInTheDocument();
    expect(screen.getByText('JD Vance')).toBeInTheDocument();
    // "Vice President" appears as both position title and category badge
    expect(screen.getAllByText('Vice President').length).toBeGreaterThanOrEqual(1);
  });

  it('hides staff table when staff button clicked again', async () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    const staffBtn = screen.getByTitle('View staff');
    await userEvent.click(staffBtn);
    expect(screen.getByText(/Staff — 47th/)).toBeInTheDocument();

    await userEvent.click(staffBtn);
    expect(screen.queryByText(/Staff — 47th/)).not.toBeInTheDocument();
  });

  it('calls onAddStaff when Add Staff button clicked', async () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    await userEvent.click(screen.getByTitle('View staff'));
    await userEvent.click(screen.getByRole('button', { name: /add staff/i }));

    expect(defaultHandlers.onAddStaff).toHaveBeenCalledWith('pres-47');
  });

  it('calls onEditStaff when staff edit button clicked', async () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    await userEvent.click(screen.getByTitle('View staff'));
    await userEvent.click(screen.getByTitle('Edit position holding'));

    expect(defaultHandlers.onEditStaff).toHaveBeenCalledWith(
      expect.objectContaining({
        holdingId: 'vp-1',
        fullName: 'JD Vance',
        category: 'Vice President',
      })
    );
  });

  it('calls onDeleteStaff when staff delete button clicked', async () => {
    renderWithQueryClient(<AdministrationDataSection {...defaultHandlers} />);

    await userEvent.click(screen.getByTitle('View staff'));
    await userEvent.click(screen.getByTitle('Delete position holding'));

    expect(defaultHandlers.onDeleteStaff).toHaveBeenCalledWith(
      expect.objectContaining({
        holdingId: 'vp-1',
        fullName: 'JD Vance',
      })
    );
  });
});
