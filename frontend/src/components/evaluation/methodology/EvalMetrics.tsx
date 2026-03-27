/**
 * EvalMetrics — Visual display of P/R/F1 formulas with explanations.
 */

function Formula({ label, numerator, denominator, explanation }: {
  label: string;
  numerator: string;
  denominator: string;
  explanation: string;
}) {
  return (
    <div className="border rounded-lg p-5 flex-1 min-w-[200px]">
      <div className="flex items-center gap-3 mb-3">
        <span className="font-semibold">{label} =</span>
        <div className="inline-flex flex-col items-center">
          <span className="border-b border-foreground px-3 pb-0.5 font-mono text-sm">{numerator}</span>
          <span className="px-3 pt-0.5 font-mono text-sm">{denominator}</span>
        </div>
      </div>
      <p className="text-sm text-muted-foreground">{explanation}</p>
    </div>
  );
}

export function EvalMetrics() {
  return (
    <section>
      <h2 className="text-2xl font-bold mb-2">Evaluation Metrics</h2>
      <p className="text-muted-foreground mb-6 max-w-3xl">
        Standard information retrieval metrics for named entity recognition. Each extracted entity is classified
        as a true positive, false positive, or false negative relative to the gold annotations.
      </p>

      {/* Formulas */}
      <div className="flex flex-col md:flex-row gap-4 mb-6">
        <Formula
          label="Precision"
          numerator="TP"
          denominator="TP + FP"
          explanation="Of entities extracted, what fraction are correct?"
        />
        <Formula
          label="Recall"
          numerator="TP"
          denominator="TP + FN"
          explanation="Of gold entities, what fraction were found?"
        />
        <Formula
          label="F1"
          numerator="2 × P × R"
          denominator="P + R"
          explanation="Harmonic mean balancing precision and recall."
        />
      </div>

      {/* Legend */}
      <div className="border rounded-md bg-muted/30 px-4 py-3 max-w-2xl">
        <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
          <span><strong className="text-green-600">TP</strong> <span className="text-muted-foreground">= True Positive (correctly extracted)</span></span>
          <span><strong className="text-red-500">FP</strong> <span className="text-muted-foreground">= False Positive (hallucinated)</span></span>
          <span><strong className="text-amber-500">FN</strong> <span className="text-muted-foreground">= False Negative (missed)</span></span>
        </div>
      </div>
    </section>
  );
}
