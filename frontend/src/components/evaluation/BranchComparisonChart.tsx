'use client';

import { useState } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { cn } from '@/lib/utils';
import { EXTRACTOR_NAMES, type EvalSummary } from '@/types/evaluation';

type MetricKey = 'f1' | 'precision' | 'recall';

const METRIC_OPTIONS: { value: MetricKey; label: string }[] = [
  { value: 'f1', label: 'F1 Score' },
  { value: 'precision', label: 'Precision' },
  { value: 'recall', label: 'Recall' },
];

const BRANCH_LABELS: Record<string, string> = {
  legislative: 'Legislative',
  executive: 'Executive',
  judicial: 'Judicial',
  conll: 'CoNLL',
};

// Claude gets the primary blue, spaCy gets a neutral gray
const CLAUDE_COLOR = '#3b82f6';
const SPACY_COLOR = '#9ca3af';

interface BranchComparisonChartProps {
  data: EvalSummary;
  /** If set, show only this branch (single pair of bars). Omit for all branches. */
  selectedBranch?: string;
}

/**
 * BranchComparisonChart — Grouped bar chart comparing extractors by branch.
 *
 * Shows side-by-side bars for Claude vs spaCy, with a metric toggle (F1/P/R).
 * Uses recharts BarChart wrapped in ResponsiveContainer for responsive sizing.
 */
export function BranchComparisonChart({ data, selectedBranch }: BranchComparisonChartProps) {
  const [metric, setMetric] = useState<MetricKey>('f1');

  // Build chart data: one entry per branch
  const chartData = Object.entries(data.branches)
    .filter(([branchName]) => !selectedBranch || selectedBranch === branchName)
    .map(([branchName, extractors]) => {
      const claude = extractors[EXTRACTOR_NAMES.claude];
      const spacy = extractors[EXTRACTOR_NAMES.spacy];

      return {
        branch: BRANCH_LABELS[branchName] ?? branchName,
        claude: claude ? Number(claude[metric].toFixed(3)) : 0,
        spacy: spacy ? Number(spacy[metric].toFixed(3)) : 0,
      };
    });

  return (
    <div>
      {/* Metric toggle */}
      <div className="flex gap-1 mb-4">
        {METRIC_OPTIONS.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => setMetric(value)}
            className={cn(
              'px-3 py-1.5 rounded text-xs font-medium transition-colors',
              metric === value
                ? 'bg-foreground text-background'
                : 'bg-muted text-muted-foreground hover:bg-muted/80'
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Chart */}
      <ResponsiveContainer width="100%" height={400}>
        <BarChart data={chartData} barGap={4} barCategoryGap="20%">
          <CartesianGrid strokeDasharray="3 3" className="opacity-30" />
          <XAxis dataKey="branch" tick={{ fontSize: 13 }} />
          <YAxis domain={[0, 1]} tick={{ fontSize: 12 }} tickFormatter={(v) => v.toFixed(1)} />
          <Tooltip
            formatter={(value: number, name: string) => [
              value.toFixed(3),
              name === 'claude' ? EXTRACTOR_NAMES.claude : EXTRACTOR_NAMES.spacy,
            ]}
            labelFormatter={(label) => `Branch: ${label}`}
          />
          <Legend
            formatter={(value) =>
              value === 'claude' ? EXTRACTOR_NAMES.claude : EXTRACTOR_NAMES.spacy
            }
          />
          <Bar dataKey="claude" fill={CLAUDE_COLOR} radius={[4, 4, 0, 0]} />
          <Bar dataKey="spacy" fill={SPACY_COLOR} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
