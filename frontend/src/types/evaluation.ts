/**
 * Evaluation data types.
 *
 * These types match the API responses from:
 * - GET /api/eval/results (EvalSummary)
 * - GET /api/eval/results/[branch] (BranchDetailResult)
 * - GET /api/eval/datasets/stats (DatasetStats)
 * - GET /api/eval/datasets/articles (ArticlePage)
 */

// --- Summary data (from summary.json) ---

export interface BranchResult {
  precision: number;
  recall: number;
  f1: number;
  true_positives: number;
  false_positives: number;
  false_negatives: number;
  article_count: number;
}

export interface EvalSummary {
  generated: string;
  branches: Record<string, Record<string, BranchResult>>;
}

// --- Extractor name constants (used across components) ---

export const EXTRACTOR_NAMES = {
  claude: 'Claude Sonnet',
  spacy: 'spaCy en_core_web_sm',
} as const;

// --- Per-entity-type breakdown (from *_results.json namedScores) ---

export interface EntityTypeMetrics {
  tp: number;
  fp: number;
  fn: number;
}

export type EntityTypeName =
  | 'person'
  | 'government_org'
  | 'organization'
  | 'location'
  | 'event'
  | 'concept'
  | 'legislation';

export const ENTITY_TYPE_NAMES: EntityTypeName[] = [
  'person',
  'government_org',
  'organization',
  'location',
  'event',
  'concept',
  'legislation',
];

export interface BranchDetailResult {
  branch: string;
  extractors: Record<string, {
    overall: {
      precision: number;
      recall: number;
      f1: number;
      true_positives: number;
      false_positives: number;
      false_negatives: number;
    };
    byEntityType: Record<EntityTypeName, EntityTypeMetrics>;
  }>;
}

// --- Dataset types (from backend eval dataset API) ---

export interface DatasetStats {
  totalArticles: number;
  faithfulCount: number;
  perturbedCount: number;
  byPerturbationType: Record<string, number>;
  byDifficulty: Record<string, number>;
  byBranch: Record<string, number>;
}

export interface SyntheticArticle {
  id: string;
  batchId: string;
  articleText: string;
  articleType: string;
  isFaithful: boolean;
  perturbationType: string | null;
  difficulty: string;
  sourceFacts: Record<string, unknown>;
  groundTruth: Record<string, unknown>;
  modelUsed: string;
  tokensUsed: number;
  createdAt: string;
}

export interface ArticlePage {
  content: SyntheticArticle[];
  totalElements: number;
  totalPages: number;
  number: number; // current page (0-based)
  size: number;
}

export interface ArticleQueryParams {
  page?: number;
  size?: number;
  branch?: string;
  perturbationType?: string;
  difficulty?: string;
  isFaithful?: boolean;
}

// --- Live extraction comparison types (EVAL-DASH.5) ---

export interface ExtractedEntity {
  text: string;
  entity_type: string;
  start: number;
  end: number;
  confidence: number;
  schema_org_type?: string;
}

export interface ExtractionResult {
  entities: ExtractedEntity[];
  total_count: number;
}

export interface GoldEntity {
  text: string;
  type: string;
  start: number;
  end: number;
}

export interface SampleArticle {
  label: string;
  branch: string;
  text: string;
  goldEntities: GoldEntity[];
}
