import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Overview } from '../Overview';
import { EntityTaxonomy } from '../EntityTaxonomy';
import { DatasetPipeline } from '../DatasetPipeline';
import { EvalMetrics } from '../EvalMetrics';
import { FuzzyMatching } from '../FuzzyMatching';
import { ResultsSummary } from '../ResultsSummary';
import { LimitationsFutureWork } from '../LimitationsFutureWork';
import { ToolsBadges } from '../ToolsBadges';
import {
  ENTITY_TAXONOMY,
  FUZZY_MATCHING_PRIORITIES,
  DATASET_STATS,
  AGGREGATE_RESULTS,
  PER_ENTITY_TYPE_RESULTS,
  TOOLS_USED,
  LIMITATIONS,
  FUTURE_WORK,
} from '@/lib/data/methodology';

describe('Data constants', () => {
  it('has 7 entity taxonomy entries', () => {
    expect(ENTITY_TAXONOMY).toHaveLength(7);
  });

  it('has 6 fuzzy matching priorities', () => {
    expect(FUZZY_MATCHING_PRIORITIES).toHaveLength(6);
  });

  it('has 4 dataset branches summing to 113 articles', () => {
    expect(DATASET_STATS.branches).toHaveLength(4);
    expect(DATASET_STATS.totals.articles).toBe(113);
  });

  it('has 4 aggregate result rows', () => {
    expect(AGGREGATE_RESULTS).toHaveLength(4);
  });

  it('has 6 per-entity-type result rows', () => {
    expect(PER_ENTITY_TYPE_RESULTS).toHaveLength(6);
  });

  it('has 9 tools', () => {
    expect(TOOLS_USED).toHaveLength(9);
  });

  it('has 4 limitations', () => {
    expect(LIMITATIONS).toHaveLength(4);
  });

  it('has 5 future work items', () => {
    expect(FUTURE_WORK).toHaveLength(5);
  });
});

describe('Overview', () => {
  it('renders headline stat callout', () => {
    render(<Overview />);
    expect(screen.getByText('0.60')).toBeInTheDocument();
    expect(screen.getByText('0.31')).toBeInTheDocument();
  });

  it('renders section heading', () => {
    render(<Overview />);
    expect(screen.getByText('Overview')).toBeInTheDocument();
  });
});

describe('EntityTaxonomy', () => {
  it('renders 7 entity type cards', () => {
    render(<EntityTaxonomy />);
    expect(screen.getByText('person')).toBeInTheDocument();
    expect(screen.getByText('government_org')).toBeInTheDocument();
    expect(screen.getByText('organization')).toBeInTheDocument();
    expect(screen.getByText('location')).toBeInTheDocument();
    expect(screen.getByText('event')).toBeInTheDocument();
    expect(screen.getByText('concept')).toBeInTheDocument();
    expect(screen.getByText('legislation')).toBeInTheDocument();
  });

  it('renders example entities', () => {
    render(<EntityTaxonomy />);
    expect(screen.getByText('Elizabeth Warren')).toBeInTheDocument();
    expect(screen.getByText('Senate')).toBeInTheDocument();
  });
});

describe('DatasetPipeline', () => {
  it('renders 4 pipeline stages', () => {
    render(<DatasetPipeline />);
    expect(screen.getByText('KB Facts')).toBeInTheDocument();
    expect(screen.getByText('Article Generation')).toBeInTheDocument();
    expect(screen.getByText('Automated Derivation')).toBeInTheDocument();
    expect(screen.getByText('Human Curation')).toBeInTheDocument();
  });

  it('renders dataset stats table with totals', () => {
    render(<DatasetPipeline />);
    expect(screen.getByText('113')).toBeInTheDocument();
    expect(screen.getByText('64')).toBeInTheDocument();
    expect(screen.getByText('601')).toBeInTheDocument();
  });
});

describe('EvalMetrics', () => {
  it('renders 3 metric formulas', () => {
    render(<EvalMetrics />);
    expect(screen.getByText('Precision =')).toBeInTheDocument();
    expect(screen.getByText('Recall =')).toBeInTheDocument();
    expect(screen.getByText('F1 =')).toBeInTheDocument();
  });

  it('renders TP/FP/FN legend descriptions', () => {
    render(<EvalMetrics />);
    expect(screen.getByText(/True Positive/)).toBeInTheDocument();
    expect(screen.getByText(/False Positive/)).toBeInTheDocument();
    expect(screen.getByText(/False Negative/)).toBeInTheDocument();
  });
});

describe('FuzzyMatching', () => {
  it('renders 6 priority rows', () => {
    render(<FuzzyMatching />);
    // Priority badges 1-6
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('6')).toBeInTheDocument();
  });

  it('renders motivating examples section', () => {
    render(<FuzzyMatching />);
    expect(screen.getByText('Why Fuzzy Matching?')).toBeInTheDocument();
    expect(screen.getByText('6-Priority Matching System')).toBeInTheDocument();
  });
});

describe('ResultsSummary', () => {
  it('renders aggregate results section', () => {
    render(<ResultsSummary />);
    expect(screen.getByText('Aggregate Comparison')).toBeInTheDocument();
    expect(screen.getByText('Per-Entity-Type Performance')).toBeInTheDocument();
    expect(screen.getByText('Cost / Quality Tradeoff')).toBeInTheDocument();
  });

  it('renders per-entity-type table', () => {
    render(<ResultsSummary />);
    expect(screen.getByText('Per-Entity-Type Performance')).toBeInTheDocument();
  });

  it('renders cost comparison', () => {
    render(<ResultsSummary />);
    expect(screen.getByText('+94%')).toBeInTheDocument();
  });
});

describe('LimitationsFutureWork', () => {
  it('renders 4 limitations', () => {
    render(<LimitationsFutureWork />);
    expect(screen.getByText(/Gold dataset size/)).toBeInTheDocument();
    expect(screen.getByText(/Single annotator/)).toBeInTheDocument();
  });

  it('renders 5 future work items', () => {
    render(<LimitationsFutureWork />);
    expect(screen.getByText('EVAL-3: Cognitive Bias Evaluation')).toBeInTheDocument();
    expect(screen.getByText('Prompt engineering')).toBeInTheDocument();
  });
});

describe('ToolsBadges', () => {
  it('renders all 9 tools', () => {
    render(<ToolsBadges />);
    expect(screen.getByText('Promptfoo')).toBeInTheDocument();
    expect(screen.getByText('spaCy')).toBeInTheDocument();
    expect(screen.getByText('Claude API')).toBeInTheDocument();
    expect(screen.getByText('Python')).toBeInTheDocument();
    expect(screen.getByText('Pydantic')).toBeInTheDocument();
    expect(screen.getByText('TypeScript')).toBeInTheDocument();
    expect(screen.getByText('Next.js')).toBeInTheDocument();
    expect(screen.getByText('Recharts')).toBeInTheDocument();
    expect(screen.getByText('GitHub Actions')).toBeInTheDocument();
  });

  it('groups by category', () => {
    render(<ToolsBadges />);
    // CSS uppercase renders visually but DOM text is lowercase
    expect(screen.getByText('Evaluation')).toBeInTheDocument();
    expect(screen.getByText('CI/CD')).toBeInTheDocument();
  });
});
