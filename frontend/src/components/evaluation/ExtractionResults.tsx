'use client';

import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { ExtractedEntity, ExtractionResult } from '@/types/evaluation';
import { EXTRACTOR_NAMES } from '@/types/evaluation';
import { ENTITY_TYPE_BG_CLASSES } from '@/lib/utils/evaluation';

/**
 * Compute which entities are unique to each extractor.
 * Match on text (case-insensitive) + entity_type.
 */
export function computeEntityDiff(
  spacyEntities: ExtractedEntity[],
  claudeEntities: ExtractedEntity[]
) {
  const normalize = (e: ExtractedEntity) => `${e.text.toLowerCase()}|${e.entity_type}`;

  const spacySet = new Set(spacyEntities.map(normalize));
  const claudeSet = new Set(claudeEntities.map(normalize));

  return {
    both: spacyEntities.filter((e) => claudeSet.has(normalize(e))),
    spacyOnly: spacyEntities.filter((e) => !claudeSet.has(normalize(e))),
    claudeOnly: claudeEntities.filter((e) => !spacySet.has(normalize(e))),
  };
}

interface EntityRowProps {
  entity: ExtractedEntity;
  diffStatus?: 'both' | 'unique';
  uniqueColor?: string;
}

function EntityRow({ entity, diffStatus, uniqueColor }: EntityRowProps) {
  const bgClass = ENTITY_TYPE_BG_CLASSES[entity.entity_type] || 'bg-muted text-muted-foreground';

  return (
    <div
      className={cn(
        'flex items-center gap-2 px-3 py-1.5 rounded text-sm',
        diffStatus === 'unique' && uniqueColor
      )}
    >
      <span className="font-medium flex-1 min-w-0 truncate">{entity.text}</span>
      <span className={cn('px-2 py-0.5 rounded text-xs font-medium shrink-0', bgClass)}>
        {entity.entity_type}
      </span>
      <span className="text-xs text-muted-foreground shrink-0 tabular-nums">
        {entity.confidence.toFixed(2)}
      </span>
      <span className="text-xs text-muted-foreground/60 shrink-0 tabular-nums">
        {entity.start}–{entity.end}
      </span>
    </div>
  );
}

interface ColumnProps {
  title: string;
  result: ExtractionResult | null;
  isLoading: boolean;
  error: string | null;
  latency: number | null;
  diffSpacy?: Set<string>;
  diffClaude?: Set<string>;
  side: 'spacy' | 'claude';
}

function ResultColumn({ title, result, isLoading, error, latency, diffSpacy, diffClaude, side }: ColumnProps) {
  const normalize = (e: ExtractedEntity) => `${e.text.toLowerCase()}|${e.entity_type}`;
  const otherSet = side === 'spacy' ? diffClaude : diffSpacy;

  return (
    <div className="border rounded-lg overflow-hidden">
      {/* Header */}
      <div className="px-4 py-3 bg-muted/30 border-b flex items-center gap-2">
        <h3 className="font-semibold text-sm">{title}</h3>
        {result && (
          <span className="px-2 py-0.5 bg-primary/10 text-primary rounded-full text-xs font-medium">
            {result.total_count} entities
          </span>
        )}
        {latency !== null && (
          <span className="text-xs text-muted-foreground ml-auto">
            {(latency / 1000).toFixed(1)}s
          </span>
        )}
      </div>

      {/* Content */}
      <div className="p-3 min-h-[200px]">
        {isLoading && (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Extracting...</span>
          </div>
        )}

        {error && (
          <div className="text-sm text-destructive p-3 border border-destructive/20 rounded bg-destructive/5">
            {error}
          </div>
        )}

        {!isLoading && !error && result && (
          <div className="space-y-1">
            {result.entities.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-8">No entities extracted</p>
            ) : (
              result.entities.map((entity, i) => {
                const key = normalize(entity);
                const isUnique = otherSet ? !otherSet.has(key) : false;
                return (
                  <EntityRow
                    key={`${key}-${i}`}
                    entity={entity}
                    diffStatus={otherSet ? (isUnique ? 'unique' : 'both') : undefined}
                    uniqueColor={isUnique ? (side === 'spacy' ? 'bg-amber-50' : 'bg-blue-50') : undefined}
                  />
                );
              })
            )}
          </div>
        )}

        {!isLoading && !error && !result && (
          <p className="text-sm text-muted-foreground text-center py-8">
            Click &quot;Extract &amp; Compare&quot; to see results
          </p>
        )}
      </div>
    </div>
  );
}

interface ExtractionResultsProps {
  spacyResult: ExtractionResult | null;
  claudeResult: ExtractionResult | null;
  spacyLoading: boolean;
  claudeLoading: boolean;
  spacyError: string | null;
  claudeError: string | null;
  spacyLatency: number | null;
  claudeLatency: number | null;
}

/**
 * ExtractionResults — Side-by-side extraction results with diff highlighting.
 *
 * Each column loads independently — spaCy results appear as soon as ready,
 * without waiting for Claude. Diff highlighting activates when both are loaded.
 */
export function ExtractionResults({
  spacyResult,
  claudeResult,
  spacyLoading,
  claudeLoading,
  spacyError,
  claudeError,
  spacyLatency,
  claudeLatency,
}: ExtractionResultsProps) {
  const normalize = (e: ExtractedEntity) => `${e.text.toLowerCase()}|${e.entity_type}`;

  // Compute diff sets for highlighting (only when both results loaded)
  const spacySet = spacyResult ? new Set(spacyResult.entities.map(normalize)) : undefined;
  const claudeSet = claudeResult ? new Set(claudeResult.entities.map(normalize)) : undefined;

  const hasAnyResult = spacyResult || claudeResult || spacyLoading || claudeLoading;
  if (!hasAnyResult) return null;

  return (
    <div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ResultColumn
          title={EXTRACTOR_NAMES.spacy}
          result={spacyResult}
          isLoading={spacyLoading}
          error={spacyError}
          latency={spacyLatency}
          diffSpacy={spacySet}
          diffClaude={claudeSet}
          side="spacy"
        />
        <ResultColumn
          title={EXTRACTOR_NAMES.claude}
          result={claudeResult}
          isLoading={claudeLoading}
          error={claudeError}
          latency={claudeLatency}
          diffSpacy={spacySet}
          diffClaude={claudeSet}
          side="claude"
        />
      </div>

      {/* Diff legend (shown when both results loaded) */}
      {spacyResult && claudeResult && (
        <div className="flex flex-wrap gap-4 mt-3 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded bg-background border" /> Found by both
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded bg-amber-50 border border-amber-200" /> spaCy only
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded bg-blue-50 border border-blue-200" /> Claude only
          </span>
        </div>
      )}
    </div>
  );
}
