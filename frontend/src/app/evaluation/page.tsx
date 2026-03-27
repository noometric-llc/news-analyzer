'use client';

import Link from 'next/link';
import { BarChart3, Database, BookOpen } from 'lucide-react';
import { useEvalSummary } from '@/hooks/useEvaluation';
import { computeWeightedF1 } from '@/lib/utils/evaluation';
import { EXTRACTOR_NAMES } from '@/types/evaluation';

const GOV_BRANCHES = ['judicial', 'executive', 'legislative'];

/**
 * Quick-stat card component for the evaluation landing page.
 * Displays a single metric with label and value.
 */
function StatCard({ label, value, detail }: { label: string; value: string; detail?: string }) {
  return (
    <div className="border rounded-lg p-6 text-center">
      <p className="text-sm text-muted-foreground mb-1">{label}</p>
      <p className="text-3xl font-bold">{value}</p>
      {detail && <p className="text-xs text-muted-foreground mt-1">{detail}</p>}
    </div>
  );
}

/**
 * Skeleton for stat cards while data loads.
 */
function StatCardSkeleton() {
  return (
    <div className="border rounded-lg p-6 text-center animate-pulse">
      <div className="h-4 bg-muted rounded w-24 mx-auto mb-2" />
      <div className="h-9 bg-muted rounded w-16 mx-auto mb-1" />
      <div className="h-3 bg-muted rounded w-32 mx-auto" />
    </div>
  );
}

/**
 * Navigation card linking to a sub-page within the evaluation section.
 */
function NavCard({
  href,
  icon: Icon,
  title,
  description,
}: {
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
}) {
  return (
    <Link
      href={href}
      className="border rounded-lg p-6 hover:border-primary hover:shadow-md transition-all group"
    >
      <div className="flex items-center gap-3 mb-2">
        <Icon className="h-5 w-5 text-muted-foreground group-hover:text-primary transition-colors" />
        <h3 className="font-semibold group-hover:text-primary transition-colors">{title}</h3>
      </div>
      <p className="text-sm text-muted-foreground">{description}</p>
    </Link>
  );
}

/**
 * AI Evaluation landing page.
 * Displays section overview, quick-stat cards (loaded dynamically), and navigation to sub-pages.
 */
export default function EvaluationPage() {
  const { data: summary, isLoading } = useEvalSummary();

  // Compute dynamic stats from summary data
  let totalArticles = '—';
  let extractorCount = '—';
  let bestGovF1 = '—';

  if (summary) {
    // Total articles: sum article_count across all branches (use first extractor's count)
    let articles = 0;
    for (const extractors of Object.values(summary.branches)) {
      const first = Object.values(extractors)[0];
      if (first) articles += first.article_count;
    }
    totalArticles = articles.toString();

    // Extractor count: number of unique extractor names
    const extractorNames = new Set<string>();
    for (const extractors of Object.values(summary.branches)) {
      for (const name of Object.keys(extractors)) {
        extractorNames.add(name);
      }
    }
    extractorCount = extractorNames.size.toString();

    // Best gov-domain F1: weighted F1 for Claude across gov branches
    const claudeGovF1 = computeWeightedF1(summary.branches, EXTRACTOR_NAMES.claude, GOV_BRANCHES);
    bestGovF1 = claudeGovF1.toFixed(2);
  }

  return (
    <div className="container py-8">
      {/* Section heading */}
      <h1 className="text-3xl font-bold mb-2">AI Evaluation</h1>
      <p className="text-muted-foreground mb-8 max-w-2xl">
        This section showcases a systematic evaluation of entity extraction quality
        comparing Claude (LLM) against spaCy (statistical NLP) across U.S. government
        domain text. Explore the results, browse the gold dataset, and read the full
        methodology.
      </p>

      {/* Quick-stat cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-12">
        {isLoading ? (
          <>
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </>
        ) : (
          <>
            <StatCard
              label="Articles Evaluated"
              value={totalArticles}
              detail="Legislative, Executive, Judicial + CoNLL"
            />
            <StatCard
              label="Extractors Compared"
              value={extractorCount}
              detail="spaCy vs Claude Sonnet"
            />
            <StatCard
              label="Claude F1 (Gov Domain)"
              value={bestGovF1}
              detail="Weighted across gov branches"
            />
          </>
        )}
      </div>

      {/* Navigation cards */}
      <h2 className="text-xl font-semibold mb-4">Explore</h2>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <NavCard
          href="/evaluation/results"
          icon={BarChart3}
          title="Model Comparison Results"
          description="Visual comparison of spaCy vs Claude with P/R/F1 charts, per-branch breakdown, and per-entity-type analysis."
        />
        <NavCard
          href="/evaluation/datasets"
          icon={Database}
          title="Gold Dataset Explorer"
          description="Browse the 113 evaluation articles with ground-truth entity annotations, perturbation labels, and difficulty ratings."
        />
        <NavCard
          href="/evaluation/methodology"
          icon={BookOpen}
          title="Evaluation Methodology"
          description="Entity taxonomy, gold dataset construction, fuzzy matching strategy, and the full evaluation design."
        />
      </div>
    </div>
  );
}
