import {
  User, Building, Building2, MapPin, Calendar, Lightbulb, FileText,
} from 'lucide-react';
import type { ComponentType, CSSProperties } from 'react';
import { ENTITY_TAXONOMY } from '@/lib/data/methodology';
import { ENTITY_TYPE_COLORS } from '@/lib/utils/evaluation';

const ICON_MAP: Record<string, ComponentType<{ className?: string; style?: CSSProperties }>> = {
  User, Building, Building2, MapPin, Calendar, Lightbulb, FileText,
};

/**
 * EntityTaxonomy — 7 entity type cards with color accents, icons, and examples.
 */
export function EntityTaxonomy() {
  return (
    <section>
      <h2 className="text-2xl font-bold mb-2">Entity Taxonomy</h2>
      <p className="text-muted-foreground mb-6 max-w-3xl">
        Both extractors produce entities in a shared 7-type taxonomy designed for government domain text.
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {ENTITY_TAXONOMY.map((entry) => {
          const Icon = ICON_MAP[entry.icon];
          const color = ENTITY_TYPE_COLORS[entry.type] || '#9ca3af';

          return (
            <div
              key={entry.type}
              className="border rounded-lg p-4 border-l-4"
              style={{ borderLeftColor: color }}
            >
              <div className="flex items-center gap-2 mb-2">
                {Icon && <Icon className="h-4 w-4 shrink-0" style={{ color }} />}
                <h3 className="font-semibold text-sm">{entry.type}</h3>
              </div>
              <p className="text-xs text-muted-foreground mb-3">{entry.description}</p>
              <div className="flex flex-wrap gap-1">
                {entry.examples.map((ex) => (
                  <span key={ex} className="px-2 py-0.5 bg-muted rounded text-xs">
                    {ex}
                  </span>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
