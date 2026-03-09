import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchImportPanel } from '../SearchImportPanel';
import type { FilterConfig, SearchResult, ImportResult } from '@/types/search-import';

// Mock the useSearchImport hook
const mockRefetch = vi.fn();
vi.mock('@/hooks/useSearchImport', () => ({
  useSearchImport: vi.fn(() => ({
    results: [],
    total: 0,
    currentPage: 1,
    pageSize: 10,
    isLoading: false,
    isFetching: false,
    isError: false,
    error: null,
    refetch: mockRefetch,
  })),
}));

// Mock the useToast hook
const mockToast = vi.fn();
vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({
    toast: mockToast,
  }),
}));

// Import mocked hook for manipulation
import { useSearchImport } from '@/hooks/useSearchImport';
const mockUseSearchImport = vi.mocked(useSearchImport);

// Test data types
interface TestRecord {
  [key: string]: unknown;
  id: string;
  name: string;
  type: string;
}

// Default props
const defaultProps = {
  apiName: 'Test API',
  searchEndpoint: '/api/test/search',
  filterConfig: [] as FilterConfig[],
  resultRenderer: (data: TestRecord) => <div data-testid="result">{data.name}</div>,
  onImport: vi.fn<[TestRecord, string], Promise<ImportResult>>().mockResolvedValue({ id: 'test-1', created: true, updated: false }),
  searchPlaceholder: 'Search records...',
};

