import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Home from '../page';

describe('Home Page', () => {
  describe('Header', () => {
    it('renders the main title', () => {
      render(<Home />);
      expect(screen.getByRole('heading', { name: /newsanalyzer v2/i })).toBeInTheDocument();
    });

    it('renders the tagline', () => {
      render(<Home />);
      expect(screen.getByText(/independent news analysis, fact-checking, and bias detection/i)).toBeInTheDocument();
    });
  });

  describe('Primary Navigation CTAs', () => {
    it('renders Knowledge Base button', () => {
      render(<Home />);
      const kbLink = screen.getByRole('link', { name: /explore knowledge base/i });
      expect(kbLink).toBeInTheDocument();
      expect(kbLink).toHaveAttribute('href', '/knowledge-base');
    });

    it('renders Article Analyzer button', () => {
      render(<Home />);
      const aaLink = screen.getByRole('link', { name: /article analyzer/i });
      expect(aaLink).toBeInTheDocument();
      expect(aaLink).toHaveAttribute('href', '/article-analyzer');
    });

    it('renders AI Evaluation button', () => {
      render(<Home />);
      const evalLink = screen.getByRole('link', { name: /ai evaluation/i });
      expect(evalLink).toBeInTheDocument();
      expect(evalLink).toHaveAttribute('href', '/evaluation');
    });

    it('all primary buttons have equal visual weight', () => {
      render(<Home />);
      const kbLink = screen.getByRole('link', { name: /explore knowledge base/i });
      const aaLink = screen.getByRole('link', { name: /article analyzer/i });
      const evalLink = screen.getByRole('link', { name: /ai evaluation/i });

      // All should have the same primary styling
      expect(kbLink).toHaveClass('bg-primary');
      expect(aaLink).toHaveClass('bg-primary');
      expect(evalLink).toHaveClass('bg-primary');
    });

    it('primary buttons have focus-visible styles for accessibility', () => {
      render(<Home />);
      const kbLink = screen.getByRole('link', { name: /explore knowledge base/i });
      const aaLink = screen.getByRole('link', { name: /article analyzer/i });
      const evalLink = screen.getByRole('link', { name: /ai evaluation/i });

      expect(kbLink).toHaveClass('focus-visible:ring-2');
      expect(aaLink).toHaveClass('focus-visible:ring-2');
      expect(evalLink).toHaveClass('focus-visible:ring-2');
    });
  });

  describe('Quick Links', () => {
    it('renders Government Organizations link', () => {
      render(<Home />);
      const link = screen.getByRole('link', { name: /government organizations/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '/knowledge-base/government');
    });

    it('renders People link', () => {
      render(<Home />);
      const link = screen.getByRole('link', { name: /^people$/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '/knowledge-base/people');
    });

    it('renders Committees link', () => {
      render(<Home />);
      const link = screen.getByRole('link', { name: /^committees$/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '/knowledge-base/committees');
    });

    it('renders Extracted Entities link', () => {
      render(<Home />);
      const link = screen.getByRole('link', { name: /extracted entities/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '/article-analyzer/entities');
    });

    it('renders Evaluation Results link', () => {
      render(<Home />);
      const link = screen.getByRole('link', { name: /evaluation results/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '/evaluation/results');
    });

    it('all quick links point to valid routes', () => {
      render(<Home />);
      const links = screen.getAllByRole('link');

      // Check that no links point to old legacy routes
      const legacyRoutes = ['/entities', '/members'];
      links.forEach((link) => {
        const href = link.getAttribute('href');
        legacyRoutes.forEach((legacyRoute) => {
          expect(href).not.toBe(legacyRoute);
        });
      });
    });
  });

  describe('Feature Cards', () => {
    it('renders Factual Accuracy card', () => {
      render(<Home />);
      expect(screen.getByText(/factual accuracy/i)).toBeInTheDocument();
      expect(screen.getByText(/cross-reference claims against authoritative sources/i)).toBeInTheDocument();
    });

    it('renders Logical Fallacies card', () => {
      render(<Home />);
      expect(screen.getByText(/logical fallacies/i)).toBeInTheDocument();
      expect(screen.getByText(/identify errors in reasoning/i)).toBeInTheDocument();
    });

    it('renders Cognitive Biases card', () => {
      render(<Home />);
      expect(screen.getByText(/cognitive biases/i)).toBeInTheDocument();
      expect(screen.getByText(/detect emotional manipulation/i)).toBeInTheDocument();
    });

    it('renders Source Reliability card', () => {
      render(<Home />);
      expect(screen.getByText(/source reliability/i)).toBeInTheDocument();
      expect(screen.getByText(/track historical accuracy/i)).toBeInTheDocument();
    });
  });

  describe('Footer', () => {
    it('renders footer information', () => {
      render(<Home />);
      expect(screen.getByText(/hosted independently in europe/i)).toBeInTheDocument();
      expect(screen.getByText(/open source/i)).toBeInTheDocument();
      expect(screen.getByText(/community driven/i)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('has a main landmark', () => {
      render(<Home />);
      expect(screen.getByRole('main')).toBeInTheDocument();
    });

    it('has a level 1 heading', () => {
      render(<Home />);
      const heading = screen.getByRole('heading', { level: 1 });
      expect(heading).toBeInTheDocument();
    });

    it('all links are keyboard accessible', () => {
      render(<Home />);
      const links = screen.getAllByRole('link');
      expect(links.length).toBeGreaterThan(0);
      // All links should be focusable by default
      links.forEach((link) => {
        expect(link).not.toHaveAttribute('tabindex', '-1');
      });
    });
  });

  describe('Link Validation', () => {
    it('contains no broken legacy routes', () => {
      render(<Home />);
      const links = screen.getAllByRole('link');

      // These old routes should not exist
      const brokenRoutes = ['/entities', '/members', '/factbase'];

      links.forEach((link) => {
        const href = link.getAttribute('href');
        brokenRoutes.forEach((broken) => {
          expect(href).not.toBe(broken);
        });
      });
    });

    it('all links start with valid route prefixes', () => {
      render(<Home />);
      const links = screen.getAllByRole('link');

      const validPrefixes = ['/knowledge-base', '/article-analyzer', '/evaluation'];

      links.forEach((link) => {
        const href = link.getAttribute('href');
        const hasValidPrefix = validPrefixes.some((prefix) => href?.startsWith(prefix));
        expect(hasValidPrefix).toBe(true);
      });
    });
  });
});
