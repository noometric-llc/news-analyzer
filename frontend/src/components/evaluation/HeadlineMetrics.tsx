'use client';

import { EXTRACTOR_NAMES, type EvalSummary } from '@/types/evaluation';
import { computeWeightedF1, computeWeightedMetric } from '@/lib/utils/evaluation';

interface HeadlineMetricsProps {
  data: EvalSummary;
  /** Which branches to include. Omit for all branches. */
  branchFilter?: string[];
}

interface MetricCardProps {
  name: string;
  f1: number;
  precision: number;
  recall: number;
  accent: 'primary' | 'muted';
}

function MetricCard({ name, f1, precision, recall, accent }: MetricCardProps) {
  const borderClass = accent === 'primary' ? 'border-primary' : 'border-muted-foreground/30';
  const f1Color = accent === 'primary' ? 'text-primary' : 'text-muted-foreground';

  return (
    <div className={`border-2 ${borderClass} rounded-lg p-6 text-center`}>
      <p className="text-sm font-medium text-muted-foreground mb-2">{name}</p>
      <p className={`text-5xl font-bold ${f1Color} mb-3`}>
        {f1.toFixed(2)}
      </p>
      <p className="text-xs text-muted-foreground">F1 Score</p>
      <div className="flex justify-center gap-6 mt-3 pt-3 border-t border-border">
        <div>
          <p className="text-lg font-semibold">{precision.toFixed(2)}</p>
          <p className="text-xs text-muted-foreground">Precision</p>
        </div>
        <div>
          <p className="text-lg font-semibold">{recall.toFixed(2)}</p>
          <p className="text-xs text-muted-foreground">Recall</p>
        </div>
      </div>
    </div>
  );
}

/**
 * HeadlineMetrics — Two large F1 score cards comparing Claude vs spaCy.
 *
 * Computes weighted F1/P/R across branches using article count as weight.
 * Claude gets the primary accent (it's the challenger); spaCy gets muted (baseline).
 */
export function HeadlineMetrics({ data, branchFilter }: HeadlineMetricsProps) {
  const claudeF1 = computeWeightedF1(data.branches, EXTRACTOR_NAMES.claude, branchFilter);
  const claudeP = computeWeightedMetric(data.branches, EXTRACTOR_NAMES.claude, 'precision', branchFilter);
  const claudeR = computeWeightedMetric(data.branches, EXTRACTOR_NAMES.claude, 'recall', branchFilter);

  const spacyF1 = computeWeightedF1(data.branches, EXTRACTOR_NAMES.spacy, branchFilter);
  const spacyP = computeWeightedMetric(data.branches, EXTRACTOR_NAMES.spacy, 'precision', branchFilter);
  const spacyR = computeWeightedMetric(data.branches, EXTRACTOR_NAMES.spacy, 'recall', branchFilter);

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <MetricCard
        name={EXTRACTOR_NAMES.claude}
        f1={claudeF1}
        precision={claudeP}
        recall={claudeR}
        accent="primary"
      />
      <MetricCard
        name={EXTRACTOR_NAMES.spacy}
        f1={spacyF1}
        precision={spacyP}
        recall={spacyR}
        accent="muted"
      />
    </div>
  );
}
