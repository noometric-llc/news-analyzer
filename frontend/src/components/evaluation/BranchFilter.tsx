'use client';

import { cn } from '@/lib/utils';

const BRANCHES = [
  { value: 'all', label: 'All' },
  { value: 'legislative', label: 'Legislative' },
  { value: 'executive', label: 'Executive' },
  { value: 'judicial', label: 'Judicial' },
  { value: 'conll', label: 'CoNLL' },
] as const;

interface BranchFilterProps {
  selected: string;
  onChange: (branch: string) => void;
}

/**
 * BranchFilter — Tab-style filter for selecting a government branch.
 *
 * "All" shows aggregated data; individual branches show branch-specific data.
 * State is managed by the parent page via useState.
 */
export function BranchFilter({ selected, onChange }: BranchFilterProps) {
  return (
    <div className="flex flex-wrap gap-1" role="tablist" aria-label="Filter by branch">
      {BRANCHES.map(({ value, label }) => (
        <button
          key={value}
          role="tab"
          aria-selected={selected === value}
          onClick={() => onChange(value)}
          className={cn(
            'px-4 py-2 rounded-md text-sm font-medium transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            selected === value
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted text-muted-foreground hover:bg-muted/80'
          )}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
