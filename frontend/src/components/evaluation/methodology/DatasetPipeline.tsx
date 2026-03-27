import { Database, FileText, Cpu, UserCheck, ArrowRight, ArrowDown } from 'lucide-react';
import type { ComponentType } from 'react';
import { DATASET_STATS } from '@/lib/data/methodology';

interface StageProps {
  icon: ComponentType<{ className?: string }>;
  title: string;
  description: string;
  count: string;
}

function StageCard({ icon: Icon, title, description, count }: StageProps) {
  return (
    <div className="border rounded-lg p-4 flex-1 min-w-[180px] text-center">
      <Icon className="h-6 w-6 mx-auto mb-2 text-primary" />
      <h4 className="font-semibold text-sm mb-1">{title}</h4>
      <p className="text-xs text-muted-foreground mb-2">{description}</p>
      <span className="inline-block px-2 py-0.5 bg-primary/10 text-primary rounded text-xs font-medium">
        {count}
      </span>
    </div>
  );
}

function Arrow() {
  return (
    <>
      <ArrowRight className="h-5 w-5 text-muted-foreground/50 shrink-0 hidden md:block" />
      <ArrowDown className="h-5 w-5 text-muted-foreground/50 shrink-0 md:hidden" />
    </>
  );
}

/**
 * DatasetPipeline — Visual flow diagram of gold dataset construction + stats table.
 */
export function DatasetPipeline() {
  return (
    <section>
      <h2 className="text-2xl font-bold mb-2">Gold Dataset Construction</h2>
      <p className="text-muted-foreground mb-6 max-w-3xl">
        The evaluation gold dataset was built through a 4-stage pipeline: structured facts → synthetic articles → automated entity derivation → human curation.
      </p>

      {/* Pipeline diagram */}
      <div className="flex flex-col md:flex-row items-center gap-3 mb-6">
        <StageCard icon={Database} title="KB Facts" description="Subject/predicate/object tuples from knowledge base" count="~3,400 facts" />
        <Arrow />
        <StageCard icon={FileText} title="Article Generation" description="LLM generates realistic news articles from facts" count="113 articles" />
        <Arrow />
        <StageCard icon={Cpu} title="Automated Derivation" description="Script maps predicates → entity annotations with offsets" count="601 entities" />
        <Arrow />
        <StageCard icon={UserCheck} title="Human Curation" description="Articles manually reviewed, corrected, enriched" count="64 reviewed" />
      </div>

      {/* Auto-enrichment callout */}
      <div className="border rounded-md bg-muted/30 px-4 py-3 text-sm text-muted-foreground mb-6 max-w-2xl">
        <strong className="text-foreground">Auto-enrichment:</strong> {DATASET_STATS.autoEnriched.entities} entities
        automatically added across {DATASET_STATS.autoEnriched.articles} articles (dateline locations, government organizations, multi-word cities).
      </div>

      {/* Dataset stats table */}
      <div className="overflow-x-auto">
        <table className="text-sm">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-semibold">Branch</th>
              <th className="text-center py-2 px-3 font-semibold">Articles</th>
              <th className="text-center py-2 px-3 font-semibold">Curated</th>
              <th className="text-center py-2 px-3 font-semibold">Entities</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {DATASET_STATS.branches.map((b) => (
              <tr key={b.name}>
                <td className="py-2 px-3">{b.name}</td>
                <td className="py-2 px-3 text-center">{b.articles}</td>
                <td className="py-2 px-3 text-center">{b.curated}</td>
                <td className="py-2 px-3 text-center">{b.entities}</td>
              </tr>
            ))}
            <tr className="font-semibold border-t-2">
              <td className="py-2 px-3">Total</td>
              <td className="py-2 px-3 text-center">{DATASET_STATS.totals.articles}</td>
              <td className="py-2 px-3 text-center">{DATASET_STATS.totals.curated}</td>
              <td className="py-2 px-3 text-center">{DATASET_STATS.totals.entities}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}
