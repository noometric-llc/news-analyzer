import type {
  EvalSummary,
  BranchDetailResult,
  DatasetStats,
  SyntheticArticle,
  ArticlePage,
  ArticleQueryParams,
  ExtractionResult,
} from '@/types/evaluation';

/**
 * Evaluation API client.
 *
 * Calls internal Next.js API routes (/api/eval/...) which read
 * static evaluation result files from the filesystem.
 *
 * Uses native fetch() instead of the project's shared Axios clients
 * (backendClient/reasoningClient) because these are same-origin requests
 * to internal API routes — no OTel trace propagation needed.
 */

export async function getEvalSummary(): Promise<EvalSummary> {
  const res = await fetch('/api/eval/results');
  if (!res.ok) {
    throw new Error(`Failed to fetch evaluation summary: ${res.statusText}`);
  }
  return res.json();
}

export async function getBranchDetail(branch: string): Promise<BranchDetailResult> {
  const res = await fetch(`/api/eval/results/${encodeURIComponent(branch)}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch branch detail for ${branch}: ${res.statusText}`);
  }
  return res.json();
}

// --- Dataset API functions (proxy to Spring Boot backend) ---

export async function getDatasetStats(): Promise<DatasetStats> {
  const res = await fetch('/api/eval/datasets/stats');
  if (!res.ok) {
    throw new Error(`Failed to fetch dataset stats: ${res.statusText}`);
  }
  return res.json();
}

export async function getDatasetArticles(params: ArticleQueryParams = {}): Promise<ArticlePage> {
  const searchParams = new URLSearchParams();

  if (params.page !== undefined) searchParams.set('page', params.page.toString());
  if (params.size !== undefined) searchParams.set('size', params.size.toString());
  if (params.branch) searchParams.set('branch', params.branch);
  if (params.perturbationType) searchParams.set('perturbationType', params.perturbationType);
  if (params.difficulty) searchParams.set('difficulty', params.difficulty);
  if (params.isFaithful !== undefined) searchParams.set('isFaithful', params.isFaithful.toString());

  const query = searchParams.toString();
  const url = query ? `/api/eval/datasets/articles?${query}` : '/api/eval/datasets/articles';

  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch articles: ${res.statusText}`);
  }
  return res.json();
}

export async function getArticleDetail(id: string): Promise<SyntheticArticle> {
  const res = await fetch(`/api/eval/datasets/articles/${encodeURIComponent(id)}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch article ${id}: ${res.statusText}`);
  }
  return res.json();
}

// --- Live extraction API functions (proxy to reasoning service) ---

export async function extractSpacy(text: string): Promise<ExtractionResult> {
  const res = await fetch('/api/eval/extract/spacy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, confidence_threshold: 0.0 }),
  });
  if (!res.ok) {
    throw new Error(`spaCy extraction failed: ${res.statusText}`);
  }
  return res.json();
}

export async function extractLLM(text: string): Promise<ExtractionResult> {
  const res = await fetch('/api/eval/extract/llm', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, confidence_threshold: 0.0 }),
  });
  if (!res.ok) {
    throw new Error(`Claude extraction failed: ${res.statusText}`);
  }
  return res.json();
}
