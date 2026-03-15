import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AdministrationPage } from '../AdministrationPage';

// Mock next/navigation for KBBreadcrumbs
vi.mock('next/navigation', () => ({
  usePathname: () => '/knowledge-base/government/executive/administrations',
}));

// Mock next/link to render a plain anchor
vi.mock('next/link', () => ({
  default: ({ children, href, ...props }: { children: React.ReactNode; href: string }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

describe('AdministrationPage', () => {
  describe('Default Rendering', () => {
    it('renders the page title', () => {
      render(<AdministrationPage />);

      expect(
        screen.getByRole('heading', { level: 1, name: 'Presidential Administrations' })
      ).toBeInTheDocument();
    });

    it('renders breadcrumbs with correct path', () => {
      render(<AdministrationPage />);

      expect(screen.getByText('Knowledge Base')).toBeInTheDocument();
      expect(screen.getByText('Executive Branch')).toBeInTheDocument();
    });

    it('renders back link to Executive Branch', () => {
      render(<AdministrationPage />);

      const backLink = screen.getByText('Back to Executive Branch');
      expect(backLink.closest('a')).toHaveAttribute(
        'href',
        '/knowledge-base/government/executive'
      );
    });

    it('renders Current Administration placeholder section', () => {
      render(<AdministrationPage />);

      expect(screen.getByText('Current Administration')).toBeInTheDocument();
      expect(screen.getByText(/coming in kb-2\.2/i)).toBeInTheDocument();
    });

    it('renders Historical Administrations placeholder section', () => {
      render(<AdministrationPage />);

      expect(screen.getByText('Historical Administrations')).toBeInTheDocument();
      expect(screen.getByText(/coming in kb-2\.3/i)).toBeInTheDocument();
    });
  });

  describe('Loading State', () => {
    it('renders loading skeletons when isLoading is true', () => {
      render(<AdministrationPage isLoading={true} />);

      // Skeletons render animated pulse elements
      expect(document.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);

      // Placeholder content should NOT be visible during loading
      expect(screen.queryByText('Current Administration')).not.toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('renders error message when error prop is provided', () => {
      render(<AdministrationPage error="Network error" />);

      expect(screen.getByText('Failed to load administration data')).toBeInTheDocument();
      expect(screen.getByText('Network error')).toBeInTheDocument();
    });

    it('does not render placeholder sections when in error state', () => {
      render(<AdministrationPage error="Something went wrong" />);

      expect(screen.queryByText('Current Administration')).not.toBeInTheDocument();
      expect(screen.queryByText('Historical Administrations')).not.toBeInTheDocument();
    });
  });
});
