import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.BACKEND_URL || process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

/**
 * GET /api/eval/datasets/articles/[id]
 *
 * Proxies to backend: GET /api/eval/datasets/articles/{id}
 * Returns a single SyntheticArticleDTO with full sourceFacts and groundTruth.
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;

  try {
    const res = await fetch(`${BACKEND_URL}/api/eval/datasets/articles/${encodeURIComponent(id)}`);
    if (!res.ok) {
      return NextResponse.json(
        { error: `Backend returned ${res.status}: ${res.statusText}` },
        { status: res.status }
      );
    }
    const data = await res.json();
    return NextResponse.json(data);
  } catch (error) {
    return NextResponse.json(
      { error: 'Dataset API unavailable — ensure the backend service is running' },
      { status: 503 }
    );
  }
}
