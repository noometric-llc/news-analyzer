#!/usr/bin/env node
/**
 * Transform raw Promptfoo results into the API format the frontend expects.
 *
 * Reads: eval/reports/baseline/{branch}_results.json (raw Promptfoo output)
 * Writes: eval/reports/baseline/{branch}_api.json   (BranchDetailResult shape)
 *
 * Run from repo root: node eval/reports/build-api-json.js
 */

const fs = require('fs');
const path = require('path');

const BRANCHES = ['judicial', 'executive', 'legislative', 'conll'];
const ENTITY_TYPES = [
  'person', 'government_org', 'organization',
  'location', 'event', 'concept', 'legislation',
];

const baselineDir = path.join(__dirname, 'baseline');

for (const branch of BRANCHES) {
  const inputPath = path.join(baselineDir, `${branch}_results.json`);
  const outputPath = path.join(baselineDir, `${branch}_api.json`);

  const raw = JSON.parse(fs.readFileSync(inputPath, 'utf-8'));

  if (!raw?.results?.prompts) {
    console.error(`Skipping ${branch}: malformed results file`);
    continue;
  }

  const extractors = {};

  for (const prompt of raw.results.prompts) {
    const provider = prompt.provider;
    const namedScores = prompt.metrics.namedScores;

    const byEntityType = {};
    for (const entityType of ENTITY_TYPES) {
      byEntityType[entityType] = {
        tp: namedScores[`${entityType}_tp`] ?? 0,
        fp: namedScores[`${entityType}_fp`] ?? 0,
        fn: namedScores[`${entityType}_fn`] ?? 0,
      };
    }

    extractors[provider] = {
      overall: {
        precision: namedScores['Precision'] ?? 0,
        recall: namedScores['Recall'] ?? 0,
        f1: namedScores['F1'] ?? 0,
        true_positives: namedScores['true_positives'] ?? 0,
        false_positives: namedScores['false_positives'] ?? 0,
        false_negatives: namedScores['false_negatives'] ?? 0,
      },
      byEntityType,
    };
  }

  const result = { branch, extractors };
  fs.writeFileSync(outputPath, JSON.stringify(result, null, 2));
  console.log(`Written: ${outputPath}`);
}
