'use client';

import { cn } from '@/lib/utils';
import type { SampleArticle } from '@/types/evaluation';

/**
 * Sample articles with gold entity annotations from eval/datasets/gold/*.yaml.
 * Truncated to first 2–3 paragraphs for fast extraction and low API cost.
 */
export const SAMPLE_ARTICLES: SampleArticle[] = [
  {
    label: 'Judicial — Senate Confirmation',
    branch: 'judicial',
    text: `WASHINGTON, June 6, 2018 - The Senate confirmed Annemarie Carney Axon to a federal position today, marking another successful appointment for President Donald J. Trump's administration.

Axon, a Republican, received confirmation following the standard Senate review process. Her appointment represents part of the ongoing effort by the Trump administration to fill key federal positions across government agencies.

The confirmation process, which concluded on Wednesday, followed established procedures for presidential appointments requiring Senate approval. Axon's Republican party affiliation aligns with the administration's broader strategy of selecting nominees who share similar policy perspectives.`,
    goldEntities: [
      { text: 'WASHINGTON', type: 'location', start: 0, end: 10 },
      { text: 'Senate', type: 'government_org', start: 33, end: 39 },
      { text: 'Annemarie Carney Axon', type: 'person', start: 50, end: 71 },
      { text: 'Donald J. Trump', type: 'person', start: 154, end: 169 },
      { text: 'Republican', type: 'concept', start: 197, end: 207 },
    ],
  },
  {
    label: 'Legislative — Senator Profile',
    branch: 'legislative',
    text: `JACKSON, Miss., March 15, 2026 — Mississippi Senator Roger F. Wicker began his new Senate term today, marking another chapter in his continued service representing the state in the upper chamber of Congress.

Wicker, who has been a fixture in Mississippi politics, officially commenced his latest term as the Senate convened for the new congressional session. The Republican lawmaker will continue to serve the people of Mississippi in his role as a member of the Senate.

"I'm honored to continue serving the great state of Mississippi in the United States Senate," Wicker said in a statement marking the start of his term.`,
    goldEntities: [
      { text: 'JACKSON, Miss.', type: 'location', start: 0, end: 14 },
      { text: 'Roger F. Wicker', type: 'person', start: 55, end: 70 },
      { text: 'Senate', type: 'government_org', start: 85, end: 91 },
      { text: 'Congress', type: 'government_org', start: 200, end: 208 },
    ],
  },
  {
    label: 'General — CoNLL News',
    branch: 'conll',
    text: 'Peter Blackburn BRUSSELS 1996-08-22 The European Commission said on Thursday it disagreed with German advice to consumers to shun British lamb.',
    goldEntities: [
      { text: 'Peter Blackburn', type: 'person', start: 0, end: 15 },
      { text: 'BRUSSELS', type: 'location', start: 16, end: 24 },
      { text: 'European Commission', type: 'organization', start: 40, end: 59 },
      { text: 'German', type: 'concept', start: 95, end: 101 },
      { text: 'British', type: 'concept', start: 130, end: 137 },
    ],
  },
];

interface SampleArticleSelectorProps {
  onSelect: (article: SampleArticle) => void;
  selectedLabel?: string;
}

/**
 * SampleArticleSelector — Button row for pre-loaded sample articles.
 * Clicking a sample populates the text input with the article text.
 */
export function SampleArticleSelector({ onSelect, selectedLabel }: SampleArticleSelectorProps) {
  return (
    <div>
      <p className="text-sm font-medium text-muted-foreground mb-2">Try a sample article:</p>
      <div className="flex flex-wrap gap-2">
        {SAMPLE_ARTICLES.map((article) => (
          <button
            key={article.label}
            onClick={() => onSelect(article)}
            className={cn(
              'px-3 py-2 rounded-md text-sm font-medium transition-colors',
              'border hover:border-primary hover:text-primary',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              selectedLabel === article.label
                ? 'border-primary text-primary bg-primary/5'
                : 'border-border text-muted-foreground'
            )}
          >
            <span className="inline-block px-1.5 py-0.5 bg-muted rounded text-xs mr-2 capitalize">
              {article.branch}
            </span>
            {article.label.split(' — ')[1]}
          </button>
        ))}
      </div>
    </div>
  );
}
