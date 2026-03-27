import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { computeEntityDiff } from '../ExtractionResults';
import { SampleArticleSelector, SAMPLE_ARTICLES } from '../SampleArticleSelector';
import { ExtractionInput } from '../ExtractionInput';
import { ComparisonMetrics } from '../ComparisonMetrics';
import type { ExtractedEntity, ExtractionResult } from '@/types/evaluation';

// --- Entity diff algorithm tests ---

describe('computeEntityDiff', () => {
  const spacyEntities: ExtractedEntity[] = [
    { text: 'Senate', entity_type: 'government_org', start: 35, end: 41, confidence: 0.95 },
    { text: 'Washington', entity_type: 'location', start: 0, end: 10, confidence: 0.90 },
    { text: 'EPA', entity_type: 'organization', start: 50, end: 53, confidence: 0.85 },
  ];

  const claudeEntities: ExtractedEntity[] = [
    { text: 'Senate', entity_type: 'government_org', start: 35, end: 41, confidence: 0.98 },
    { text: 'Washington', entity_type: 'location', start: 0, end: 10, confidence: 0.95 },
    { text: 'John Smith', entity_type: 'person', start: 100, end: 110, confidence: 0.92 },
  ];

  it('finds entities in both extractors', () => {
    const diff = computeEntityDiff(spacyEntities, claudeEntities);
    expect(diff.both).toHaveLength(2);
    expect(diff.both.map((e) => e.text)).toContain('Senate');
    expect(diff.both.map((e) => e.text)).toContain('Washington');
  });

  it('finds entities unique to spaCy', () => {
    const diff = computeEntityDiff(spacyEntities, claudeEntities);
    expect(diff.spacyOnly).toHaveLength(1);
    expect(diff.spacyOnly[0].text).toBe('EPA');
  });

  it('finds entities unique to Claude', () => {
    const diff = computeEntityDiff(spacyEntities, claudeEntities);
    expect(diff.claudeOnly).toHaveLength(1);
    expect(diff.claudeOnly[0].text).toBe('John Smith');
  });

  it('is case-insensitive for matching', () => {
    const spacy: ExtractedEntity[] = [
      { text: 'senate', entity_type: 'government_org', start: 0, end: 6, confidence: 0.9 },
    ];
    const claude: ExtractedEntity[] = [
      { text: 'Senate', entity_type: 'government_org', start: 0, end: 6, confidence: 0.9 },
    ];
    const diff = computeEntityDiff(spacy, claude);
    expect(diff.both).toHaveLength(1);
    expect(diff.spacyOnly).toHaveLength(0);
    expect(diff.claudeOnly).toHaveLength(0);
  });

  it('treats different entity types as different entities', () => {
    const spacy: ExtractedEntity[] = [
      { text: 'EPA', entity_type: 'organization', start: 0, end: 3, confidence: 0.9 },
    ];
    const claude: ExtractedEntity[] = [
      { text: 'EPA', entity_type: 'government_org', start: 0, end: 3, confidence: 0.9 },
    ];
    const diff = computeEntityDiff(spacy, claude);
    expect(diff.both).toHaveLength(0);
    expect(diff.spacyOnly).toHaveLength(1);
    expect(diff.claudeOnly).toHaveLength(1);
  });

  it('handles empty arrays', () => {
    const diff = computeEntityDiff([], []);
    expect(diff.both).toHaveLength(0);
    expect(diff.spacyOnly).toHaveLength(0);
    expect(diff.claudeOnly).toHaveLength(0);
  });
});

// --- SampleArticleSelector tests ---

