import { NextRequest, NextResponse } from 'next/server';

const REASONING_URL = process.env.REASONING_URL || process.env.NEXT_PUBLIC_REASONING_SERVICE_URL || 'http://localhost:8000';

/**
 * POST /api/eval/extract/spacy
 *
 * Proxies to reasoning service: POST /entities/extract
 * Forwards text and confidence_threshold for spaCy entity extraction.
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const res = await fetch(`${REASONING_URL}/entities/extract`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      return NextResponse.json(
        { error: `spaCy extraction failed: ${res.statusText}` },
        { status: res.status }
      );
    }
    const data = await res.json();
    return NextResponse.json(data);
  } catch (error) {
    return NextResponse.json(
      { error: 'Reasoning service unavailable — ensure it is running on port 8000' },
      { status: 503 }
    );
  }
}
