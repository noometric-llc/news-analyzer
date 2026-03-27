import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EvaluationSidebar } from '../EvaluationSidebar';

// Mock the sidebar store
const mockStore = {
  isCollapsed: false,
  toggle: vi.fn(),
  closeMobile: vi.fn(),
};

vi.mock('@/stores/evaluationSidebarStore', () => ({
  useEvaluationSidebarStore: () => mockStore,
}));

// Mock menu config
vi.mock('@/lib/menu-config', () => ({
  evaluationMenuItems: [
    { label: 'Overview', href: '/evaluation' },
    { label: 'Results', href: '/evaluation/results' },
    { label: 'Dataset Explorer', href: '/evaluation/datasets' },
    { label: 'Methodology', href: '/evaluation/methodology' },
  ],
}));

// Mock BaseSidebar
vi.mock('@/components/sidebar', () => ({
  BaseSidebar: ({
    menuItems,
    isCollapsed,
    onToggle,
    header,
    footer,
    ariaLabel,
    onNavigate,
    className,
  }: {
    menuItems: Array<{ label: string; href?: string; disabled?: boolean }>;
    isCollapsed: boolean;
    onToggle: () => void;
    header: React.ReactNode;
    footer: React.ReactNode;
    ariaLabel: string;
    onNavigate: () => void;
    className?: string;
  }) => (
    <nav
      data-testid="base-sidebar"
      data-collapsed={isCollapsed}
      data-menu-count={menuItems.length}
      aria-label={ariaLabel}
      className={className}
    >
      <div data-testid="sidebar-header">{header}</div>
      <ul data-testid="menu-items">
        {menuItems.map((item) => (
          <li key={item.label} data-disabled={item.disabled}>
            {item.label}
          </li>
        ))}
      </ul>
      <div data-testid="sidebar-footer">{footer}</div>
      <button data-testid="toggle-btn" onClick={onToggle}>
        Toggle
      </button>
      <button data-testid="navigate-btn" onClick={onNavigate}>
        Navigate
      </button>
    </nav>
  ),
}));

describe('EvaluationSidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockStore.isCollapsed = false;
  });

  // ====== Rendering Tests ======

  describe('Rendering', () => {
    it('renders BaseSidebar with correct props', () => {
      render(<EvaluationSidebar />);

      const sidebar = screen.getByTestId('base-sidebar');
      expect(sidebar).toBeInTheDocument();
      expect(sidebar).toHaveAttribute('aria-label', 'AI Evaluation navigation');
    });

    it('renders with custom className', () => {
      render(<EvaluationSidebar className="custom-class" />);

      const sidebar = screen.getByTestId('base-sidebar');
      expect(sidebar).toHaveClass('custom-class');
    });

    it('renders header with AI Evaluation link', () => {
      render(<EvaluationSidebar />);

      const header = screen.getByTestId('sidebar-header');
      const link = header.querySelector('a');
      expect(link).toHaveAttribute('href', '/evaluation');
      expect(link).toHaveTextContent('AI Evaluation');
    });

    it('renders footer with Knowledge Base link', () => {
      render(<EvaluationSidebar />);

      const footer = screen.getByTestId('sidebar-footer');
      const link = footer.querySelector('a');
      expect(link).toHaveAttribute('href', '/knowledge-base');
      expect(link).toHaveAttribute('title', 'Knowledge Base');
    });
  });

  // ====== Menu Items Tests ======

  describe('Menu Items', () => {
    it('renders four menu items', () => {
      render(<EvaluationSidebar />);

      const sidebar = screen.getByTestId('base-sidebar');
      expect(sidebar).toHaveAttribute('data-menu-count', '4');
    });

    it('renders Overview menu item', () => {
      render(<EvaluationSidebar />);

      const menuItems = screen.getByTestId('menu-items');
      expect(menuItems).toHaveTextContent('Overview');
    });

    it('renders Results menu item', () => {
      render(<EvaluationSidebar />);

      const menuItems = screen.getByTestId('menu-items');
      expect(menuItems).toHaveTextContent('Results');
    });

    it('renders Dataset Explorer menu item', () => {
      render(<EvaluationSidebar />);

      const menuItems = screen.getByTestId('menu-items');
      expect(menuItems).toHaveTextContent('Dataset Explorer');
    });

    it('renders Methodology menu item', () => {
      render(<EvaluationSidebar />);

      const menuItems = screen.getByTestId('menu-items');
      expect(menuItems).toHaveTextContent('Methodology');
    });
  });

  // ====== Store Integration Tests ======

  describe('Store Integration', () => {
    it('passes isCollapsed state to BaseSidebar', () => {
      mockStore.isCollapsed = true;

      render(<EvaluationSidebar />);

      const sidebar = screen.getByTestId('base-sidebar');
      expect(sidebar).toHaveAttribute('data-collapsed', 'true');
    });

    it('passes toggle function to BaseSidebar', () => {
      render(<EvaluationSidebar />);

      const toggleBtn = screen.getByTestId('toggle-btn');
      toggleBtn.click();

      expect(mockStore.toggle).toHaveBeenCalledTimes(1);
    });

    it('passes closeMobile function to BaseSidebar for navigation', () => {
      render(<EvaluationSidebar />);

      const navigateBtn = screen.getByTestId('navigate-btn');
      navigateBtn.click();

      expect(mockStore.closeMobile).toHaveBeenCalledTimes(1);
    });
  });

  // ====== Collapsed State Tests ======

  describe('Collapsed State', () => {
    it('shows only icon in footer when collapsed', () => {
      mockStore.isCollapsed = true;

      render(<EvaluationSidebar />);

      const footer = screen.getByTestId('sidebar-footer');
      const link = footer.querySelector('a');
      expect(link).toHaveClass('justify-center');
    });

    it('shows icon and text in footer when expanded', () => {
      mockStore.isCollapsed = false;

      render(<EvaluationSidebar />);

      const footer = screen.getByTestId('sidebar-footer');
      const link = footer.querySelector('a');
      expect(link).not.toHaveClass('justify-center');
      expect(footer).toHaveTextContent('Knowledge Base');
    });
  });
});
