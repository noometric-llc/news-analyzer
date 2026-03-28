import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()] as any,
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.d.ts',
        'src/test/**',
        'src/**/*.config.*',
        'src/**/types/**',
        'src/**/index.ts', // barrel files
        'src/hooks/**', // hooks need integration tests (future)
        'src/lib/api/**', // API wrappers need integration tests (future)
        'src/lib/utils/**', // utility functions (test as needed)
        'src/lib/constants/**', // constants don't need tests
        'src/app/**', // Next.js pages (covered by E2E tests)
      ],
      // Coverage thresholds - fails if below these percentages
      // Starting at 30% baseline; increase as more tests are added
      // Target: 60% by next quarter
      thresholds: {
        lines: 30,
        functions: 20,
        branches: 50,
        statements: 30,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
