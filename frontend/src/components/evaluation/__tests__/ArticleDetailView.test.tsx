import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ArticleDetailView } from '../ArticleDetailView';
import type { SyntheticArticle } from '@/types/evaluation';

const faithfulArticle: SyntheticArticle = {
  id: 'test-1',
  batchId: 'batch-1',
  articleText: 'WASHINGTON — The Senate confirmed John Smith to a federal position.',
  articleType: 'news_report',
  isFaithful: true,
  perturbationType: null,
  difficulty: 'EASY',
  sourceFacts: {
    topic: 'Judge John Smith',
    branch: 'judicial',
    facts: [
      { subject: 'John Smith', predicate: 'appointment', object: 'federal judge', entity_type: 'Judge' },
      { subject: 'Senate', predicate: 'confirmed', object: 'John Smith', entity_type: 'GovernmentOrganization' },
    ],
    related_entity_ids: [],
  },
  groundTruth: {
    changed_facts: [],
    expected_findings: [],
  },
  modelUsed: 'claude-sonnet',
  tokensUsed: 500,
  createdAt: '2026-03-20T10:00:00',
};

const perturbedArticle: SyntheticArticle = {
  ...faithfulArticle,
  id: 'test-2',
  isFaithful: false,
  perturbationType: 'wrong_party',
  groundTruth: {
    changed_facts: [
      { predicate: 'party_affiliation', original_value: 'Democratic', perturbed_value: 'Republican' },
    ],
    expected_findings: [
      "Incorrect party_affiliation: stated 'Republican', should be 'Democratic'",
    ],
  },
};

describe('ArticleDetailView', () => {
  describe('Article text', () => {
    it('renders full article text', () => {
      render(<ArticleDetailView article={faithfulArticle} />);
      expect(screen.getByText(/The Senate confirmed John Smith/)).toBeInTheDocument();
    });
  });

  describe('Metadata', () => {
    it('shows branch from sourceFacts', () => {
      render(<ArticleDetailView article={faithfulArticle} />);
      expect(screen.getByText('judicial')).toBeInTheDocument();
    });

    it('shows topic from sourceFacts', () => {
      render(<ArticleDetailView article={faithfulArticle} />);
      expect(screen.getByText('Judge John Smith')).toBeInTheDocument();
    });

    it('shows faithful badge for faithful articles', () => {
      render(<ArticleDetailView article={faithfulArticle} />);
      expect(screen.getByText('Faithful')).toBeInTheDocument();
    });

    it('shows perturbed badge for perturbed articles', () => {
      render(<ArticleDetailView article={perturbedArticle} />);
      expect(screen.getByText('Perturbed')).toBeInTheDocument();
    });
  });

  describe('Entity badges', () => {
    it('renders entity badges from sourceFacts.facts', () => {
      render(<ArticleDetailView article={faithfulArticle} />);
      expect(screen.getByText('John Smith')).toBeInTheDocument();
      expect(screen.getByText('Senate')).toBeInTheDocument();
    });

    it('deduplicates entities by subject text', () => {
      const articleWithDupes: SyntheticArticle = {
        ...faithfulArticle,
        sourceFacts: {
          ...faithfulArticle.sourceFacts as Record<string, unknown>,
          facts: [
            { subject: 'John Smith', predicate: 'a', object: 'b', entity_type: 'Judge' },
            { subject: 'John Smith', predicate: 'c', object: 'd', entity_type: 'Judge' },
          ],
        },
      };
      render(<ArticleDetailView article={articleWithDupes} />);
      // Should only appear once
      const badges = screen.getAllByText('John Smith');
      expect(badges).toHaveLength(1);
    });
  });

  describe('Perturbation details', () => {
    it('does not show perturbation section for faithful articles', () => {
      render(<ArticleDetailView article={faithfulArticle} />);
      expect(screen.queryByText('Perturbation Details')).not.toBeInTheDocument();
    });

    it('shows changed facts table for perturbed articles', () => {
      render(<ArticleDetailView article={perturbedArticle} />);
      expect(screen.getByText('Perturbation Details')).toBeInTheDocument();
      expect(screen.getByText('party_affiliation')).toBeInTheDocument();
      expect(screen.getByText('Democratic')).toBeInTheDocument();
      expect(screen.getByText('Republican')).toBeInTheDocument();
    });

    it('shows expected findings for perturbed articles', () => {
      render(<ArticleDetailView article={perturbedArticle} />);
      expect(screen.getByText(/Incorrect party_affiliation/)).toBeInTheDocument();
    });
  });

  describe('Source facts', () => {
    it('renders collapsible source facts section', () => {
      render(<ArticleDetailView article={faithfulArticle} />);
      expect(screen.getByText('Source Facts (raw)')).toBeInTheDocument();
    });
  });
});
