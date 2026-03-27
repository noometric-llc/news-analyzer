import type { Metadata } from 'next';
import {
  Overview,
  EntityTaxonomy,
  DatasetPipeline,
  EvalMetrics,
  FuzzyMatching,
  ResultsSummary,
  LimitationsFutureWork,
  ToolsBadges,
} from '@/components/evaluation/methodology';

/**
 * Open Graph metadata for social sharing.
 * This page is the portfolio-ready URL to share with recruiters.
 */
export const metadata: Metadata = {
  title: 'Entity Extraction Evaluation Methodology | NewsAnalyzer',
  description:
    'Systematic evaluation comparing Claude vs spaCy entity extraction on U.S. government domain text. Claude achieves 2× F1 improvement.',
  openGraph: {
    title: 'Entity Extraction Evaluation Methodology',
    description:
      'Claude vs spaCy NER evaluation with precision/recall/F1 metrics on 113 government news articles.',
  },
};

/**
 * Evaluation Methodology page — portfolio-ready case study.
 *
 * Presents the full evaluation methodology as a designed, readable page:
 * entity taxonomy, dataset construction, metrics, fuzzy matching, results, and tools.
 *
 * This is a Server Component (no hooks) — all section components use static data constants.
 */
export default function MethodologyPage() {
  return (
    <div className="container py-8 max-w-5xl">
      {/* Page title */}
      <h1 className="text-3xl font-bold mb-2">Evaluation Methodology</h1>
      <p className="text-muted-foreground mb-12 max-w-3xl">
        A systematic evaluation of named entity extraction quality on U.S. government domain text,
        comparing a Claude LLM extractor against a spaCy statistical NER baseline.
      </p>

      {/* 8 content sections with generous spacing */}
      <div className="space-y-16">
        <Overview />
        <EntityTaxonomy />
        <DatasetPipeline />
        <EvalMetrics />
        <FuzzyMatching />
        <ResultsSummary />
        <LimitationsFutureWork />
        <ToolsBadges />
      </div>
    </div>
  );
}