describe('SampleArticleSelector', () => {
  it('renders sample buttons for each article', () => {
    render(<SampleArticleSelector onSelect={vi.fn()} />);
    expect(screen.getByText('Senate Confirmation')).toBeInTheDocument();
    expect(screen.getByText('Senator Profile')).toBeInTheDocument();
    expect(screen.getByText('CoNLL News')).toBeInTheDocument();
  });

  it('has 3 sample articles with gold entities', () => {
    expect(SAMPLE_ARTICLES).toHaveLength(3);
    SAMPLE_ARTICLES.forEach((article) => {
      expect(article.goldEntities.length).toBeGreaterThan(0);
      expect(article.text.length).toBeGreaterThan(0);
    });
  });

  it('calls onSelect when a sample is clicked', () => {
    const onSelect = vi.fn();
    render(<SampleArticleSelector onSelect={onSelect} />);
    screen.getByText('Senate Confirmation').click();
    expect(onSelect).toHaveBeenCalledWith(SAMPLE_ARTICLES[0]);
  });
});

// --- ExtractionInput tests ---

describe('ExtractionInput', () => {
  const defaultProps = {
    text: '',
    onTextChange: vi.fn(),
    onSubmit: vi.fn(),
    onClear: vi.fn(),
    isLoading: false,
    hasResults: false,
  };

  it('renders textarea and submit button', () => {
    render(<ExtractionInput {...defaultProps} />);
    expect(screen.getByPlaceholderText(/paste a news article/i)).toBeInTheDocument();
    expect(screen.getByText('Extract & Compare')).toBeInTheDocument();
  });

  it('renders cost disclaimer', () => {
    render(<ExtractionInput {...defaultProps} />);
    expect(screen.getByText(/Claude extraction uses API credits/)).toBeInTheDocument();
  });

  it('disables submit when text is empty', () => {
    render(<ExtractionInput {...defaultProps} />);
    expect(screen.getByText('Extract & Compare')).toBeDisabled();
  });

  it('enables submit when text is present', () => {
    render(<ExtractionInput {...defaultProps} text="Some article text" />);
    expect(screen.getByText('Extract & Compare')).not.toBeDisabled();
  });

  it('shows loading state', () => {
    render(<ExtractionInput {...defaultProps} text="text" isLoading />);
    expect(screen.getByText('Extracting...')).toBeInTheDocument();
  });

  it('shows Try Another button when results exist', () => {
    render(<ExtractionInput {...defaultProps} text="text" hasResults />);
    expect(screen.getByText('Try Another')).toBeInTheDocument();
  });

  it('shows character count', () => {
    render(<ExtractionInput {...defaultProps} text="Hello world" />);
    expect(screen.getByText('11 characters')).toBeInTheDocument();
  });
});

// --- ComparisonMetrics tests ---

describe('ComparisonMetrics', () => {
  const spacyResult: ExtractionResult = {
    entities: [
      { text: 'Senate', entity_type: 'government_org', start: 0, end: 6, confidence: 0.95 },
      { text: 'EPA', entity_type: 'organization', start: 10, end: 13, confidence: 0.85 },
    ],
    total_count: 2,
  };

  const claudeResult: ExtractionResult = {
    entities: [
      { text: 'Senate', entity_type: 'government_org', start: 0, end: 6, confidence: 0.98 },
      { text: 'John Smith', entity_type: 'person', start: 50, end: 60, confidence: 0.92 },
    ],
    total_count: 2,
  };

  it('returns null when missing one result', () => {
    const { container } = render(<ComparisonMetrics spacyResult={spacyResult} claudeResult={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('shows entity counts and overlap', () => {
    render(<ComparisonMetrics spacyResult={spacyResult} claudeResult={claudeResult} />);
    // Both extractors have 2 entities
    expect(screen.getByText('spaCy entities')).toBeInTheDocument();
    expect(screen.getByText('Claude entities')).toBeInTheDocument();
    expect(screen.getByText('Overlap')).toBeInTheDocument();
    expect(screen.getByText('spaCy only')).toBeInTheDocument();
    expect(screen.getByText('Claude only')).toBeInTheDocument();
  });

  it('shows P/R/F1 when gold entities provided', () => {
    const gold = [
      { text: 'Senate', type: 'government_org', start: 0, end: 6 },
      { text: 'John Smith', type: 'person', start: 50, end: 60 },
    ];
    render(<ComparisonMetrics spacyResult={spacyResult} claudeResult={claudeResult} goldEntities={gold} />);
    expect(screen.getByText(/against gold annotations/)).toBeInTheDocument();
  });
});
