/**
 * Overview section — opening paragraphs + headline stat callout.
 */
export function Overview() {
  return (
    <section>
      <h2 className="text-2xl font-bold mb-4">Overview</h2>
      <div className="space-y-4 text-muted-foreground leading-relaxed max-w-3xl">
        <p>
          This evaluation measures the quality of named entity extraction on U.S. government
          domain text. Two extractors are compared: a <strong className="text-foreground">spaCy</strong> statistical
          NER pipeline (baseline) and a <strong className="text-foreground">Claude LLM</strong> extractor
          (challenger) — both scored against a curated gold dataset using precision, recall, and F1 metrics.
        </p>
        <p>
          The evaluation uses 113 articles across three government branches (legislative, executive, judicial)
          plus a CoNLL-2003 general-domain validation set. A fuzzy matching strategy with 6 priority levels
          prevents inflating error rates due to reasonable boundary differences between extractors and gold annotations.
        </p>
      </div>

      {/* Headline stat callout */}
      <div className="mt-6 border rounded-lg bg-muted/30 p-6 max-w-2xl">
        <div className="flex flex-wrap items-center justify-center gap-6 text-center">
          <div>
            <p className="text-3xl font-bold text-primary">0.60</p>
            <p className="text-xs text-muted-foreground mt-1">Claude F1 (Gov)</p>
          </div>
          <div className="text-2xl text-muted-foreground/50 font-light">vs</div>
          <div>
            <p className="text-3xl font-bold text-muted-foreground">0.31</p>
            <p className="text-xs text-muted-foreground mt-1">spaCy F1 (Gov)</p>
          </div>
          <div className="w-full text-sm text-muted-foreground pt-2 border-t">
            Claude achieves ~2× spaCy&apos;s F1 on government domain text, while spaCy leads on general-domain (CoNLL F1: 0.905 vs 0.867)
          </div>
        </div>
      </div>
    </section>
  );
}
