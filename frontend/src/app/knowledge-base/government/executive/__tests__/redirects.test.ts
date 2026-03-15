import { describe, it, expect, vi } from 'vitest';

// Mock next/navigation redirect
const mockRedirect = vi.fn();
vi.mock('next/navigation', () => ({
  redirect: (...args: unknown[]) => {
    mockRedirect(...args);
    throw new Error('NEXT_REDIRECT'); // redirect() throws to halt execution
  },
}));

describe('KB route redirects (KB-2.6)', () => {
  beforeEach(() => {
    mockRedirect.mockClear();
  });

  it('/president redirects to /administrations', async () => {
    const { default: PresidentPage } = await import('../president/page');
    expect(() => PresidentPage()).toThrow('NEXT_REDIRECT');
    expect(mockRedirect).toHaveBeenCalledWith(
      '/knowledge-base/government/executive/administrations'
    );
  });

  it('/vice-president redirects to /administrations', async () => {
    const { default: VicePresidentPage } = await import('../vice-president/page');
    expect(() => VicePresidentPage()).toThrow('NEXT_REDIRECT');
    expect(mockRedirect).toHaveBeenCalledWith(
      '/knowledge-base/government/executive/administrations'
    );
  });
});
