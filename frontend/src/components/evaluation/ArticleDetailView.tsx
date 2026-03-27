'use client';

import { useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { SyntheticArticle } from '@/types/evaluation';
import { ENTITY_TYPE_BG_CLASSES, ENTITY_TYPE_COLORS } from '@/lib/utils/evaluation';

interface ArticleDetailViewProps {
  article: SyntheticArticle;
}

interface Fact {
  subject: string;
  predicate: string;
  object: string;
  entity_type?: string;
  [key: string]: unknown;
}

interface ChangedFact {
  predicate: string;
  original_value: string;
  perturbed_value: string;
}

/**
 * Extract unique entities from sourceFacts.facts[] for badge display.
 * Returns deduplicated entries by subject text.
 */
function extractEntities(sourceFacts: Record<string, unknown>): { text: string; type: string }[] {
  const facts = (sourceFacts?.facts as Fact[]) || [];
  const seen = new Set<string>();
  const entities: { text: string; type: string }[] = [];

  for (const fact of facts) {
    if (fact.subject && !seen.has(fact.subject)) {
      seen.add(fact.subject);
      entities.push({
        text: fact.subject,
        type: fact.entity_type || 'unknown',
      });
    }
  }

  return entities;
}

/**
 * Map domain entity types (CongressionalMember, Judge, etc.) to display-friendly labels.
 */
function mapEntityTypeLabel(type: string): string {
  const mapping: Record<string, string> = {
    CongressionalMember: 'person',
    Judge: 'person',
    Presidency: 'person',
    GovernmentOrganization: 'government_org',
  };
  return mapping[type] || type.toLowerCase();
}

/**
 * Collapsible section for optional content.
 */
function CollapsibleSection({ title, children, defaultOpen = false }: { title: string; children: React.ReactNode; defaultOpen?: boolean }) {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  return (
    <div className="border rounded-md">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2 w-full px-3 py-2 text-sm font-medium hover:bg-muted/50 transition-colors"
      >
        {isOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        {title}
      </button>
      {isOpen && (
        <div className="px-3 pb-3 border-t">
          {children}
        </div>
      )}
    </div>
  );
}

/**
 * ArticleDetailView — Expandable detail view for a single synthetic article.
 *
 * Shows full article text, entity badge list (from sourceFacts), metadata,
 * and perturbation details (changed facts + expected findings) for perturbed articles.
 *
 * Note: Entities are extracted from sourceFacts.facts[] (subject + entity_type),
 * NOT from character-offset annotations. Inline highlighting is deferred.
 */
export function ArticleDetailView({ article }: ArticleDetailViewProps) {
  const sourceFacts = article.sourceFacts as Record<string, unknown>;
  const groundTruth = article.groundTruth as Record<string, unknown>;
  const entities = extractEntities(sourceFacts);
  const branch = (sourceFacts?.branch as string) || '—';
  const topic = (sourceFacts?.topic as string) || '—';

  const changedFacts = (groundTruth?.changed_facts as ChangedFact[]) || [];
  const expectedFindings = (groundTruth?.expected_findings as string[]) || [];

  return (
    <div className="space-y-4">
      {/* Two-column layout: article text + metadata */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Article text — takes 2/3 width on large screens */}
        <div className="lg:col-span-2">
          <h4 className="text-sm font-semibold mb-2">Article Text</h4>
          <div className="border rounded-md p-4 bg-background max-h-96 overflow-y-auto">
            <p className="text-sm leading-relaxed whitespace-pre-wrap">
              {article.articleText}
            </p>
          </div>
        </div>

        {/* Metadata panel — takes 1/3 width */}
        <div className="space-y-3">
          <h4 className="text-sm font-semibold">Metadata</h4>
          <div className="text-sm space-y-1.5">
            <div><span className="text-muted-foreground">Topic:</span> {topic}</div>
            <div><span className="text-muted-foreground">Branch:</span> <span className="capitalize">{branch}</span></div>
            <div><span className="text-muted-foreground">Article Type:</span> {article.articleType}</div>
            <div><span className="text-muted-foreground">Difficulty:</span> <span className="capitalize">{article.difficulty?.toLowerCase()}</span></div>
            <div><span className="text-muted-foreground">Model:</span> {article.modelUsed}</div>
            <div>
              <span className="text-muted-foreground">Status:</span>{' '}
              <span className={cn(
                'inline-block px-2 py-0.5 rounded-full text-xs font-medium',
                article.isFaithful ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'
              )}>
                {article.isFaithful ? 'Faithful' : 'Perturbed'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Entity badges */}
      {entities.length > 0 && (
        <div>
          <h4 className="text-sm font-semibold mb-2">Entities (from source facts)</h4>
          {/* Legend */}
          <div className="flex flex-wrap gap-2 mb-2">
            {Object.entries(ENTITY_TYPE_COLORS).map(([type, color]) => (
              <span key={type} className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: color }} />
                {type}
              </span>
            ))}
          </div>
          {/* Badges */}
          <div className="flex flex-wrap gap-1.5">
            {entities.map((entity) => {
              const normalizedType = mapEntityTypeLabel(entity.type);
              const bgClass = ENTITY_TYPE_BG_CLASSES[normalizedType] || 'bg-muted text-muted-foreground';
              return (
                <span
                  key={entity.text}
                  className={cn('inline-block px-2 py-0.5 rounded text-xs font-medium', bgClass)}
                  title={`${entity.text} (${normalizedType})`}
                >
                  {entity.text}
                </span>
              );
            })}
          </div>
        </div>
      )}

      {/* Perturbation details (only for perturbed articles) */}
      {!article.isFaithful && (changedFacts.length > 0 || expectedFindings.length > 0) && (
        <div>
          <h4 className="text-sm font-semibold mb-2">Perturbation Details</h4>
          <div className="space-y-3">
            {/* Changed facts table */}
            {changedFacts.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b">
                      <th className="text-left py-2 px-2 text-xs font-semibold text-muted-foreground">Predicate</th>
                      <th className="text-left py-2 px-2 text-xs font-semibold text-muted-foreground">Original Value</th>
                      <th className="text-left py-2 px-2 text-xs font-semibold text-muted-foreground">Perturbed Value</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {changedFacts.map((cf, i) => (
                      <tr key={i}>
                        <td className="py-1.5 px-2 font-mono text-xs">{cf.predicate}</td>
                        <td className="py-1.5 px-2 text-green-700">{cf.original_value}</td>
                        <td className="py-1.5 px-2 text-red-600">{cf.perturbed_value}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Expected findings */}
            {expectedFindings.length > 0 && (
              <div>
                <p className="text-xs font-medium text-muted-foreground mb-1">Expected Findings:</p>
                <ul className="list-disc list-inside text-xs space-y-0.5 text-muted-foreground">
                  {expectedFindings.map((finding, i) => (
                    <li key={i}>{finding}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Collapsible source facts JSON */}
      <CollapsibleSection title="Source Facts (raw)">
        <pre className="text-xs overflow-x-auto mt-2 p-2 bg-muted rounded">
          {JSON.stringify(sourceFacts?.facts || [], null, 2)}
        </pre>
      </CollapsibleSection>
    </div>
  );
}
