import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EntityTypeTable } from '../EntityTypeTable';
import type { BranchDetailResult } from '@/types/evaluation';

const mockBranchDetail: BranchDetailResult = {
  branch: 'judicial',
  extractors: {
    'spaCy en_core_web_sm': {
      overall: { precision: 0.192, recall: 0.925, f1: 0.318, true_positives: 74, false_positives: 311, false_negatives: 6 },
      byEntityType: {
        person: { tp: 27, fp: 137, fn: 0 },
        government_org: { tp: 17, fp: 62, fn: 4 },
        organization: { tp: 0.5, fp: 41, fn: 0 },
        location: { tp: 13, fp: 18, fn: 1 },
        event: { tp: 0, fp: 0, fn: 1 },
        concept: { tp: 16.5, fp: 52, fn: 0 },
        legislation: { tp: 0, fp: 1, fn: 0 },
      },
    },
    'Claude Sonnet': {
      overall: { precision: 0.456, recall: 0.938, f1: 0.614, true_positives: 75.5, false_positives: 90, false_negatives: 5 },
      byEntityType: {
        person: { tp: 27.5, fp: 32, fn: 0 },
        government_org: { tp: 18, fp: 25, fn: 4 },
        organization: { tp: 0, fp: 0, fn: 0 },
        location: { tp: 14, fp: 1, fn: 0 },
        event: { tp: 0, fp: 4, fn: 1 },
        concept: { tp: 16, fp: 27, fn: 0 },
        legislation: { tp: 0, fp: 1, fn: 0 },
      },
    },
  },
};

describe('EntityTypeTable', () => {
  it('renders 7 entity type rows', () => {
    render(<EntityTypeTable branchDetails={[mockBranchDetail]} />);
    expect(screen.getByText('Person')).toBeInTheDocument();
    expect(screen.getByText('Government Org')).toBeInTheDocument();
    expect(screen.getByText('Organization')).toBeInTheDocument();
    expect(screen.getByText('Location')).toBeInTheDocument();
    expect(screen.getByText('Event')).toBeInTheDocument();
    expect(screen.getByText('Concept')).toBeInTheDocument();
    expect(screen.getByText('Legislation')).toBeInTheDocument();
  });

  it('renders extractor column headers', () => {
    render(<EntityTypeTable branchDetails={[mockBranchDetail]} />);
    expect(screen.getByText('spaCy en_core_web_sm')).toBeInTheDocument();
    expect(screen.getByText('Claude Sonnet')).toBeInTheDocument();
  });

  it('renders TP/FP/FN column headers', () => {
    render(<EntityTypeTable branchDetails={[mockBranchDetail]} />);
    const tpHeaders = screen.getAllByText('TP');
    const fpHeaders = screen.getAllByText('FP');
    const fnHeaders = screen.getAllByText('FN');
    // 2 of each (one per extractor)
    expect(tpHeaders).toHaveLength(2);
    expect(fpHeaders).toHaveLength(2);
    expect(fnHeaders).toHaveLength(2);
  });

  it('displays fractional values with one decimal', () => {
    render(<EntityTypeTable branchDetails={[mockBranchDetail]} />);
    // organization tp for spaCy is 0.5, concept tp for spaCy is 16.5
    expect(screen.getByText('0.5')).toBeInTheDocument();
    expect(screen.getByText('16.5')).toBeInTheDocument();
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<EntityTypeTable branchDetails={[]} isLoading />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('shows empty message when no data', () => {
    render(<EntityTypeTable branchDetails={[]} />);
    expect(screen.getByText('No entity type data available.')).toBeInTheDocument();
  });

  it('aggregates across multiple branches', () => {
    const secondBranch: BranchDetailResult = {
      branch: 'executive',
      extractors: {
        'spaCy en_core_web_sm': {
          overall: { precision: 0.22, recall: 0.983, f1: 0.359, true_positives: 114.5, false_positives: 406, false_negatives: 2 },
          byEntityType: {
            person: { tp: 60, fp: 147, fn: 0 },
            government_org: { tp: 1.5, fp: 1, fn: 1 },
            organization: { tp: 14, fp: 87, fn: 0 },
            location: { tp: 23, fp: 57, fn: 0 },
            event: { tp: 2, fp: 7, fn: 0 },
            concept: { tp: 14, fp: 107, fn: 1 },
            legislation: { tp: 0, fp: 0, fn: 0 },
          },
        },
        'Claude Sonnet': {
          overall: { precision: 0.432, recall: 1.0, f1: 0.603, true_positives: 123, false_positives: 162, false_negatives: 0 },
          byEntityType: {
            person: { tp: 60, fp: 50, fn: 0 },
            government_org: { tp: 20, fp: 30, fn: 0 },
            organization: { tp: 10, fp: 10, fn: 0 },
            location: { tp: 20, fp: 5, fn: 0 },
            event: { tp: 3, fp: 2, fn: 0 },
            concept: { tp: 10, fp: 65, fn: 0 },
            legislation: { tp: 0, fp: 0, fn: 0 },
          },
        },
      },
    };

    render(<EntityTypeTable branchDetails={[mockBranchDetail, secondBranch]} />);
    // spaCy person TP should be 27 + 60 = 87
    expect(screen.getByText('87')).toBeInTheDocument();
  });
});
