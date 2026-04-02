import { describe, it, expect } from 'vitest';
import { computeWeightedF1, computeWeightedMetric } from '../evaluation';
import type { BranchResult } from '@/types/evaluation';

const mockBranches: Record<string, Record<string, BranchResult>> = {
  legislative: {
    'Claude Sonnet': { precision: 0.426, recall: 0.977, f1: 0.593, true_positives: 296, false_positives: 399, false_negatives: 7, article_count: 53 },
    'spaCy en_core_web_sm': { precision: 0.151, recall: 0.963, f1: 0.261, true_positives: 288, false_positives: 1620, false_negatives: 11, article_count: 53 },
  },
  executive: {
    'Claude Sonnet': { precision: 0.432, recall: 1.0, f1: 0.603, true_positives: 123, false_positives: 162, false_negatives: 0, article_count: 20 },
    'spaCy en_core_web_sm': { precision: 0.22, recall: 0.983, f1: 0.359, true_positives: 114.5, false_positives: 406, false_negatives: 2, article_count: 20 },
  },
  judicial: {
    'Claude Sonnet': { precision: 0.456, recall: 0.938, f1: 0.614, true_positives: 75.5, false_positives: 90, false_negatives: 5, article_count: 15 },
    'spaCy en_core_web_sm': { precision: 0.192, recall: 0.925, f1: 0.318, true_positives: 74, false_positives: 311, false_negatives: 6, article_count: 15 },
  },
  conll: {
    'Claude Sonnet': { precision: 0.789, recall: 0.963, f1: 0.867, true_positives: 78.5, false_positives: 21, false_negatives: 3, article_count: 25 },
    'spaCy en_core_web_sm': { precision: 0.96, recall: 0.856, f1: 0.905, true_positives: 71.5, false_positives: 3, false_negatives: 12, article_count: 25 },
  },
};

describe('computeWeightedF1', () => {
  it('computes weighted F1 across all branches', () => {
    const result = computeWeightedF1(mockBranches, 'Claude Sonnet');
    // (0.593*53 + 0.603*20 + 0.614*15 + 0.867*25) / (53+20+15+25)
    const expected = (0.593 * 53 + 0.603 * 20 + 0.614 * 15 + 0.867 * 25) / 113;
    expect(result).toBeCloseTo(expected, 4);
  });

  it('computes weighted F1 for gov-domain only', () => {
    const govFilter = ['legislative', 'executive', 'judicial'];
    const result = computeWeightedF1(mockBranches, 'Claude Sonnet', govFilter);
    const expected = (0.593 * 53 + 0.603 * 20 + 0.614 * 15) / 88;
    expect(result).toBeCloseTo(expected, 4);
  });

  it('computes weighted F1 for a single branch', () => {
    const result = computeWeightedF1(mockBranches, 'Claude Sonnet', ['judicial']);
    expect(result).toBeCloseTo(0.614, 4);
  });

  it('returns 0 for unknown extractor', () => {
    const result = computeWeightedF1(mockBranches, 'NonexistentModel');
    expect(result).toBe(0);
  });

  it('returns 0 for empty branches', () => {
    const result = computeWeightedF1({}, 'Claude Sonnet');
    expect(result).toBe(0);
  });

  it('returns 0 when filter matches no branches', () => {
    const result = computeWeightedF1(mockBranches, 'Claude Sonnet', ['nonexistent']);
    expect(result).toBe(0);
  });
});

describe('computeWeightedMetric', () => {
  it('computes weighted precision', () => {
    const result = computeWeightedMetric(mockBranches, 'spaCy en_core_web_sm', 'precision');
    const expected = (0.151 * 53 + 0.22 * 20 + 0.192 * 15 + 0.96 * 25) / 113;
    expect(result).toBeCloseTo(expected, 4);
  });

  it('computes weighted recall', () => {
    const result = computeWeightedMetric(mockBranches, 'Claude Sonnet', 'recall');
    const expected = (0.977 * 53 + 1.0 * 20 + 0.938 * 15 + 0.963 * 25) / 113;
    expect(result).toBeCloseTo(expected, 4);
  });
});
