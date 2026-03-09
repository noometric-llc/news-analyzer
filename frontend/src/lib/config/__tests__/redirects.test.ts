/**
 * Tests for Next.js redirect configuration
 *
 * These tests validate that the redirect configuration in next.config.js
 * correctly maps legacy routes to their new destinations.
 */

import { describe, it, expect } from 'vitest';

// Import the redirect configuration by requiring next.config.js
// Note: This is a simplified validation - actual redirect behavior
// is tested by Next.js at runtime
// eslint-disable-next-line @typescript-eslint/no-var-requires
const nextConfig = require('../../../../next.config.js');

describe('Next.js Redirects Configuration', () => {
  let redirects: Array<{
    source: string;
    destination: string;
    permanent: boolean;
  }>;

  beforeAll(async () => {
    // Get redirects from the async function
    redirects = await nextConfig.redirects();
  });

  describe('Redirect Structure', () => {
    it('returns an array of redirect objects', () => {
      expect(Array.isArray(redirects)).toBe(true);
      expect(redirects.length).toBeGreaterThan(0);
    });

    it('each redirect has required fields', () => {
      redirects.forEach((redirect) => {
        expect(redirect).toHaveProperty('source');
        expect(redirect).toHaveProperty('destination');
        expect(redirect).toHaveProperty('permanent');
        expect(typeof redirect.source).toBe('string');
        expect(typeof redirect.destination).toBe('string');
        expect(typeof redirect.permanent).toBe('boolean');
      });
    });
  });

  describe('Factbase Root Redirects', () => {
    it('/factbase redirects to /knowledge-base', () => {
      const redirect = redirects.find((r) => r.source === '/factbase');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base');
      expect(redirect?.permanent).toBe(true);
    });
  });

  describe('Government Branch Redirects (UI-3.A.2)', () => {
    it('/factbase/organizations/executive redirects to /knowledge-base/government/executive', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/organizations/executive');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/government/executive');
      expect(redirect?.permanent).toBe(true);
    });

    it('/factbase/organizations/legislative redirects to /knowledge-base/government/legislative', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/organizations/legislative');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/government/legislative');
      expect(redirect?.permanent).toBe(true);
    });

    it('/factbase/organizations/judicial redirects to /knowledge-base/government/judicial', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/organizations/judicial');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/government/judicial');
      expect(redirect?.permanent).toBe(true);
    });

    it('/factbase/government-orgs redirects to /knowledge-base/government', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/government-orgs');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/government');
      expect(redirect?.permanent).toBe(true);
    });
  });

  describe('People Redirects', () => {
    it('/factbase/people/federal-judges redirects with judges subtype', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/people/federal-judges');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/people?type=judges');
      expect(redirect?.permanent).toBe(true);
    });

    it('/factbase/people/congressional-members redirects with members subtype', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/people/congressional-members');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/people?type=members');
      expect(redirect?.permanent).toBe(true);
    });

    it('/factbase/people/executive-appointees redirects with appointees subtype', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/people/executive-appointees');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/people?type=appointees');
      expect(redirect?.permanent).toBe(true);
    });

    it('/factbase/people/:path* redirects to /knowledge-base/people', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/people/:path*');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/people');
      expect(redirect?.permanent).toBe(true);
    });
  });

  describe('Organization Redirects', () => {
    it('/factbase/organizations/:path* redirects to /knowledge-base/organizations', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/organizations/:path*');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/organizations');
      expect(redirect?.permanent).toBe(true);
    });
  });

  describe('Committees Redirects', () => {
    it('/factbase/committees redirects to /knowledge-base/committees', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/committees');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/committees');
      expect(redirect?.permanent).toBe(true);
    });

    it('/factbase/committees/:path* redirects to /knowledge-base/committees', () => {
      const redirect = redirects.find((r) => r.source === '/factbase/committees/:path*');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/knowledge-base/committees');
      expect(redirect?.permanent).toBe(true);
    });
  });

  describe('Redirect Status Codes', () => {
    it('all factbase redirects are permanent (308)', () => {
      const factbaseRedirects = redirects.filter((r) => r.source.startsWith('/factbase'));
      expect(factbaseRedirects.length).toBeGreaterThan(0);
      factbaseRedirects.forEach((redirect) => {
        expect(redirect.permanent).toBe(true);
      });
    });
  });

  describe('Redirect Order (specificity)', () => {
    it('branch-specific redirects come before generic organization redirect', () => {
      const executiveIndex = redirects.findIndex(
        (r) => r.source === '/factbase/organizations/executive'
      );
      const genericIndex = redirects.findIndex(
        (r) => r.source === '/factbase/organizations/:path*'
      );

      expect(executiveIndex).toBeGreaterThan(-1);
      expect(genericIndex).toBeGreaterThan(-1);
      expect(executiveIndex).toBeLessThan(genericIndex);
    });

    it('subtype-specific people redirects come before generic people redirect', () => {
      const judgesIndex = redirects.findIndex(
        (r) => r.source === '/factbase/people/federal-judges'
      );
      const genericIndex = redirects.findIndex((r) => r.source === '/factbase/people/:path*');

      expect(judgesIndex).toBeGreaterThan(-1);
      expect(genericIndex).toBeGreaterThan(-1);
      expect(judgesIndex).toBeLessThan(genericIndex);
    });
  });

  describe('Extracted Entity Type Redirects (UI-3.B.2)', () => {
    it('/knowledge-base/person redirects to /article-analyzer/entities?type=person', () => {
      const redirect = redirects.find((r) => r.source === '/knowledge-base/person');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/article-analyzer/entities?type=person');
      expect(redirect?.permanent).toBe(false); // 307 temporary during transition
    });

    it('/knowledge-base/organization redirects to /article-analyzer/entities?type=organization', () => {
      const redirect = redirects.find((r) => r.source === '/knowledge-base/organization');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/article-analyzer/entities?type=organization');
      expect(redirect?.permanent).toBe(false);
    });

    it('/knowledge-base/event redirects to /article-analyzer/entities?type=event', () => {
      const redirect = redirects.find((r) => r.source === '/knowledge-base/event');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/article-analyzer/entities?type=event');
      expect(redirect?.permanent).toBe(false);
    });

    it('/knowledge-base/location redirects to /article-analyzer/entities?type=location', () => {
      const redirect = redirects.find((r) => r.source === '/knowledge-base/location');
      expect(redirect).toBeDefined();
      expect(redirect?.destination).toBe('/article-analyzer/entities?type=location');
      expect(redirect?.permanent).toBe(false);
    });

    it('all extracted entity redirects use temporary (307) status', () => {
      const extractedEntityRedirects = redirects.filter((r) =>
        ['/knowledge-base/person', '/knowledge-base/organization', '/knowledge-base/event', '/knowledge-base/location'].includes(r.source)
      );
      expect(extractedEntityRedirects.length).toBe(4);
      extractedEntityRedirects.forEach((redirect) => {
        expect(redirect.permanent).toBe(false);
      });
    });

    it('all extracted entity redirects point to Article Analyzer', () => {
      const extractedEntityRedirects = redirects.filter((r) =>
        r.source.startsWith('/knowledge-base/') &&
        r.destination.startsWith('/article-analyzer/entities')
      );
      expect(extractedEntityRedirects.length).toBe(4);
    });
  });
});
