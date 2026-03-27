import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.BACKEND_URL || process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

/**
 * GET /api/eval/datasets/articles
 *
 * Proxies to backend: GET /api/eval/datasets/articles
 * Forwards query params: page, size, branch, perturbationType, difficulty, isFaithful
 *
 * Known issue MNT-002: Backend's native JSONB query may return 500 when
 * filters are applied. If this occurs, the frontend should handle the error
 * gracefully and suggest clearing filters.
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams.toString();
    const url = searchParams
      ? `${BACKEND_URL}/api/eval/datasets/articles?${searchParams}`
      : `${BACKEND_URL}/api/eval/datasets/articles`;

    const res = await fetch(url);
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
