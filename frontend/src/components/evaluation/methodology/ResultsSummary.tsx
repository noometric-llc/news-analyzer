import { Fragment } from 'react';
import { cn } from '@/lib/utils';
import { AGGREGATE_RESULTS, PER_ENTITY_TYPE_RESULTS, COST_COMPARISON } from '@/lib/data/methodology';

/**
 * ResultsSummary — Aggregate results, per-entity-type table, key findings, and cost comparison.
 */
export function ResultsSummary() {
  return (
    <section>
      <h2 className="text-2xl font-bold mb-2">Results</h2>
      <p className="text-muted-foreground mb-6 max-w-3xl">
        Both extractors receive identical input (same article text) and are scored by the same scorer
        against the same gold annotations. This controlled comparison isolates extraction quality.
      </p>

      {/* Aggregate results table */}
      <h3 className="text-lg font-semibold mb-3">Aggregate Comparison</h3>
      <div className="overflow-x-auto mb-8">
        <table className="text-sm w-full">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-semibold">Dataset</th>
              <th className="text-left py-2 px-3 font-semibold">Extractor</th>
              <th className="text-center py-2 px-3 font-semibold">Precision</th>
              <th className="text-center py-2 px-3 font-semibold">Recall</th>
              <th className="text-center py-2 px-3 font-semibold">F1</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {AGGREGATE_RESULTS.map((row) => {
              const claudeWins = row.claude.f1 > row.spacy.f1;
              return (
                <Fragment key={row.dataset}>
                  <tr>
                    <td className="py-2 px-3 font-medium" rowSpan={2}>
                      {row.dataset} ({row.count})
                    </td>
                    <td className="py-2 px-3 text-muted-foreground">spaCy</td>
                    <td className="py-2 px-3 text-center">{row.spacy.p.toFixed(3)}</td>
                    <td className="py-2 px-3 text-center">{row.spacy.r.toFixed(3)}</td>
                    <td className={cn('py-2 px-3 text-center', !claudeWins && 'font-bold')}>
                      {row.spacy.f1.toFixed(3)}
                    </td>
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-primary">Claude</td>
                    <td className="py-2 px-3 text-center">{row.claude.p.toFixed(3)}</td>
                    <td className="py-2 px-3 text-center">{row.claude.r.toFixed(3)}</td>
                    <td className={cn('py-2 px-3 text-center', claudeWins && 'font-bold')}>
                      {row.claude.f1.toFixed(3)}
                    </td>
                  </tr>
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Per-entity-type table */}
      <h3 className="text-lg font-semibold mb-2">Per-Entity-Type Performance</h3>
      <p className="text-xs text-muted-foreground mb-3">Legislative branch — largest dataset (53 articles)</p>
      <div className="overflow-x-auto mb-8">
        <table className="text-sm w-full">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-semibold" rowSpan={2}>Entity Type</th>
              <th className="text-center py-2 px-3 font-semibold border-l" colSpan={3}>spaCy</th>
              <th className="text-center py-2 px-3 font-semibold border-l" colSpan={3}>Claude</th>
            </tr>
            <tr className="border-b text-muted-foreground text-xs">
              <th className="py-1 px-3 text-center border-l">P</th>
              <th className="py-1 px-3 text-center">R</th>
              <th className="py-1 px-3 text-center">F1</th>
              <th className="py-1 px-3 text-center border-l">P</th>
              <th className="py-1 px-3 text-center">R</th>
              <th className="py-1 px-3 text-center">F1</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {PER_ENTITY_TYPE_RESULTS.map((row) => (
              <tr key={row.type}>
                <td className="py-2 px-3 font-medium capitalize">{row.type.replace('_', ' ')}</td>
                <td className="py-2 px-3 text-center border-l">{row.spacy.p.toFixed(2)}</td>
                <td className="py-2 px-3 text-center">{row.spacy.r.toFixed(2)}</td>
                <td className="py-2 px-3 text-center">{row.spacy.f1.toFixed(2)}</td>
                <td className={cn('py-2 px-3 text-center border-l', row.claude.f1 > row.spacy.f1 && 'font-bold text-primary')}>
                  {row.claude.p.toFixed(2)}
                </td>
                <td className="py-2 px-3 text-center">{row.claude.r.toFixed(2)}</td>
                <td className={cn('py-2 px-3 text-center', row.claude.f1 > row.spacy.f1 && 'font-bold text-primary')}>
                  {row.claude.f1.toFixed(2)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Key findings */}
      <h3 className="text-lg font-semibold mb-3">Performance Patterns</h3>
      <div className="grid grid-cols-1 gap-3 mb-8 max-w-3xl">
        <div className="border border-l-4 border-l-blue-500 rounded-lg p-4">
          <h4 className="font-semibold text-sm mb-1">spaCy&apos;s weakness is precision, not recall</h4>
          <p className="text-xs text-muted-foreground">
            The small en_core_web_sm model finds most entities (recall &gt; 0.90) but generates massive false positives — 1,620 FPs on 53 legislative articles.
          </p>
        </div>
        <div className="border border-l-4 border-l-green-500 rounded-lg p-4">
          <h4 className="font-semibold text-sm mb-1">Claude&apos;s advantage is disciplined extraction</h4>
          <p className="text-xs text-muted-foreground">
            Similar recall but 4× fewer false positives (399 vs 1,620 on legislative). This drives the F1 improvement from 0.261 to 0.593 — a 2.3× gain.
          </p>
        </div>
        <div className="border border-l-4 border-l-amber-500 rounded-lg p-4">
          <h4 className="font-semibold text-sm mb-1">spaCy excels on CoNLL-2003</h4>
          <p className="text-xs text-muted-foreground">
            On general newswire (the domain spaCy was trained on), it achieves 0.905 F1 with near-perfect precision (0.960). The government article weakness is a domain gap issue.
          </p>
        </div>
      </div>

      {/* Cost/quality */}
      <h3 className="text-lg font-semibold mb-3">Cost / Quality Tradeoff</h3>
      <div className="overflow-x-auto">
        <table className="text-sm">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-semibold">Metric</th>
              <th className="text-center py-2 px-3 font-semibold">spaCy</th>
              <th className="text-center py-2 px-3 font-semibold">Claude (Sonnet)</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            <tr>
              <td className="py-2 px-3">Cost per article</td>
              <td className="py-2 px-3 text-center">{COST_COMPARISON.spacy.costPerArticle}</td>
              <td className="py-2 px-3 text-center">{COST_COMPARISON.claude.costPerArticle}</td>
            </tr>
            <tr>
              <td className="py-2 px-3">Cost for 50 articles</td>
              <td className="py-2 px-3 text-center">$0.00</td>
              <td className="py-2 px-3 text-center">{COST_COMPARISON.costFor50Articles}</td>
            </tr>
            <tr>
              <td className="py-2 px-3">Avg F1 (government)</td>
              <td className="py-2 px-3 text-center">{COST_COMPARISON.spacy.avgGovF1}</td>
              <td className="py-2 px-3 text-center font-bold text-primary">{COST_COMPARISON.claude.avgGovF1}</td>
            </tr>
            <tr>
              <td className="py-2 px-3">F1 improvement</td>
              <td className="py-2 px-3 text-center">—</td>
              <td className="py-2 px-3 text-center text-primary font-semibold">{COST_COMPARISON.claude.f1Improvement}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}
