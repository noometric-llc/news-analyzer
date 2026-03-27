import type { BranchResult } from '@/types/evaluation';

/**
 * Compute weighted F1 score across branches for a given extractor.
 *
 * Weights each branch's F1 by its article count so branches with more
 * evaluation data have proportionally more influence on the overall score.
 *
 * Formula: Σ(branch_f1 × branch_article_count) / Σ(branch_article_count)
 *
 * @param branches - The branches record from EvalSummary
 * @param extractor - Extractor name (e.g., "Claude Sonnet", "spaCy en_core_web_sm")
 * @param branchFilter - Optional list of branch names to include.
 *   Pass ['judicial','executive','legislative'] for gov-domain only.
 *   Omit or pass undefined for all branches.
 * @returns Weighted F1 score (0–1), or 0 if no matching data
 */
export function computeWeightedF1(
  branches: Record<string, Record<string, BranchResult>>,
  extractor: string,
  branchFilter?: string[]
): number {
  let weightedSum = 0;
  let totalArticles = 0;

  for (const [branchName, extractors] of Object.entries(branches)) {
    if (branchFilter && !branchFilter.includes(branchName)) continue;

    const result = extractors[extractor];
    if (!result) continue;

    weightedSum += result.f1 * result.article_count;
    totalArticles += result.article_count;
  }

  if (totalArticles === 0) return 0;
  return weightedSum / totalArticles;
}

/**
 * Compute weighted precision or recall across branches for a given extractor.
 * Same weighting strategy as computeWeightedF1.
 */
export function computeWeightedMetric(
  branches: Record<string, Record<string, BranchResult>>,
  extractor: string,
  metric: 'precision' | 'recall',
  branchFilter?: string[]
): number {
  let weightedSum = 0;
  let totalArticles = 0;

  for (const [branchName, extractors] of Object.entries(branches)) {
    if (branchFilter && !branchFilter.includes(branchName)) continue;

    const result = extractors[extractor];
    if (!result) continue;

    weightedSum += result[metric] * result.article_count;
    totalArticles += result.article_count;
  }

  if (totalArticles === 0) return 0;
  return weightedSum / totalArticles;
}

// --- Entity type color map (shared across EVAL-DASH.3, .4, .5) ---

export const ENTITY_TYPE_COLORS: Record<string, string> = {
  person: '#3b82f6',         // blue-500
  government_org: '#a855f7', // purple-500
  organization: '#14b8a6',   // teal-500
  location: '#22c55e',       // green-500
  event: '#f97316',          // orange-500
  concept: '#ec4899',        // pink-500
  legislation: '#f59e0b',    // amber-500
};

/**
 * Tailwind background class for entity type badges.
 * Uses bg- variants for inline badge styling.
 */
export const ENTITY_TYPE_BG_CLASSES: Record<string, string> = {
  person: 'bg-blue-100 text-blue-800',
  government_org: 'bg-purple-100 text-purple-800',
  organization: 'bg-teal-100 text-teal-800',
  location: 'bg-green-100 text-green-800',
  event: 'bg-orange-100 text-orange-800',
  concept: 'bg-pink-100 text-pink-800',
  legislation: 'bg-amber-100 text-amber-800',
};
