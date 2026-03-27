'use client';

import { useState } from 'react';
import { useEvalSummary, useBranchDetail } from '@/hooks/useEvaluation';
import {
  BranchFilter,
  HeadlineMetrics,
  BranchComparisonChart,
  EntityTypeTable,
  KeyFindings,
} from '@/components/evaluation';
import type { BranchDetailResult } from '@/types/evaluation';

const ALL_BRANCHES = ['judicial', 'executive', 'legislative', 'conll'];
const GOV_BRANCHES = ['judicial', 'executive', 'legislative'];

/**
 * Loading skeleton matching the results page layout
 */
function ResultsSkeleton() {
  return (
    <div className="space-y-8 animate-pulse">
      <div className="flex gap-1">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-10 w-24 bg-muted rounded-md" />
        ))}
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="h-40 bg-muted rounded-lg" />
        <div className="h-40 bg-muted rounded-lg" />
      </div>
      <div className="h-[400px] bg-muted rounded-lg" />
      <div className="h-64 bg-muted rounded-lg" />
    </div>
  );
}

/**
 * Model Comparison Results page.
 *
 * Fetches summary data and per-branch detail data, wires up the branch filter,
 * and composes all dashboard components.
 *
 * Data flow:
 * - Summary (useEvalSummary) → HeadlineMetrics, BranchComparisonChart
 * - Branch details (useBranchDetail × 4) → EntityTypeTable
 * - Branch filter state → controls which data each component receives
 */
export default function ResultsPage() {
  const [selectedBranch, setSelectedBranch] = useState('all');

  // Fetch summary data (lightweight, used by headline cards and chart)
  const { data: summary, isLoading: summaryLoading, error: summaryError } = useEvalSummary();

  // Fetch all 4 branch details for entity-type table
  // When a specific branch is selected, we still need all 4 loaded for switching
  const judicialDetail = useBranchDetail('judicial');
  const executiveDetail = useBranchDetail('executive');
  const legislativeDetail = useBranchDetail('legislative');
  const conllDetail = useBranchDetail('conll');

  const allDetailsLoading =
    judicialDetail.isLoading || executiveDetail.isLoading ||
    legislativeDetail.isLoading || conllDetail.isLoading;

  // Collect loaded branch details into an array based on current filter
  function getFilteredDetails(): BranchDetailResult[] {
    const allDetails = [
      judicialDetail.data,
      executiveDetail.data,
      legislativeDetail.data,
      conllDetail.data,
    ].filter((d): d is BranchDetailResult => d !== undefined);

    if (selectedBranch === 'all') return allDetails;
    return allDetails.filter((d) => d.branch === selectedBranch);
  }

  // Determine which branches to include in headline metrics
  const headlineBranchFilter = selectedBranch === 'all' ? undefined : [selectedBranch];

  if (summaryError) {
    return (
      <div className="container py-8">
        <h1 className="text-3xl font-bold mb-2">Model Comparison Results</h1>
        <div className="border border-destructive rounded-lg p-8 text-center">
          <p className="text-destructive font-medium">Failed to load evaluation data</p>
          <p className="text-sm text-muted-foreground mt-2">
            Ensure the evaluation result files exist at eval/reports/baseline/
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-8">
      <h1 className="text-3xl font-bold mb-2">Model Comparison Results</h1>
      <p className="text-muted-foreground mb-8 max-w-2xl">
        Visual comparison of spaCy vs Claude entity extraction with precision, recall,
        and F1 metrics — grouped by government branch with per-entity-type breakdowns.
      </p>

      {summaryLoading ? (
        <ResultsSkeleton />
      ) : summary ? (
        <div className="space-y-8">
          {/* Branch filter */}
          <BranchFilter selected={selectedBranch} onChange={setSelectedBranch} />

          {/* Headline F1 cards */}
          <HeadlineMetrics data={summary} branchFilter={headlineBranchFilter} />

          {/* Branch comparison bar chart */}
          <div>
            <h2 className="text-lg font-semibold mb-4">Branch Comparison</h2>
            <BranchComparisonChart
              data={summary}
              selectedBranch={selectedBranch === 'all' ? undefined : selectedBranch}
            />
          </div>

          {/* Entity type breakdown table */}
          <div>
            <h2 className="text-lg font-semibold mb-4">Per-Entity-Type Breakdown</h2>
            <EntityTypeTable
              branchDetails={getFilteredDetails()}
              isLoading={allDetailsLoading}
            />
          </div>

          {/* Key findings */}
          <KeyFindings />
        </div>
      ) : null}
    </div>
  );
}
