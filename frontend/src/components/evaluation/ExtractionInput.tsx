'use client';

import { useRef } from 'react';

interface ExtractionInputProps {
  text: string;
  onTextChange: (text: string) => void;
  onSubmit: () => void;
  onClear: () => void;
  isLoading: boolean;
  hasResults: boolean;
}

/**
 * ExtractionInput — Textarea for article text with submit and clear buttons.
 * Includes cost disclaimer and character count.
 */
export function ExtractionInput({
  text,
  onTextChange,
  onSubmit,
  onClear,
  isLoading,
  hasResults,
}: ExtractionInputProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleTryAnother = () => {
    onClear();
    textareaRef.current?.focus();
  };

  return (
    <div className="space-y-3">
      <textarea
        ref={textareaRef}
        value={text}
        onChange={(e) => onTextChange(e.target.value)}
        placeholder="Paste a news article or select a sample above..."
        className="w-full min-h-[200px] px-4 py-3 border rounded-lg text-sm bg-background resize-y focus:outline-none focus:ring-2 focus:ring-ring"
      />

      <div className="flex flex-wrap items-center gap-3">
        <button
          onClick={onSubmit}
          disabled={!text.trim() || isLoading}
          className="px-6 py-2.5 bg-primary text-primary-foreground rounded-lg font-semibold text-sm hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {isLoading ? 'Extracting...' : 'Extract & Compare'}
        </button>
        {text && (
          <button
            onClick={onClear}
            className="px-4 py-2.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            Clear
          </button>
        )}
        {hasResults && (
          <button
            onClick={handleTryAnother}
            className="px-4 py-2.5 text-sm text-primary hover:text-primary/80 font-medium transition-colors"
          >
            Try Another
          </button>
        )}
        <span className="text-xs text-muted-foreground ml-auto">
          {text.length} characters
        </span>
      </div>

      {/* Cost disclaimer */}
      <p className="text-xs text-muted-foreground">
        Note: Claude extraction uses API credits. Each extraction costs approximately $0.004.
      </p>
    </div>
  );
}
