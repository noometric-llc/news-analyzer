import { NextRequest, NextResponse } from 'next/server';
import { readFile } from 'fs/promises';
import path from 'path';

const VALID_BRANCHES = ['judicial', 'executive', 'legislative', 'conll'];

const ENTITY_TYPES = [
  'person',
  'government_org',
  'organization',
  'location',
  'event',
  'concept',
  'legislation',
] as const;

/**
 * Extract per-entity-type TP/FP/FN from Promptfoo namedScores.
 *
 * The namedScores object has fields like `person_tp`, `government_org_fp`, etc.
 * We reshape these into a structured record keyed by entity type.
 */
function extractEntityTypeMetrics(namedScores: Record<string, number>) {
  const byEntityType: Record<string, { tp: number; fp: number; fn: number }> = {};

  for (const entityType of ENTITY_TYPES) {
    byEntityType[entityType] = {
      tp: namedScores[`${entityType}_tp`] ?? 0,
      fp: namedScores[`${entityType}_fp`] ?? 0,
      fn: namedScores[`${entityType}_fn`] ?? 0,
    };
  }

  return byEntityType;
}

/**
 * GET /api/eval/results/[branch]
 *
 * Returns per-entity-type breakdown for a specific branch.
 * Extracts namedScores from each provider in the Promptfoo results file
 * and reshapes into a clean API response.
 *
 * Branch param is validated against an allowlist to prevent path traversal.
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ branch: string }> }
) {
  const { branch } = await params;

  // Security: validate branch against allowlist
  if (!VALID_BRANCHES.includes(branch)) {
    return NextResponse.json({ error: 'Invalid branch' }, { status: 400 });
  }

  try {
    const baseDir = process.env.EVAL_REPORTS_PATH
      || path.join(process.cwd(), '..', 'eval', 'reports', 'baseline');
    const filePath = path.join(baseDir, `${branch}_results.json`);
    const raw = await readFile(filePath, 'utf-8');
    const data = JSON.parse(raw);

    if (!data?.results?.prompts) {
      return NextResponse.json(
        { error: `Malformed results file for branch: ${branch}` },
        { status: 500 }
      );
    }

    // Extract namedScores from each provider's metrics
    const extractors: Record<string, {
      overall: {
        precision: number;
        recall: number;
        f1: number;
        true_positives: number;
        false_positives: number;
        false_negatives: number;
      };
      byEntityType: Record<string, { tp: number; fp: number; fn: number }>;
    }> = {};

    for (const prompt of data.results.prompts) {
      const provider = prompt.provider;
      const namedScores = prompt.metrics.namedScores;

      extractors[provider] = {
        overall: {
          // Use capitalized versions — these are the actual 0–1 metrics
          precision: namedScores['Precision'] ?? 0,
          recall: namedScores['Recall'] ?? 0,
          f1: namedScores['F1'] ?? 0,
          true_positives: namedScores['true_positives'] ?? 0,
          false_positives: namedScores['false_positives'] ?? 0,
          false_negatives: namedScores['false_negatives'] ?? 0,
        },
        byEntityType: extractEntityTypeMetrics(namedScores),
      };
    }

    return NextResponse.json({ branch, extractors });
  } catch (error) {
    return NextResponse.json(
      { error: `Branch results not found: ${branch}` },
      { status: 404 }
    );
  }
}
