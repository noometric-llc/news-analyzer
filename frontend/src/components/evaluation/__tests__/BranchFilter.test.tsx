import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BranchFilter } from '../BranchFilter';

describe('BranchFilter', () => {
  it('renders 5 branch tabs', () => {
    render(<BranchFilter selected="all" onChange={vi.fn()} />);
    const tabs = screen.getAllByRole('tab');
    expect(tabs).toHaveLength(5);
  });

  it('renders correct labels', () => {
    render(<BranchFilter selected="all" onChange={vi.fn()} />);
    expect(screen.getByText('All')).toBeInTheDocument();
    expect(screen.getByText('Legislative')).toBeInTheDocument();
    expect(screen.getByText('Executive')).toBeInTheDocument();
    expect(screen.getByText('Judicial')).toBeInTheDocument();
    expect(screen.getByText('CoNLL')).toBeInTheDocument();
  });

  it('marks selected tab as active', () => {
    render(<BranchFilter selected="judicial" onChange={vi.fn()} />);
    const judicialTab = screen.getByText('Judicial');
    expect(judicialTab).toHaveAttribute('aria-selected', 'true');
    expect(judicialTab).toHaveClass('bg-primary');
  });

  it('marks non-selected tabs as inactive', () => {
    render(<BranchFilter selected="judicial" onChange={vi.fn()} />);
    const allTab = screen.getByText('All');
    expect(allTab).toHaveAttribute('aria-selected', 'false');
    expect(allTab).toHaveClass('bg-muted');
  });

  it('calls onChange with correct branch value on click', () => {
    const onChange = vi.fn();
    render(<BranchFilter selected="all" onChange={onChange} />);

    screen.getByText('Executive').click();
    expect(onChange).toHaveBeenCalledWith('executive');
  });

  it('has tablist role for accessibility', () => {
    render(<BranchFilter selected="all" onChange={vi.fn()} />);
    expect(screen.getByRole('tablist')).toBeInTheDocument();
  });
});
