'use client';

import type { ExtractedEntity, ExtractionResult, GoldEntity } from '@/types/evaluation';
import { computeEntityDiff } from './ExtractionResults';

/**
 * Simple exact-match scorer for P/R/F1 against gold annotations.
 * Case-insensitive text + entity_type match. NOT the full fuzzy matcher.
 */
function computeMetricsAgainstGold(
  extracted: ExtractedEntity[],
  gold: GoldEntity[]
): { precision: number; recall: number; f1: number } {
  const normalize = (text: string, type: string) => `${text.toLowerCase()}|${type}`;

  const extractedSet = new Set(extracted.map((e) => normalize(e.text, e.entity_type)));
  const goldSet = new Set(gold.map((g) => normalize(g.text, g.type)));

  let tp = 0;
  for (const key of extractedSet) {
    if (goldSet.has(key)) tp++;
  }

  const precision = extractedSet.size > 0 ? tp / extractedSet.size : 0;
  const recall = goldSet.size > 0 ? tp / goldSet.size : 0;
  const f1 = precision + recall > 0 ? (2 * precision * recall) / (precision + recall) : 0;

  return { precision, recall, f1 };
}

interface ComparisonMetricsProps {
  spacyResult: ExtractionResult | null;
  claudeResult: ExtractionResult | null;
  goldEntities?: GoldEntity[];
}

function MetricBadge({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="text-center px-4">
      <p className="text-lg font-bold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  );
}

/**
 * ComparisonMetrics — Summary bar with entity counts, overlap, and optional P/R/F1 against gold.
 *
 * Shows counts and diff stats when both extractors have results.
 * If gold annotations are available (sample articles), computes P/R/F1 for each extractor.
 */
export function ComparisonMetrics({ spacyResult, claudeResult, goldEntities }: ComparisonMetricsProps) {
  if (!spacyResult || !claudeResult) return null;

  const diff = computeEntityDiff(spacyResult.entities, claudeResult.entities);

  const spacyMetrics = goldEntities ? computeMetricsAgainstGold(spacyResult.entities, goldEntities) : null;
  const claudeMetrics = goldEntities ? computeMetricsAgainstGold(claudeResult.entities, goldEntities) : null;

  return (
    <div className="border rounded-lg p-4 space-y-4">
      {/* Count summary */}
      <div className="flex flex-wrap items-center justify-center gap-6 divide-x">
        <MetricBadge label="spaCy entities" value={spacyResult.total_count} />
        <MetricBadge label="Claude entities" value={claudeResult.total_count} />
        <MetricBadge label="Overlap" value={diff.both.length} />
        <MetricBadge label="spaCy only" value={diff.spacyOnly.length} />
        <MetricBadge label="Claude only" value={diff.claudeOnly.length} />
      </div>

      {/* Gold-based P/R/F1 (only for sample articles) */}
      {spacyMetrics && claudeMetrics && goldEntities && (
        <div className="border-t pt-4">
          <p className="text-xs text-muted-foreground text-center mb-3">
            P/R/F1 against gold annotations ({goldEntities.length} gold entities, simple exact-match)
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="text-center border rounded-md p-3">
              <p className="text-xs text-muted-foreground mb-2">spaCy</p>
              <div className="flex justify-center gap-4">
                <span className="text-sm">P: <strong>{spacyMetrics.precision.toFixed(2)}</strong></span>
                <span className="text-sm">R: <strong>{spacyMetrics.recall.toFixed(2)}</strong></span>
                <span className="text-sm">F1: <strong>{spacyMetrics.f1.toFixed(2)}</strong></span>
              </div>
            </div>
            <div className="text-center border rounded-md p-3 border-primary/30">
              <p className="text-xs text-muted-foreground mb-2">Claude</p>
              <div className="flex justify-center gap-4">
                <span className="text-sm">P: <strong>{claudeMetrics.precision.toFixed(2)}</strong></span>
                <span className="text-sm">R: <strong>{claudeMetrics.recall.toFixed(2)}</strong></span>
                <span className="text-sm">F1: <strong>{claudeMetrics.f1.toFixed(2)}</strong></span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
