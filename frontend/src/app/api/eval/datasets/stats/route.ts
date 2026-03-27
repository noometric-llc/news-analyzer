import { NextResponse } from 'next/server';

const BACKEND_URL = process.env.BACKEND_URL || process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

/**
 * GET /api/eval/datasets/stats
 *
 * Proxies to backend: GET /api/eval/datasets/stats
 * Returns dataset statistics (total articles, breakdowns by branch/perturbation/difficulty).
 */
export async function GET() {
  try {
    const res = await fetch(`${BACKEND_URL}/api/eval/datasets/stats`);
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
