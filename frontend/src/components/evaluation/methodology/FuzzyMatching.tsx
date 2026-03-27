import { FUZZY_MATCHING_PRIORITIES, FUZZY_MOTIVATING_EXAMPLES } from '@/lib/data/methodology';

/**
 * FuzzyMatching — Why fuzzy matching, motivating examples, and 6-priority table.
 */
export function FuzzyMatching() {
  return (
    <section>
      <h2 className="text-2xl font-bold mb-2">Fuzzy Matching Strategy</h2>
      <p className="text-muted-foreground mb-6 max-w-3xl">
        Strict exact-match evaluation penalizes extractors for reasonable boundary differences.
        Our scorer uses a 6-priority fuzzy matching system that awards full or partial credit
        for semantically correct extractions with minor text or type variations.
      </p>

      {/* Motivating examples */}
      <h3 className="text-lg font-semibold mb-3">Why Fuzzy Matching?</h3>
      <div className="overflow-x-auto mb-8">
        <table className="text-sm w-full max-w-2xl">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-semibold">Extracted</th>
              <th className="text-left py-2 px-3 font-semibold">Gold</th>
              <th className="text-center py-2 px-3 font-semibold">Strict</th>
              <th className="text-center py-2 px-3 font-semibold">Fuzzy</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {FUZZY_MOTIVATING_EXAMPLES.map((ex) => (
              <tr key={ex.extracted}>
                <td className="py-2 px-3 font-mono text-xs">{ex.extracted}</td>
                <td className="py-2 px-3 font-mono text-xs">{ex.gold}</td>
                <td className="py-2 px-3 text-center text-red-500 text-xs">{ex.strict}</td>
                <td className="py-2 px-3 text-center text-green-600 text-xs">{ex.fuzzy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Priority table */}
      <h3 className="text-lg font-semibold mb-3">6-Priority Matching System</h3>
      <div className="overflow-x-auto">
        <table className="text-sm w-full">
          <thead>
            <tr className="border-b">
              <th className="text-center py-2 px-3 font-semibold w-16">Priority</th>
              <th className="text-left py-2 px-3 font-semibold">Rule</th>
              <th className="text-center py-2 px-3 font-semibold">Credit</th>
              <th className="text-left py-2 px-3 font-semibold">Example</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {FUZZY_MATCHING_PRIORITIES.map((p) => {
              // Visual grouping: 1-2 exact, 3-4 substring, 5-6 fuzzy
              const groupColor =
                p.priority <= 2 ? 'bg-blue-50' :
                p.priority <= 4 ? 'bg-purple-50' :
                'bg-amber-50';

              return (
                <tr key={p.priority} className={groupColor}>
                  <td className="py-2.5 px-3 text-center">
                    <span className="inline-block w-6 h-6 rounded-full bg-foreground text-background text-xs font-bold leading-6">
                      {p.priority}
                    </span>
                  </td>
                  <td className="py-2.5 px-3">{p.rule}</td>
                  <td className="py-2.5 px-3 text-center font-mono text-xs">{p.credit}</td>
                  <td className="py-2.5 px-3 text-xs text-muted-foreground">
                    <span className="font-mono">{p.example.extracted}</span>
                    {' → '}
                    <span className="font-mono">{p.example.gold}</span>
                    <span className="text-muted-foreground/70"> ({p.example.note})</span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
