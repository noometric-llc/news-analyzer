import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock next/navigation
vi.mock('next/navigation', () => ({
  usePathname: () => '/knowledge-base/government/executive/president',
}));

// Mock next/link
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Mock the government orgs hook
vi.mock('@/hooks/useGovernmentOrgs', () => ({
  useGovernmentOrgsHierarchy: () => ({
    data: [],
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
}));

// Mock the presidency sync hooks used by PresidentPage
vi.mock('@/hooks/usePresidencySync', () => ({
  useCurrentPresidency: () => ({
    data: null,
    isLoading: false,
    error: null,
  }),
  useAllPresidencies: () => ({
    data: [],
    isLoading: false,
    error: null,
  }),
}));

// Import pages (President and Vice President are now redirects — tested in redirects.test.ts)
import EOPPage from '../eop/page';
import CabinetPage from '../cabinet/page';
import IndependentAgenciesPage from '../independent-agencies/page';
import CorporationsPage from '../corporations/page';

// QueryClient wrapper for components that use React Query
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
);

describe('Executive Branch Sub-Section Pages (UI-6.3)', () => {
  // NOTE: PresidentPage and VicePresidentPage are now redirects (KB-2.6).
  // Their redirect behavior is tested in redirects.test.ts.

  // ========== EOP Page ==========
  describe('EOPPage', () => {
    it('renders the page title', () => {
      render(<EOPPage />, { wrapper });
      expect(
        screen.getByRole('heading', { level: 1, name: /Executive Office of the President/i })
      ).toBeInTheDocument();
    });

    it('renders component agencies section', () => {
      render(<EOPPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /EOP Component Agencies/i })).toBeInTheDocument();
    });

    it('has official resources section', () => {
      render(<EOPPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Official Resources/i })).toBeInTheDocument();
    });
  });

  // ========== Cabinet Page ==========
  describe('CabinetPage', () => {
    it('renders the page title', () => {
      render(<CabinetPage />, { wrapper });
      expect(
        screen.getByRole('heading', { level: 1, name: /Cabinet Departments/i })
      ).toBeInTheDocument();
    });

    it('renders the 15 departments section', () => {
      render(<CabinetPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /The 15 Executive Departments/i })).toBeInTheDocument();
    });

    it('shows fallback department list when no data', () => {
      render(<CabinetPage />, { wrapper });
      // Check that the fallback grid is rendered (15 departments)
      const cards = screen.getAllByText(/^Department of/i);
      expect(cards.length).toBeGreaterThan(0);
    });

    it('has official resources section', () => {
      render(<CabinetPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Official Resources/i })).toBeInTheDocument();
    });
  });

  // ========== Independent Agencies Page ==========
  describe('IndependentAgenciesPage', () => {
    it('renders the page title', () => {
      render(<IndependentAgenciesPage />, { wrapper });
      expect(
        screen.getByRole('heading', { level: 1, name: /Independent Agencies/i })
      ).toBeInTheDocument();
    });

    it('renders types of agencies section', () => {
      render(<IndependentAgenciesPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Types of Independent Agencies/i })).toBeInTheDocument();
    });

    it('renders federal independent agencies section', () => {
      render(<IndependentAgenciesPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Federal Independent Agencies/i })).toBeInTheDocument();
    });

    it('has official resources section', () => {
      render(<IndependentAgenciesPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Official Resources/i })).toBeInTheDocument();
    });
  });

  // ========== Government Corporations Page ==========
  describe('CorporationsPage', () => {
    it('renders the page title', () => {
      render(<CorporationsPage />, { wrapper });
      expect(
        screen.getByRole('heading', { level: 1, name: /Government Corporations/i })
      ).toBeInTheDocument();
    });

    it('renders types of corporations section', () => {
      render(<CorporationsPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Types of Government Corporations/i })).toBeInTheDocument();
    });

    it('renders federal government corporations section', () => {
      render(<CorporationsPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Federal Government Corporations/i })).toBeInTheDocument();
    });

    it('has official resources section', () => {
      render(<CorporationsPage />, { wrapper });
      expect(screen.getByRole('heading', { name: /Official Resources/i })).toBeInTheDocument();
    });

    it('renders external links to corporation websites', () => {
      render(<CorporationsPage />, { wrapper });
      const links = screen.getAllByRole('link');
      const uspsLink = links.find(link => link.getAttribute('href') === 'https://www.usps.com/');
      expect(uspsLink).toBeDefined();
    });
  });

  // ========== Common Elements Tests ==========
  describe('Common Elements', () => {
    const pages = [
      { Page: EOPPage, name: 'EOP' },
      { Page: CabinetPage, name: 'Cabinet' },
      { Page: IndependentAgenciesPage, name: 'Independent Agencies' },
      { Page: CorporationsPage, name: 'Corporations' },
    ];

    pages.forEach(({ Page, name }) => {
      it(`${name} page has back link to Executive Branch`, () => {
        render(<Page />, { wrapper });
        const backLink = screen.getByRole('link', { name: /Back to Executive Branch/i });
        expect(backLink).toHaveAttribute('href', '/knowledge-base/government/executive');
      });

      it(`${name} page has Official Resources section`, () => {
        render(<Page />, { wrapper });
        expect(screen.getByRole('heading', { name: /Official Resources/i })).toBeInTheDocument();
      });
    });
  });
});
