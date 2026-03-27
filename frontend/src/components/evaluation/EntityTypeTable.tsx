'use client';

import { ENTITY_TYPE_NAMES, EXTRACTOR_NAMES, type BranchDetailResult, type EntityTypeName } from '@/types/evaluation';

const ENTITY_TYPE_LABELS: Record<EntityTypeName, string> = {
  person: 'Person',
  government_org: 'Government Org',
  organization: 'Organization',
  location: 'Location',
  event: 'Event',
  concept: 'Concept',
  legislation: 'Legislation',
};

interface EntityTypeTableProps {
  /** Array of branch detail results. For "All" view, pass all 4 branches to be summed. */
  branchDetails: BranchDetailResult[];
  /** Whether data is still loading */
  isLoading?: boolean;
}

/**
 * Sum entity-type metrics across multiple branches.
 * Returns aggregated TP/FP/FN per entity type per extractor.
 */
function aggregateEntityMetrics(branchDetails: BranchDetailResult[]) {
  // Collect all extractor names from the data
  const extractorNames = new Set<string>();
  for (const detail of branchDetails) {
    for (const name of Object.keys(detail.extractors)) {
      extractorNames.add(name);
    }
  }

  const result: Record<string, Record<EntityTypeName, { tp: number; fp: number; fn: number }>> = {};

  for (const extractor of extractorNames) {
    result[extractor] = {} as Record<EntityTypeName, { tp: number; fp: number; fn: number }>;

    for (const entityType of ENTITY_TYPE_NAMES) {
      let tp = 0;
      let fp = 0;
      let fn = 0;

      for (const detail of branchDetails) {
        const extractorData = detail.extractors[extractor];
        if (!extractorData) continue;

        const metrics = extractorData.byEntityType[entityType];
        if (!metrics) continue;

        tp += metrics.tp;
        fp += metrics.fp;
        fn += metrics.fn;
      }

      result[extractor][entityType] = { tp, fp, fn };
    }
  }

  return result;
}

/**
 * Format a potentially fractional TP/FP/FN value.
 * Shows one decimal place for fractional values, integer for whole numbers.
 */
function formatCount(value: number): string {
  return Number.isInteger(value) ? value.toString() : value.toFixed(1);
}

/**
 * EntityTypeTable — Per-entity-type TP/FP/FN breakdown for each extractor.
 *
 * Shows 7 entity type rows with side-by-side spaCy and Claude metrics.
 * When multiple branch details are provided (for "All" view), sums across branches.
 */
export function EntityTypeTable({ branchDetails, isLoading }: EntityTypeTableProps) {
  if (isLoading) {
    return (
      <div className="animate-pulse space-y-2">
        {Array.from({ length: 7 }).map((_, i) => (
          <div key={i} className="h-10 bg-muted rounded" />
        ))}
      </div>
    );
  }

  if (branchDetails.length === 0) {
    return (
      <div className="text-center text-muted-foreground py-8">
        No entity type data available.
      </div>
    );
  }

  const aggregated = aggregateEntityMetrics(branchDetails);
  const spacy = aggregated[EXTRACTOR_NAMES.spacy];
  const claude = aggregated[EXTRACTOR_NAMES.claude];

  if (!spacy && !claude) {
    return (
      <div className="text-center text-muted-foreground py-8">
        No extractor data available.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            <th className="text-left py-3 px-2 font-semibold" rowSpan={2}>Entity Type</th>
            <th className="text-center py-2 px-2 font-semibold border-l border-border" colSpan={3}>
              spaCy en_core_web_sm
            </th>
            <th className="text-center py-2 px-2 font-semibold border-l border-border" colSpan={3}>
              Claude Sonnet
            </th>
          </tr>
          <tr className="border-b border-border text-muted-foreground">
            <th className="text-center py-2 px-2 text-xs border-l border-border">TP</th>
            <th className="text-center py-2 px-2 text-xs">FP</th>
            <th className="text-center py-2 px-2 text-xs">FN</th>
            <th className="text-center py-2 px-2 text-xs border-l border-border">TP</th>
            <th className="text-center py-2 px-2 text-xs">FP</th>
            <th className="text-center py-2 px-2 text-xs">FN</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {ENTITY_TYPE_NAMES.map((entityType) => {
            const s = spacy?.[entityType] ?? { tp: 0, fp: 0, fn: 0 };
            const c = claude?.[entityType] ?? { tp: 0, fp: 0, fn: 0 };

            return (
              <tr key={entityType} className="hover:bg-muted/50">
                <td className="py-2.5 px-2 font-medium">{ENTITY_TYPE_LABELS[entityType]}</td>
                <td className="text-center py-2.5 px-2 border-l border-border text-green-600">{formatCount(s.tp)}</td>
                <td className="text-center py-2.5 px-2 text-red-500">{formatCount(s.fp)}</td>
                <td className="text-center py-2.5 px-2 text-amber-500">{formatCount(s.fn)}</td>
                <td className="text-center py-2.5 px-2 border-l border-border text-green-600">{formatCount(c.tp)}</td>
                <td className="text-center py-2.5 px-2 text-red-500">{formatCount(c.fp)}</td>
                <td className="text-center py-2.5 px-2 text-amber-500">{formatCount(c.fn)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
