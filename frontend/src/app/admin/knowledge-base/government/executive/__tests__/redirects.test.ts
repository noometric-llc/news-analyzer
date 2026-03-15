import { describe, it, expect, vi } from 'vitest';

// Mock next/navigation redirect
const mockRedirect = vi.fn();
vi.mock('next/navigation', () => ({
  redirect: (...args: unknown[]) => {
    mockRedirect(...args);
    throw new Error('NEXT_REDIRECT');
  },
}));

describe('Admin route redirects (KB-2.6)', () => {
  beforeEach(() => {
    mockRedirect.mockClear();
  });

  it('/admin/president redirects to /admin/administrations', async () => {
    const { default: AdminPresidentPage } = await import('../president/page');
    expect(() => AdminPresidentPage()).toThrow('NEXT_REDIRECT');
    expect(mockRedirect).toHaveBeenCalledWith(
      '/admin/knowledge-base/government/executive/administrations'
    );
  });

  it('/admin/vice-president redirects to /admin/administrations', async () => {
    const { default: AdminVicePresidentPage } = await import('../vice-president/page');
    expect(() => AdminVicePresidentPage()).toThrow('NEXT_REDIRECT');
    expect(mockRedirect).toHaveBeenCalledWith(
      '/admin/knowledge-base/government/executive/administrations'
    );
  });
});
