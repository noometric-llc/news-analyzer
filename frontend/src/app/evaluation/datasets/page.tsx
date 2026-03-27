'use client';

import { useState, useCallback } from 'react';
import { useDatasetStats, useDatasetArticles } from '@/hooks/useEvaluation';
import {
  DatasetStatsOverview,
  ArticleFilters,
  ArticleBrowserTable,
} from '@/components/evaluation';
import type { ArticleQueryParams } from '@/types/evaluation';

/**
 * Gold Dataset Explorer page.
 *
 * Fetches dataset stats and paginated articles from the backend API.
 * Provides filters for branch, perturbation type, difficulty, and faithful status.
 * Articles expand to show full text, entity badges, and perturbation details.
 *
 * Note: Requires the backend service to be running (proxied via Next.js API routes).
 */
export default function DatasetsPage() {
  // Filter state
  const [branch, setBranch] = useState('');
  const [perturbationType, setPerturbationType] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [faithful, setFaithful] = useState('all');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  // Build query params from filter state
  const queryParams: ArticleQueryParams = {
    page,
    size: pageSize,
    ...(branch && { branch }),
    ...(perturbationType && { perturbationType }),
    ...(difficulty && { difficulty }),
    ...(faithful !== 'all' && { isFaithful: faithful === 'true' }),
  };

  // Fetch data
  const { data: stats, isLoading: statsLoading, error: statsError } = useDatasetStats();
  const { data: articles, isLoading: articlesLoading, error: articlesError } = useDatasetArticles(queryParams);

  // Reset filters to defaults
  const resetFilters = useCallback(() => {
    setBranch('');
    setPerturbationType('');
    setDifficulty('');
    setFaithful('all');
    setPage(0);
  }, []);

  // Reset page to 0 when filters change
  const handleFilterChange = useCallback(<T,>(setter: (v: T) => void) => {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }, []);

  // Perturbation type options from stats data
  const perturbationOptions = stats
    ? Object.keys(stats.byPerturbationType)
    : [];

  const hasAnyError = statsError || articlesError;

  if (statsError && articlesError) {
    return (
      <div className="container py-8">
        <h1 className="text-3xl font-bold mb-2">Gold Dataset Explorer</h1>
        <div className="border border-destructive rounded-lg p-8 text-center">
          <p className="text-destructive font-medium">Dataset API unavailable</p>
          <p className="text-sm text-muted-foreground mt-2">
            Ensure the backend service is running at the configured BACKEND_URL.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-8">
      <h1 className="text-3xl font-bold mb-2">Gold Dataset Explorer</h1>
      <p className="text-muted-foreground mb-8 max-w-2xl">
        Browse the evaluation gold dataset — synthetic articles with ground-truth
        entity annotations, perturbation labels, and difficulty ratings.
      </p>

      <div className="space-y-8">
        {/* Partial error banner */}
        {hasAnyError && !(statsError && articlesError) && (
          <div className="border border-amber-300 bg-amber-50 rounded-lg px-4 py-3 text-sm text-amber-800">
            Some data could not be loaded — the backend service may be partially unavailable.
          </div>
        )}

        {/* Stats overview */}
        {statsLoading ? (
          <div className="animate-pulse space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="h-24 bg-muted rounded-lg" />
              <div className="h-24 bg-muted rounded-lg" />
              <div className="h-24 bg-muted rounded-lg" />
            </div>
            <div className="h-64 bg-muted rounded-lg" />
          </div>
        ) : stats ? (
          <DatasetStatsOverview stats={stats} />
        ) : null}

        {/* Filters */}
        <ArticleFilters
          branch={branch}
          perturbationType={perturbationType}
          difficulty={difficulty}
          faithful={faithful}
          perturbationOptions={perturbationOptions}
          onBranchChange={handleFilterChange(setBranch)}
          onPerturbationTypeChange={handleFilterChange(setPerturbationType)}
          onDifficultyChange={handleFilterChange(setDifficulty)}
          onFaithfulChange={handleFilterChange(setFaithful)}
          onReset={resetFilters}
        />

        {/* Article table */}
        <ArticleBrowserTable
          articles={articles?.content ?? []}
          totalElements={articles?.totalElements ?? 0}
          totalPages={articles?.totalPages ?? 0}
          currentPage={page}
          pageSize={pageSize}
          onPageChange={setPage}
          onPageSizeChange={(size) => {
            setPageSize(size);
            setPage(0);
          }}
          isLoading={articlesLoading}
          onReset={resetFilters}
        />
      </div>
    </div>
  );
}
