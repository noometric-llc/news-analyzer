'use client';

import { useState, useCallback } from 'react';
import { extractSpacy, extractLLM } from '@/lib/api/evaluation';
import {
  SampleArticleSelector,
  ExtractionInput,
  ExtractionResults,
  ComparisonMetrics,
} from '@/components/evaluation';
import type { ExtractionResult, SampleArticle, GoldEntity } from '@/types/evaluation';

/**
 * Live Extraction Comparison page.
 *
 * Lets visitors paste text or select a sample article, then runs both
 * spaCy and Claude extractors side-by-side with independent loading states.
 *
 * State flow:
 * 1. User enters text (paste or sample button)
 * 2. Click "Extract & Compare" fires both API calls simultaneously
 * 3. spaCy results appear first (~1–3s), Claude follows (~5–15s)
 * 4. Diff highlighting activates when both are loaded
 * 5. ComparisonMetrics shows counts + optional P/R/F1 for samples
 */
export default function ComparePage() {
  // Text input state
  const [text, setText] = useState('');
  const [selectedSample, setSelectedSample] = useState<SampleArticle | null>(null);

  // Extraction state — managed independently per extractor
  const [spacyResult, setSpacyResult] = useState<ExtractionResult | null>(null);
  const [claudeResult, setClaudeResult] = useState<ExtractionResult | null>(null);
  const [spacyLoading, setSpacyLoading] = useState(false);
  const [claudeLoading, setClaudeLoading] = useState(false);
  const [spacyError, setSpacyError] = useState<string | null>(null);
  const [claudeError, setClaudeError] = useState<string | null>(null);
  const [spacyLatency, setSpacyLatency] = useState<number | null>(null);
  const [claudeLatency, setClaudeLatency] = useState<number | null>(null);

  const isLoading = spacyLoading || claudeLoading;
  const hasResults = !!(spacyResult || claudeResult);

  // Gold entities for P/R/F1 scoring (only available for sample articles)
  const goldEntities: GoldEntity[] | undefined = selectedSample?.goldEntities;

  const handleSampleSelect = useCallback((article: SampleArticle) => {
    setText(article.text);
    setSelectedSample(article);
    // Clear previous results
    setSpacyResult(null);
    setClaudeResult(null);
    setSpacyError(null);
    setClaudeError(null);
    setSpacyLatency(null);
    setClaudeLatency(null);
  }, []);

  const handleClear = useCallback(() => {
    setText('');
    setSelectedSample(null);
    setSpacyResult(null);
    setClaudeResult(null);
    setSpacyError(null);
    setClaudeError(null);
    setSpacyLatency(null);
    setClaudeLatency(null);
  }, []);

  const handleSubmit = useCallback(async () => {
    if (!text.trim()) return;

    // Reset results and errors
    setSpacyResult(null);
    setClaudeResult(null);
    setSpacyError(null);
    setClaudeError(null);
    setSpacyLatency(null);
    setClaudeLatency(null);

    // Fire both extractions simultaneously — independent state updates
    setSpacyLoading(true);
    setClaudeLoading(true);

    // spaCy extraction
    const spacyStart = Date.now();
    extractSpacy(text)
      .then((result) => {
        setSpacyResult(result);
        setSpacyLatency(Date.now() - spacyStart);
      })
      .catch((err) => setSpacyError(err.message))
      .finally(() => setSpacyLoading(false));

    // Claude extraction (runs in parallel)
    const claudeStart = Date.now();
    extractLLM(text)
      .then((result) => {
        setClaudeResult(result);
        setClaudeLatency(Date.now() - claudeStart);
      })
      .catch((err) => setClaudeError(err.message))
      .finally(() => setClaudeLoading(false));
  }, [text]);

  return (
    <div className="container py-8">
      <h1 className="text-3xl font-bold mb-2">Live Extraction Comparison</h1>
      <p className="text-muted-foreground mb-8 max-w-2xl">
        See how spaCy and Claude extract entities from the same text. Select a sample article
        or paste your own, then watch both extractors work side-by-side.
      </p>

      <div className="space-y-6">
        {/* Sample article buttons */}
        <SampleArticleSelector
          onSelect={handleSampleSelect}
          selectedLabel={selectedSample?.label}
        />

        {/* Text input + submit */}
        <ExtractionInput
          text={text}
          onTextChange={(t) => {
            setText(t);
            // Clear sample selection when user types custom text
            if (selectedSample) setSelectedSample(null);
          }}
          onSubmit={handleSubmit}
          onClear={handleClear}
          isLoading={isLoading}
          hasResults={hasResults}
        />

        {/* Side-by-side results */}
        <ExtractionResults
          spacyResult={spacyResult}
          claudeResult={claudeResult}
          spacyLoading={spacyLoading}
          claudeLoading={claudeLoading}
          spacyError={spacyError}
          claudeError={claudeError}
          spacyLatency={spacyLatency}
          claudeLatency={claudeLatency}
        />

        {/* Comparison metrics (shown when both results loaded) */}
        <ComparisonMetrics
          spacyResult={spacyResult}
          claudeResult={claudeResult}
          goldEntities={goldEntities}
        />
      </div>
    </div>
  );
}
