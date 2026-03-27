'use client';

import { Fragment, useState } from 'react';
import { ChevronDown, ChevronRight, Check, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { SyntheticArticle } from '@/types/evaluation';
import { ArticleDetailView } from './ArticleDetailView';

const DIFFICULTY_STYLES: Record<string, string> = {
  EASY: 'bg-green-100 text-green-800',
  MEDIUM: 'bg-amber-100 text-amber-800',
  HARD: 'bg-red-100 text-red-800',
};

interface ArticleBrowserTableProps {
  articles: SyntheticArticle[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  isLoading?: boolean;
  onReset?: () => void;
}

/**
 * ArticleBrowserTable — Paginated table of synthetic articles with expandable detail rows.
 *
 * Displays excerpt, branch, article type, perturbation badge, difficulty badge, and faithful status.
 * Clicking a row expands the ArticleDetailView below it.
 */
export function ArticleBrowserTable({
  articles,
  totalElements,
  totalPages,
  currentPage,
  pageSize,
  onPageChange,
  onPageSizeChange,
  isLoading,
  onReset,
}: ArticleBrowserTableProps) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-12 bg-muted rounded" />
        ))}
      </div>
    );
  }

  if (articles.length === 0) {
    return (
      <div className="border border-dashed rounded-lg p-12 text-center">
        <p className="text-muted-foreground mb-2">No articles match your filters</p>
        {onReset && (
          <button
            onClick={onReset}
            className="text-sm text-primary hover:underline"
          >
            Reset filters
          </button>
        )}
      </div>
    );
  }

  return (
    <div>
      {/* Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="w-8 py-3 px-2" />
              <th className="text-left py-3 px-2 font-semibold">Excerpt</th>
              <th className="text-left py-3 px-2 font-semibold">Branch</th>
              <th className="text-left py-3 px-2 font-semibold">Type</th>
              <th className="text-left py-3 px-2 font-semibold">Perturbation</th>
              <th className="text-left py-3 px-2 font-semibold">Difficulty</th>
              <th className="text-center py-3 px-2 font-semibold">Faithful</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {articles.map((article) => {
              const isExpanded = expandedId === article.id;
              const excerpt = article.articleText.slice(0, 100).replace(/\*\*/g, '') + (article.articleText.length > 100 ? '...' : '');
              const branch = (article.sourceFacts as Record<string, unknown>)?.branch as string || '—';

              return (
                <Fragment key={article.id}>
                  <tr
                    className={cn(
                      'cursor-pointer hover:bg-muted/50 transition-colors',
                      isExpanded && 'bg-muted/30'
                    )}
                    onClick={() => setExpandedId(isExpanded ? null : article.id)}
                  >
                    <td className="py-2.5 px-2 text-muted-foreground">
                      {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                    </td>
                    <td className="py-2.5 px-2 max-w-xs">
                      <span className="text-xs leading-snug line-clamp-2">{excerpt}</span>
                    </td>
                    <td className="py-2.5 px-2">
                      <span className="text-xs capitalize">{branch}</span>
                    </td>
                    <td className="py-2.5 px-2">
                      <span className="text-xs">{article.articleType}</span>
                    </td>
                    <td className="py-2.5 px-2">
                      <span className={cn(
                        'inline-block px-2 py-0.5 rounded-full text-xs font-medium',
                        article.isFaithful
                          ? 'bg-green-100 text-green-800'
                          : 'bg-amber-100 text-amber-800'
                      )}>
                        {article.isFaithful ? 'faithful' : article.perturbationType || 'perturbed'}
                      </span>
                    </td>
                    <td className="py-2.5 px-2">
                      <span className={cn(
                        'inline-block px-2 py-0.5 rounded-full text-xs font-medium',
                        DIFFICULTY_STYLES[article.difficulty] || 'bg-muted'
                      )}>
                        {article.difficulty?.toLowerCase() || '—'}
                      </span>
                    </td>
                    <td className="py-2.5 px-2 text-center">
                      {article.isFaithful
                        ? <Check className="h-4 w-4 text-green-600 mx-auto" />
                        : <X className="h-4 w-4 text-red-500 mx-auto" />
                      }
                    </td>
                  </tr>
                  {isExpanded && (
                    <tr>
                      <td colSpan={7} className="p-0">
                        <div className="border-t border-border bg-muted/10 p-4">
                          <ArticleDetailView article={article} />
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between mt-4 pt-4 border-t border-border">
        <div className="text-sm text-muted-foreground">
          {totalElements} articles total
        </div>
        <div className="flex items-center gap-4">
          <select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="px-2 py-1 border rounded text-sm bg-background"
          >
            <option value={10}>10 / page</option>
            <option value={20}>20 / page</option>
            <option value={50}>50 / page</option>
          </select>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onPageChange(currentPage - 1)}
              disabled={currentPage === 0}
              className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted transition-colors"
            >
              Prev
            </button>
            <span className="text-sm text-muted-foreground">
              Page {currentPage + 1} of {totalPages || 1}
            </span>
            <button
              onClick={() => onPageChange(currentPage + 1)}
              disabled={currentPage >= totalPages - 1}
              className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted transition-colors"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
