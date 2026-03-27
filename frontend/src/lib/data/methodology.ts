/**
 * Methodology page data constants.
 *
 * All content adapted from docs/evaluation-methodology.md.
 * Separated from components so presentation logic stays focused on layout.
 */

// --- Entity Taxonomy ---

export const ENTITY_TAXONOMY = [
  { type: 'person', description: 'Named individuals', examples: ['Elizabeth Warren', 'John Roberts'], icon: 'User' },
  { type: 'government_org', description: 'Government bodies, agencies, courts', examples: ['Senate', 'EPA', 'Supreme Court'], icon: 'Building' },
  { type: 'organization', description: 'Non-government organizations', examples: ['Georgetown University', 'AP'], icon: 'Building2' },
  { type: 'location', description: 'Geographic entities', examples: ['Washington', 'Pennsylvania'], icon: 'MapPin' },
  { type: 'event', description: 'Named events', examples: ['Civil War', 'inauguration'], icon: 'Calendar' },
  { type: 'concept', description: 'Political groups, ideologies', examples: ['Republican', 'Democratic'], icon: 'Lightbulb' },
  { type: 'legislation', description: 'Laws, bills, executive orders', examples: ['Affordable Care Act'], icon: 'FileText' },
] as const;

// --- Fuzzy Matching Priorities ---

export const FUZZY_MATCHING_PRIORITIES = [
  { priority: 1, rule: 'Exact text + type match', credit: '1.0 TP', example: { extracted: 'Senate', gold: 'Senate', note: 'both government_org' } },
  { priority: 2, rule: 'Exact text + type mismatch', credit: '0.5 TP', example: { extracted: 'EPA (organization)', gold: 'EPA (government_org)', note: 'type differs' } },
  { priority: 3, rule: 'Substring containment + type match', credit: '1.0 TP', example: { extracted: 'Banking Committee', gold: 'Senate Banking Committee', note: 'substring' } },
  { priority: 4, rule: 'Substring containment + type mismatch', credit: '0.5 TP', example: { extracted: 'Banking Committee (org)', gold: 'Senate Banking Committee (gov_org)', note: 'substring + type differs' } },
  { priority: 5, rule: 'Levenshtein similarity ≥ 0.8 + type match', credit: '1.0 TP', example: { extracted: 'John Fettermann', gold: 'John Fetterman', note: 'fuzzy match' } },
  { priority: 6, rule: 'Levenshtein similarity ≥ 0.8 + type mismatch', credit: '0.5 TP', example: { extracted: 'John Fettermann (person)', gold: 'John Fetterman (concept)', note: 'fuzzy + type differs' } },
] as const;

// --- Motivating Examples for Fuzzy Matching ---

export const FUZZY_MOTIVATING_EXAMPLES = [
  { extracted: 'Banking Committee', gold: 'Senate Banking Committee', strict: 'FP + FN', fuzzy: 'TP (substring)' },
  { extracted: 'John Fettermann', gold: 'John Fetterman', strict: 'FP + FN', fuzzy: 'TP (Levenshtein ≥ 0.8)' },
  { extracted: 'EPA (organization)', gold: 'EPA (government_org)', strict: 'FP + FN', fuzzy: '0.5 TP (type mismatch)' },
] as const;

// --- Dataset Statistics ---

export const DATASET_STATS = {
  branches: [
    { name: 'Legislative', articles: 53, curated: 14, entities: 308 },
    { name: 'Executive', articles: 20, curated: 15, entities: 125 },
    { name: 'Judicial', articles: 15, curated: 10, entities: 81 },
    { name: 'CoNLL-2003', articles: 25, curated: 25, entities: 87 },
  ],
  totals: { articles: 113, curated: 64, entities: 601 },
  autoEnriched: { entities: 190, articles: 88 },
} as const;

// --- Aggregate Results (from evaluation-methodology.md §4) ---

