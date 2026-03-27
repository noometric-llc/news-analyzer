import { Brain, Target, Scale } from 'lucide-react';
import type { ComponentType } from 'react';

interface Finding {
  icon: ComponentType<{ className?: string }>;
  title: string;
  description: string;
  accentColor: string;
}

const FINDINGS: Finding[] = [
  {
    icon: Brain,
    title: 'Claude excels on government domain text',
    description:
      'Claude F1 ~0.60 vs spaCy ~0.31 across legislative, executive, and judicial branches. Domain context understanding — knowing that "Banking Committee" is a government_org, not a generic organization — drives the 2× improvement.',
    accentColor: 'border-l-blue-500',
  },
  {
    icon: Target,
    title: 'spaCy leads on general-domain text',
    description:
      'spaCy F1 = 0.905 on CoNLL-2003 benchmark vs Claude F1 = 0.867. Statistical NER works well when trained on the target domain — CoNLL is newswire text, exactly what spaCy\'s en_core_web_sm was trained on.',
    accentColor: 'border-l-green-500',
  },
  {
    icon: Scale,
    title: 'Precision vs recall trade-off',
    description:
      'spaCy has high recall (0.86–0.98) but low precision (0.15–0.22) on government text — it finds most entities but generates massive false positives. Claude maintains better precision (0.43–0.46) with similar recall, producing 4× fewer false positives.',
    accentColor: 'border-l-amber-500',
  },
];

/**
 * KeyFindings — 3 insight cards highlighting notable evaluation results.
 *
 * Content is hardcoded (editorial) — derived from actual evaluation results
 * but written as interpretive analysis, not computed from data.
 */
export function KeyFindings() {
  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Key Findings</h3>
      <div className="grid grid-cols-1 gap-4">
        {FINDINGS.map((finding) => {
          const Icon = finding.icon;
          return (
            <div
              key={finding.title}
              className={`border border-l-4 ${finding.accentColor} rounded-lg p-5`}
            >
              <div className="flex items-center gap-2 mb-2">
                <Icon className="h-5 w-5 text-muted-foreground shrink-0" />
                <h4 className="font-semibold">{finding.title}</h4>
              </div>
              <p className="text-sm text-muted-foreground leading-relaxed">
                {finding.description}
              </p>
            </div>
          );
        })}
      </div>
    </div>
  );
}
