import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ArticleFilters } from '../ArticleFilters';

const defaultProps = {
  branch: '',
  perturbationType: '',
  difficulty: '',
  faithful: 'all',
  perturbationOptions: ['wrong_party', 'wrong_committee', 'wrong_state'],
  onBranchChange: vi.fn(),
  onPerturbationTypeChange: vi.fn(),
  onDifficultyChange: vi.fn(),
  onFaithfulChange: vi.fn(),
  onReset: vi.fn(),
};

describe('ArticleFilters', () => {
  it('renders 4 filter selects', () => {
    render(<ArticleFilters {...defaultProps} />);

    expect(screen.getByLabelText('Branch')).toBeInTheDocument();
    expect(screen.getByLabelText('Perturbation')).toBeInTheDocument();
    expect(screen.getByLabelText('Difficulty')).toBeInTheDocument();
    expect(screen.getByLabelText('Faithful')).toBeInTheDocument();
  });

  it('renders perturbation options from props', () => {
    render(<ArticleFilters {...defaultProps} />);

    const select = screen.getByLabelText('Perturbation');
    expect(select).toContainHTML('wrong_party');
    expect(select).toContainHTML('wrong_committee');
    expect(select).toContainHTML('wrong_state');
  });

  it('calls onBranchChange when branch is selected', () => {
    const onBranchChange = vi.fn();
    render(<ArticleFilters {...defaultProps} onBranchChange={onBranchChange} />);

    fireEvent.change(screen.getByLabelText('Branch'), { target: { value: 'legislative' } });
    expect(onBranchChange).toHaveBeenCalledWith('legislative');
  });

  it('calls onDifficultyChange when difficulty is selected', () => {
    const onDifficultyChange = vi.fn();
    render(<ArticleFilters {...defaultProps} onDifficultyChange={onDifficultyChange} />);

    fireEvent.change(screen.getByLabelText('Difficulty'), { target: { value: 'HARD' } });
    expect(onDifficultyChange).toHaveBeenCalledWith('HARD');
  });

  it('does not show reset button when no filters active', () => {
    render(<ArticleFilters {...defaultProps} />);

    expect(screen.queryByText('Reset filters')).not.toBeInTheDocument();
  });

  it('shows reset button when a filter is active', () => {
    render(<ArticleFilters {...defaultProps} branch="legislative" />);

    expect(screen.getByText('Reset filters')).toBeInTheDocument();
  });

  it('calls onReset when reset button is clicked', () => {
    const onReset = vi.fn();
    render(<ArticleFilters {...defaultProps} branch="legislative" onReset={onReset} />);

    screen.getByText('Reset filters').click();
    expect(onReset).toHaveBeenCalledTimes(1);
  });
});