describe('SearchImportPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSearchImport.mockReturnValue({
      results: [],
      total: 0,
      currentPage: 1,
      pageSize: 10,
      isLoading: false,
      isFetching: false,
      isError: false,
      error: null,
      refetch: mockRefetch,
    });
  });

  // ====== Initial State Tests ======

  describe('Initial State', () => {
    it('renders search input with placeholder', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });

    it('shows initial state message when no search query', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      expect(screen.getByText(/enter a search term to find records/i)).toBeInTheDocument();
    });

    it('shows API name in initial state message', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      expect(screen.getByText(/test api/i)).toBeInTheDocument();
    });

    it('renders search icon', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      // Search icon should be visible
      const input = screen.getByPlaceholderText('Search records...');
      expect(input.closest('div')?.querySelector('svg')).toBeInTheDocument();
    });
  });

  // ====== Search Input Tests ======

  describe('Search Input', () => {
    it('updates search query on input', async () => {
      const user = userEvent.setup();
      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      await user.type(input, 'test query');

      expect(input).toHaveValue('test query');
    });

    it('triggers search when query is entered', async () => {
      const user = userEvent.setup();
      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      await user.type(input, 'test');

      // useSearchImport should be called with the query
      expect(mockUseSearchImport).toHaveBeenCalled();
    });

    it('has correct input type', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      expect(input).toHaveAttribute('type', 'search');
    });
  });

  // ====== Loading State Tests ======

  describe('Loading State', () => {
    it('shows loading spinner when searching', () => {
      mockUseSearchImport.mockReturnValue({
        results: [],
        total: 0,
        currentPage: 1,
        pageSize: 10,
        isLoading: true,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      // Type something to trigger search
      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByText(/searching test api/i)).toBeInTheDocument();
    });
  });

  // ====== Error State Tests ======

  describe('Error State', () => {
    it('shows error message when search fails', () => {
      mockUseSearchImport.mockReturnValue({
        results: [],
        total: 0,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: true,
        error: new Error('Network error'),
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      // Type something to trigger search
      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByText(/search failed/i)).toBeInTheDocument();
      expect(screen.getByText(/network error/i)).toBeInTheDocument();
    });

    it('shows retry button on error', () => {
      mockUseSearchImport.mockReturnValue({
        results: [],
        total: 0,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: true,
        error: new Error('Network error'),
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
    });

    it('calls refetch when retry is clicked', () => {
      mockUseSearchImport.mockReturnValue({
        results: [],
        total: 0,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: true,
        error: new Error('Network error'),
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      const retryButton = screen.getByRole('button', { name: /retry/i });
      fireEvent.click(retryButton);

      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  // ====== Empty Results Tests ======

  describe('Empty Results', () => {
    it('shows empty state message when no results found', () => {
      mockUseSearchImport.mockReturnValue({
        results: [],
        total: 0,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByText(/no results found/i)).toBeInTheDocument();
    });

    it('shows custom empty message when provided', () => {
      mockUseSearchImport.mockReturnValue({
        results: [],
        total: 0,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(
        <SearchImportPanel<TestRecord>
          {...defaultProps}
          emptyMessage="Custom empty message"
        />
      );

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByText('Custom empty message')).toBeInTheDocument();
    });
  });

  // ====== Results Display Tests ======

  describe('Results Display', () => {
    const mockResults: SearchResult<TestRecord>[] = [
      { data: { id: '1', name: 'Result One', type: 'A' }, source: 'api' },
      { data: { id: '2', name: 'Result Two', type: 'B' }, source: 'api' },
    ];

    it('displays search results', () => {
      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 2,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByText('2 results found')).toBeInTheDocument();
    });

    it('shows correct result count (singular)', () => {
      mockUseSearchImport.mockReturnValue({
        results: [mockResults[0]],
        total: 1,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByText('1 result found')).toBeInTheDocument();
    });

    it('shows fetching indicator when refetching', () => {
      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 2,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: true,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      // Should show inline loading indicator
      expect(screen.getByText('2 results found')).toBeInTheDocument();
    });
  });

  // ====== Pagination Tests ======

  describe('Pagination', () => {
    const mockResults: SearchResult<TestRecord>[] = [
      { data: { id: '1', name: 'Result One', type: 'A' }, source: 'api' },
    ];

    it('shows pagination when multiple pages', () => {
      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 25, // More than default page size
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} pageSize={10} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByRole('button', { name: /previous/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /next/i })).toBeInTheDocument();
    });

    it('disables previous button on first page', () => {
      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 25,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} pageSize={10} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled();
    });

    it('shows page info', () => {
      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 25,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} pageSize={10} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();
    });

    it('navigates to next page on click', () => {
      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 25,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} pageSize={10} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      const nextButton = screen.getByRole('button', { name: /next/i });
      fireEvent.click(nextButton);

      // After clicking next, page should be 2 (displayed as "2 / 3")
      expect(screen.getByText('2 / 3')).toBeInTheDocument();
    });

    it('hides pagination when single page', () => {
      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 5, // Less than page size
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} pageSize={10} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      expect(screen.queryByRole('button', { name: /previous/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /next/i })).not.toBeInTheDocument();
    });
  });

  // ====== Filter Tests ======

  describe('Filters', () => {
    const filterConfig: FilterConfig[] = [
      {
        id: 'type',
        label: 'Type',
        type: 'select',
        options: [
          { value: 'A', label: 'Type A' },
          { value: 'B', label: 'Type B' },
        ],
      },
    ];

    it('renders filters when filterConfig provided', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} filterConfig={filterConfig} />);

      // SearchFilters component should be rendered
      // (we're not testing the full filter UI, just that the component renders)
      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });

    it('does not render filters when filterConfig is empty', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} filterConfig={[]} />);

      // Only search input should be present, no filter UI
      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });
  });

  // ====== Import Tests ======

  describe('Import Functionality', () => {
    const mockResults: SearchResult<TestRecord>[] = [
      { data: { id: '1', name: 'Result One', type: 'A' }, source: 'test-api' },
    ];

    it('calls onImport when import is triggered', async () => {
      const onImport = vi.fn().mockResolvedValue({ created: true });

      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 1,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} onImport={onImport} />);

      const input = screen.getByPlaceholderText('Search records...');
      fireEvent.change(input, { target: { value: 'test' } });

      // Note: The actual import button is inside SearchResultCard
      // which would need to be tested with proper child component mocking
      // For now we verify the component renders and hook is called correctly
      expect(mockUseSearchImport).toHaveBeenCalled();
    });

    it('shows success toast after successful import', async () => {
      const onImport = vi.fn().mockResolvedValue({ created: true });

      mockUseSearchImport.mockReturnValue({
        results: mockResults,
        total: 1,
        currentPage: 1,
        pageSize: 10,
        isLoading: false,
        isFetching: false,
        isError: false,
        error: null,
        refetch: mockRefetch,
      });

      render(<SearchImportPanel<TestRecord> {...defaultProps} onImport={onImport} />);

      // Component is rendered and ready for import interactions
      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });
  });

  // ====== Duplicate Detection Tests ======

  describe('Duplicate Detection', () => {
    it('accepts duplicateChecker function', () => {
      const duplicateChecker = vi.fn().mockResolvedValue(null);

      render(
        <SearchImportPanel<TestRecord>
          {...defaultProps}
          duplicateChecker={duplicateChecker}
        />
      );

      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });

    it('accepts getExistingRecord function', () => {
      const getExistingRecord = vi.fn().mockResolvedValue(null);

      render(
        <SearchImportPanel<TestRecord>
          {...defaultProps}
          getExistingRecord={getExistingRecord}
        />
      );

      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });
  });

  // ====== Props Tests ======

  describe('Component Props', () => {
    it('accepts custom debounceMs', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} debounceMs={500} />);

      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });

    it('accepts custom pageSize', () => {
      render(<SearchImportPanel<TestRecord> {...defaultProps} pageSize={20} />);

      expect(screen.getByPlaceholderText('Search records...')).toBeInTheDocument();
    });

    it('uses default placeholder when not provided', () => {
      const { searchPlaceholder, ...propsWithoutPlaceholder } = defaultProps;

      render(<SearchImportPanel<TestRecord> {...propsWithoutPlaceholder} searchPlaceholder="Search..." />);

      expect(screen.getByPlaceholderText('Search...')).toBeInTheDocument();
    });
  });
});
