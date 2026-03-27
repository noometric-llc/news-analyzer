import { LIMITATIONS, FUTURE_WORK } from '@/lib/data/methodology';

/**
 * LimitationsFutureWork — Honest limitations + forward-looking future work.
 */
export function LimitationsFutureWork() {
  return (
    <section>
      <h2 className="text-2xl font-bold mb-2">Limitations &amp; Future Work</h2>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mt-6">
        {/* Limitations */}
        <div>
          <h3 className="text-lg font-semibold mb-3">Known Limitations</h3>
          <ol className="space-y-3">
            {LIMITATIONS.map((limitation, i) => (
              <li key={i} className="flex gap-3 text-sm text-muted-foreground">
                <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-muted text-xs font-bold shrink-0">
                  {i + 1}
                </span>
                <span>{limitation}</span>
              </li>
            ))}
          </ol>
        </div>

        {/* Future work */}
        <div>
          <h3 className="text-lg font-semibold mb-3">Future Work</h3>
          <div className="space-y-3">
            {FUTURE_WORK.map((item) => (
              <div key={item.title} className="text-sm">
                <p className="font-medium">{item.title}</p>
                <p className="text-muted-foreground">{item.description}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
