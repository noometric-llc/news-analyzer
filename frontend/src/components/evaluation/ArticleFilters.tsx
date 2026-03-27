'use client';

interface ArticleFiltersProps {
  branch: string;
  perturbationType: string;
  difficulty: string;
  faithful: string; // 'all' | 'true' | 'false'
  perturbationOptions: string[];
  onBranchChange: (value: string) => void;
  onPerturbationTypeChange: (value: string) => void;
  onDifficultyChange: (value: string) => void;
  onFaithfulChange: (value: string) => void;
  onReset: () => void;
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}) {
  const id = `filter-${label.toLowerCase().replace(/\s+/g, '-')}`;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-muted-foreground">{label}</label>
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="px-3 py-2 border rounded-md text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring"
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
}

/**
 * ArticleFilters — Filter controls for the article browser table.
 *
 * Provides select dropdowns for branch, perturbation type, difficulty, and faithful status.
 * Perturbation type options are dynamic (from stats data).
 * All filter state is managed by the parent page.
 */
export function ArticleFilters({
  branch,
  perturbationType,
  difficulty,
  faithful,
  perturbationOptions,
  onBranchChange,
  onPerturbationTypeChange,
  onDifficultyChange,
  onFaithfulChange,
  onReset,
}: ArticleFiltersProps) {
  const hasActiveFilters = branch !== '' || perturbationType !== '' || difficulty !== '' || faithful !== 'all';

  return (
    <div className="flex flex-wrap items-end gap-3">
      <FilterSelect
        label="Branch"
        value={branch}
        onChange={onBranchChange}
        options={[
          { value: '', label: 'All Branches' },
          { value: 'legislative', label: 'Legislative' },
          { value: 'executive', label: 'Executive' },
          { value: 'judicial', label: 'Judicial' },
        ]}
      />
      <FilterSelect
        label="Perturbation"
        value={perturbationType}
        onChange={onPerturbationTypeChange}
        options={[
          { value: '', label: 'All Types' },
          ...perturbationOptions.map((pt) => ({ value: pt, label: pt })),
        ]}
      />
      <FilterSelect
        label="Difficulty"
        value={difficulty}
        onChange={onDifficultyChange}
        options={[
          { value: '', label: 'All Difficulties' },
          { value: 'EASY', label: 'Easy' },
          { value: 'MEDIUM', label: 'Medium' },
          { value: 'HARD', label: 'Hard' },
        ]}
      />
      <FilterSelect
        label="Faithful"
        value={faithful}
        onChange={onFaithfulChange}
        options={[
          { value: 'all', label: 'All' },
          { value: 'true', label: 'Faithful Only' },
          { value: 'false', label: 'Perturbed Only' },
        ]}
      />
      {hasActiveFilters && (
        <button
          onClick={onReset}
          className="px-3 py-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          Reset filters
        </button>
      )}
    </div>
  );
}
