'use client';

import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { DatasetStats } from '@/types/evaluation';

const BRANCH_COLORS: Record<string, string> = {
  legislative: '#3b82f6',
  executive: '#a855f7',
  judicial: '#22c55e',
};

const DIFFICULTY_COLORS: Record<string, string> = {
  EASY: '#22c55e',
  MEDIUM: '#f59e0b',
  HARD: '#ef4444',
};

interface DatasetStatsOverviewProps {
  stats: DatasetStats;
}

function StatCard({ label, value, detail }: { label: string; value: string | number; detail?: string }) {
  return (
    <div className="border rounded-lg p-5 text-center">
      <p className="text-sm text-muted-foreground mb-1">{label}</p>
      <p className="text-3xl font-bold">{value}</p>
      {detail && <p className="text-xs text-muted-foreground mt-1">{detail}</p>}
    </div>
  );
}

function BadgeRow({ label, items, colors }: { label: string; items: Record<string, number>; colors?: Record<string, string> }) {
  return (
    <div>
      <p className="text-sm font-medium text-muted-foreground mb-2">{label}</p>
      <div className="flex flex-wrap gap-2">
        {Object.entries(items).map(([name, count]) => (
          <span
            key={name}
            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-muted"
          >
            {colors?.[name] && (
              <span
                className="w-2 h-2 rounded-full shrink-0"
                style={{ backgroundColor: colors[name] }}
              />
            )}
            {name}: {count}
          </span>
        ))}
      </div>
    </div>
  );
}

/**
 * DatasetStatsOverview — Stats cards, branch pie chart, and breakdown badges.
 *
 * Displays total/faithful/perturbed counts, branch distribution as a pie chart,
 * and perturbation type + difficulty breakdowns as badge rows.
 */
export function DatasetStatsOverview({ stats }: DatasetStatsOverviewProps) {
  const faithfulPct = stats.totalArticles > 0
    ? Math.round((stats.faithfulCount / stats.totalArticles) * 100)
    : 0;
  const perturbedPct = stats.totalArticles > 0
    ? Math.round((stats.perturbedCount / stats.totalArticles) * 100)
    : 0;

  const branchData = Object.entries(stats.byBranch).map(([name, value]) => ({ name, value }));

  return (
    <div className="space-y-6">
      {/* Stat cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard label="Total Articles" value={stats.totalArticles} />
        <StatCard label="Faithful" value={stats.faithfulCount} detail={`${faithfulPct}% of dataset`} />
        <StatCard label="Perturbed" value={stats.perturbedCount} detail={`${perturbedPct}% of dataset`} />
      </div>

      {/* Branch distribution + breakdowns */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Pie chart */}
        <div>
          <p className="text-sm font-medium text-muted-foreground mb-2">By Branch</p>
          <ResponsiveContainer width="100%" height={250}>
            <PieChart>
              <Pie
                data={branchData}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                outerRadius={80}
                label={({ name, value }) => `${name} (${value})`}
                labelLine={false}
              >
                {branchData.map((entry) => (
                  <Cell key={entry.name} fill={BRANCH_COLORS[entry.name] || '#9ca3af'} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>

        {/* Breakdowns */}
        <div className="space-y-4">
          <BadgeRow label="By Perturbation Type" items={stats.byPerturbationType} />
          <BadgeRow label="By Difficulty" items={stats.byDifficulty} colors={DIFFICULTY_COLORS} />
        </div>
      </div>
    </div>
  );
}