export const AGGREGATE_RESULTS = [
  { dataset: 'Legislative', count: 53, spacy: { p: 0.151, r: 0.963, f1: 0.261 }, claude: { p: 0.426, r: 0.977, f1: 0.593 } },
  { dataset: 'Executive', count: 20, spacy: { p: 0.220, r: 0.983, f1: 0.359 }, claude: { p: 0.432, r: 1.000, f1: 0.603 } },
  { dataset: 'Judicial', count: 15, spacy: { p: 0.192, r: 0.925, f1: 0.318 }, claude: { p: 0.456, r: 0.938, f1: 0.614 } },
  { dataset: 'CoNLL-2003', count: 25, spacy: { p: 0.960, r: 0.856, f1: 0.905 }, claude: { p: 0.789, r: 0.963, f1: 0.867 } },
] as const;

// --- Per-Entity-Type Results (Legislative branch, from methodology doc §4.2) ---

export const PER_ENTITY_TYPE_RESULTS = [
  { type: 'person', spacy: { p: 0.20, r: 1.00, f1: 0.33 }, claude: { p: 0.56, r: 1.00, f1: 0.72 } },
  { type: 'government_org', spacy: { p: 0.18, r: 1.00, f1: 0.31 }, claude: { p: 0.48, r: 1.00, f1: 0.65 } },
  { type: 'location', spacy: { p: 0.13, r: 0.88, f1: 0.23 }, claude: { p: 0.42, r: 0.92, f1: 0.58 } },
  { type: 'concept', spacy: { p: 0.14, r: 1.00, f1: 0.25 }, claude: { p: 0.33, r: 1.00, f1: 0.49 } },
  { type: 'organization', spacy: { p: 0.03, r: 1.00, f1: 0.06 }, claude: { p: 0.03, r: 1.00, f1: 0.06 } },
  { type: 'event', spacy: { p: 0.00, r: 0.00, f1: 0.00 }, claude: { p: 0.25, r: 1.00, f1: 0.40 } },
] as const;

// --- Cost/Quality Tradeoff ---

export const COST_COMPARISON = {
  spacy: { costPerArticle: '$0.00', avgGovF1: 0.31 },
  claude: { costPerArticle: '~$0.004', avgGovF1: 0.60, f1Improvement: '+94%' },
  costFor50Articles: '~$0.20',
} as const;

// --- Tools Used ---

export const TOOLS_USED = [
  { name: 'Promptfoo', category: 'Evaluation', description: 'LLM evaluation framework' },
  { name: 'spaCy', category: 'NLP', description: 'Statistical NER pipeline' },
  { name: 'Claude API', category: 'AI', description: 'LLM entity extraction' },
  { name: 'Python', category: 'Language', description: 'Evaluation scripts & providers' },
  { name: 'Pydantic', category: 'Validation', description: 'Data model validation' },
  { name: 'TypeScript', category: 'Language', description: 'Frontend dashboard' },
  { name: 'Next.js', category: 'Framework', description: 'React framework' },
  { name: 'Recharts', category: 'Visualization', description: 'Charts & graphs' },
  { name: 'GitHub Actions', category: 'CI/CD', description: 'Automated evaluation runs' },
] as const;

// --- Limitations ---

export const LIMITATIONS = [
  'Gold dataset size: 113 articles with 64 curated is sufficient for methodology demonstration but small for production-grade evaluation.',
  'Synthetic articles: Generated articles may not fully represent real-world news complexity.',
  'Single annotator: Curation was performed by one person; inter-annotator agreement was not measured.',
  'Entity type ambiguity: The boundary between "organization" and "government_org" is subjective for some entities.',
] as const;

// --- Future Work ---

export const FUTURE_WORK = [
  { title: 'EVAL-3: Cognitive Bias Evaluation', description: 'Extend the harness pattern to evaluate bias detection using an ontology-grounded approach.' },
  { title: 'Larger gold dataset', description: 'Expand to 200+ curated articles with multiple annotators for inter-annotator agreement.' },
  { title: 'Additional extractors', description: 'Add GPT-4, Gemini, and larger spaCy models for broader comparison.' },
  { title: 'Active learning', description: 'Use evaluation results to identify articles where annotation would most improve the dataset.' },
  { title: 'Prompt engineering', description: 'Iterate on Claude\'s extraction prompt to improve precision on "concept" and "organization" types.' },
] as const;
