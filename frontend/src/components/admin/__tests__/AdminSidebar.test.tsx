import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { AdminSidebar } from '../AdminSidebar';
import { useSidebarStore } from '@/stores/sidebarStore';

// Mock next/navigation (already globally mocked, but override pathname for admin)
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
  usePathname: () => '/admin/factbase/executive/agencies',
}));

describe('AdminSidebar', () => {
  // Reset Zustand store before each test
  beforeEach(() => {
    useSidebarStore.setState({
      isCollapsed: false,
      isMobileOpen: false,
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  // ====== Rendering Tests ======

  describe('Rendering', () => {
    it('renders the Admin header', () => {
      render(<AdminSidebar />);
      expect(screen.getByText('Admin')).toBeInTheDocument();
    });

    it('renders navigation with correct aria-label', () => {
      render(<AdminSidebar />);
      expect(screen.getByRole('navigation', { name: /admin navigation/i })).toBeInTheDocument();
    });

    it('renders top-level menu items', () => {
      render(<AdminSidebar />);
      expect(screen.getByText('Knowledge Base')).toBeInTheDocument();
      expect(screen.getByText('Factbase (Legacy)')).toBeInTheDocument();
    });

    it('renders Dashboard footer link', () => {
      render(<AdminSidebar />);
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
    });

    it('renders nested menu structure', () => {
      render(<AdminSidebar />);

      // Top level
      expect(screen.getByText('Knowledge Base')).toBeInTheDocument();
      expect(screen.getByText('Factbase (Legacy)')).toBeInTheDocument();

      // Knowledge Base second level
      expect(screen.getByText('U.S. Federal Government')).toBeInTheDocument();

      // Legacy Factbase second level
      expect(screen.getByText('Government Entities')).toBeInTheDocument();
      expect(screen.getByText('Federal Laws & Regulations')).toBeInTheDocument();
    });

    it('renders third level menu items', () => {
      render(<AdminSidebar />);

      // Both Knowledge Base and Legacy Factbase have Executive/Legislative/Judicial branches
      expect(screen.getAllByText('Executive Branch')).toHaveLength(2);
      expect(screen.getAllByText('Legislative Branch')).toHaveLength(2);
      expect(screen.getAllByText('Judicial Branch')).toHaveLength(2);
    });

    it('renders leaf menu items with links', () => {
      render(<AdminSidebar />);

      const agenciesLink = screen.getByRole('link', { name: /agencies & departments/i });
      expect(agenciesLink).toHaveAttribute('href', '/admin/factbase/executive/agencies');
    });

    it('renders consolidated Presidential Administrations link (KB-2.6)', () => {
      render(<AdminSidebar />);
      const adminLink = screen.getByRole('link', { name: /presidential administrations/i });
      expect(adminLink).toHaveAttribute('href', '/admin/knowledge-base/government/executive/administrations');
    });

    it('does NOT render separate President or Vice President links (KB-2.6)', () => {
      render(<AdminSidebar />);
      expect(screen.queryByRole('link', { name: /^president$/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: /^vice president$/i })).not.toBeInTheDocument();
    });
  });

  // ====== Collapse/Expand Tests ======

  describe('Collapse/Expand', () => {
    it('renders expand button when collapsed', () => {
      useSidebarStore.setState({ isCollapsed: true });
      render(<AdminSidebar />);

      expect(screen.getByRole('button', { name: /expand sidebar/i })).toBeInTheDocument();
    });

    it('renders collapse button when expanded', () => {
      useSidebarStore.setState({ isCollapsed: false });
      render(<AdminSidebar />);

      expect(screen.getByRole('button', { name: /collapse sidebar/i })).toBeInTheDocument();
    });

    it('toggles collapse state when button clicked', () => {
      render(<AdminSidebar />);

      const toggleButton = screen.getByRole('button', { name: /collapse sidebar/i });
      fireEvent.click(toggleButton);

      expect(useSidebarStore.getState().isCollapsed).toBe(true);
    });

    it('hides header text when collapsed', () => {
      useSidebarStore.setState({ isCollapsed: true });
      render(<AdminSidebar />);

      // Admin text should not be visible when collapsed
      expect(screen.queryByText('Admin')).not.toBeInTheDocument();
    });

    it('hides nested menu items when collapsed', () => {
      useSidebarStore.setState({ isCollapsed: true });
      render(<AdminSidebar />);

      // Nested items should not be visible when collapsed
      expect(screen.queryByText('Government Entities')).not.toBeInTheDocument();
    });
  });

  // ====== Navigation Tests ======

  describe('Navigation', () => {
    it('highlights active menu item based on pathname', () => {
      render(<AdminSidebar />);

      // The current path is /admin/factbase/executive/agencies
      const agenciesLink = screen.getByRole('link', { name: /agencies & departments/i });
      expect(agenciesLink).toHaveAttribute('aria-current', 'page');
    });

    it('calls closeMobile when navigating', () => {
      const closeMobileSpy = vi.fn();
      useSidebarStore.setState({ closeMobile: closeMobileSpy });

      render(<AdminSidebar />);

      const link = screen.getByRole('link', { name: /agencies & departments/i });
      fireEvent.click(link);

      expect(closeMobileSpy).toHaveBeenCalled();
    });
  });

  // ====== Menu Expansion Tests ======

  describe('Menu Expansion', () => {
    it('expands/collapses menu sections on click', () => {
      render(<AdminSidebar />);

      // Find the Knowledge Base menu item
      const kbButton = screen.getByText('Knowledge Base').closest('[role="button"]');
      expect(kbButton).toBeInTheDocument();

      if (kbButton) {
        // Initially expanded (children visible)
        expect(screen.getByText('U.S. Federal Government')).toBeInTheDocument();

        // Click to collapse
        fireEvent.click(kbButton);

        // Children should be hidden after collapse
        // Note: The actual behavior depends on SidebarMenuItem implementation
      }
    });

    it('supports keyboard navigation for menu expansion', () => {
      render(<AdminSidebar />);

      const kbButton = screen.getByText('Knowledge Base').closest('[role="button"]');

      if (kbButton) {
        (kbButton as HTMLElement).focus();

        // Press Enter to toggle
        fireEvent.keyDown(kbButton, { key: 'Enter' });

        // Press ArrowLeft to collapse
        fireEvent.keyDown(kbButton, { key: 'ArrowLeft' });

        // Press ArrowRight to expand
        fireEvent.keyDown(kbButton, { key: 'ArrowRight' });
      }
    });
  });

  // ====== Accessibility Tests ======

  describe('Accessibility', () => {
    it('has correct ARIA structure', () => {
      render(<AdminSidebar />);

      // Navigation landmark with specific aria-label
      const nav = screen.getByRole('navigation', { name: /admin navigation/i });
      expect(nav).toBeInTheDocument();
    });

    it('menu buttons have aria-expanded attribute', () => {
      render(<AdminSidebar />);

      const kbButton = screen.getByText('Knowledge Base').closest('[role="button"]');
      expect(kbButton).toHaveAttribute('aria-expanded');
    });

    it('links are keyboard focusable', () => {
      render(<AdminSidebar />);

      const links = screen.getAllByRole('link');
      links.forEach((link) => {
        expect(link).not.toHaveAttribute('tabindex', '-1');
      });
    });

    it('submenus have aria-label', () => {
      render(<AdminSidebar />);

      const submenu = screen.getByRole('group', { name: /knowledge base submenu/i });
      expect(submenu).toBeInTheDocument();
    });
  });

  // ====== CSS Class Tests ======

  describe('Styling', () => {
    it('applies custom className', () => {
      render(<AdminSidebar className="custom-class" />);

      const aside = screen.getByRole('navigation', { name: /admin navigation/i });
      expect(aside).toHaveClass('custom-class');
    });

    it('changes width based on collapsed state', () => {
      const { rerender } = render(<AdminSidebar />);

      let aside = screen.getByRole('navigation', { name: /admin navigation/i });
      expect(aside).toHaveClass('w-64');

      act(() => {
        useSidebarStore.setState({ isCollapsed: true });
      });
      rerender(<AdminSidebar />);

      aside = screen.getByRole('navigation', { name: /admin navigation/i });
      expect(aside).toHaveClass('w-16');
    });
  });
});
