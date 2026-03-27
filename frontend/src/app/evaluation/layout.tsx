'use client';

import { Suspense } from 'react';
import { SidebarLayout } from '@/components/layout';
import { useEvaluationSidebarStore } from '@/stores/evaluationSidebarStore';
import { EvaluationSidebar } from '@/components/evaluation';

/**
 * Loading skeleton for AI Evaluation section
 */
function EvaluationSkeleton() {
  return (
    <div className="min-h-screen bg-background animate-pulse">
      {/* Mobile header skeleton */}
      <div className="md:hidden fixed top-0 left-0 right-0 z-40 h-14 bg-card border-b border-border" />
      {/* Content skeleton */}
      <div className="pt-14 md:pt-0 md:ml-64">
        <div className="container py-8">
          <div className="h-8 bg-muted rounded w-48 mb-4" />
          <div className="h-4 bg-muted rounded w-96 mb-8" />
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="h-24 bg-muted rounded" />
            <div className="h-24 bg-muted rounded" />
            <div className="h-24 bg-muted rounded" />
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Main content wrapper for AI Evaluation that uses SidebarLayout
 */
function EvaluationContent({ children }: { children: React.ReactNode }) {
  const store = useEvaluationSidebarStore();

  return (
    <SidebarLayout
      sidebar={<EvaluationSidebar className="h-full" />}
      sectionTitle="AI Evaluation"
      store={store}
    >
      <main className="flex-1">
        {children}
      </main>
    </SidebarLayout>
  );
}

/**
 * Layout for the AI Evaluation section.
 * Provides sidebar navigation for Overview, Results, Dataset Explorer, and Methodology.
 */
export default function EvaluationLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <Suspense fallback={<EvaluationSkeleton />}>
      <EvaluationContent>{children}</EvaluationContent>
    </Suspense>
  );
}
