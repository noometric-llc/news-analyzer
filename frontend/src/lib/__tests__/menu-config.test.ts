import { describe, it, expect } from 'vitest';
import {
  publicMenuConfig,
  publicMenuItemsFlat,
  articleAnalyzerMenuItems,
} from '../menu-config';

describe('menu-config', () => {
  // ====== Public Menu Config Tests ======

  describe('publicMenuConfig', () => {
    it('has Knowledge Base as root item', () => {
      expect(publicMenuConfig).toHaveLength(1);
      expect(publicMenuConfig[0].label).toBe('Knowledge Base');
    });

    it('has U.S. Federal Government as main category', () => {
      const children = publicMenuConfig[0].children;
      expect(children).toHaveLength(1);
      expect(children?.map((c) => c.label)).toEqual([
        'U.S. Federal Government',
      ]);
    });
  });

  describe('publicMenuItemsFlat', () => {
    it('contains U.S. Federal Government', () => {
      expect(publicMenuItemsFlat).toHaveLength(1);
      expect(publicMenuItemsFlat[0].label).toBe('U.S. Federal Government');
    });

    it('U.S. Federal Government category has correct structure', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      expect(government).toBeDefined();
      expect(government?.href).toBe('/knowledge-base/government');
      // Now has 2 children: Branches (grouping) and U.S. Code
      expect(government?.children).toHaveLength(2);
    });

    it('Branches grouping has no href (non-clickable)', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');

      expect(branches).toBeDefined();
      expect(branches?.href).toBeUndefined();
      expect(branches?.children).toHaveLength(3);
    });

    it('Government branch routes are nested under Branches', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const branchChildren = branches?.children;

      expect(branchChildren?.[0].label).toBe('Executive Branch');
      expect(branchChildren?.[0].href).toBe('/knowledge-base/government/executive');

      expect(branchChildren?.[1].label).toBe('Legislative Branch');
      expect(branchChildren?.[1].href).toBe('/knowledge-base/government/legislative');

      expect(branchChildren?.[2].label).toBe('Judicial Branch');
      expect(branchChildren?.[2].href).toBe('/knowledge-base/government/judicial');
    });

    it('Executive Branch has 5 sub-sections (KB-2.6 consolidated President+VP into Administrations)', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const executive = branches?.children?.find((item) => item.label === 'Executive Branch');

      expect(executive?.children).toHaveLength(5);
      expect(executive?.children?.map((c) => c.label)).toEqual([
        'Presidential Administrations',
        'Executive Office of the President',
        'Cabinet Departments',
        'Independent Agencies',
        'Government Corporations',
      ]);
    });

    it('does NOT have separate President or Vice President entries', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const executive = branches?.children?.find((item) => item.label === 'Executive Branch');
      const labels = executive?.children?.map((c) => c.label) ?? [];

      expect(labels).not.toContain('President of the United States');
      expect(labels).not.toContain('Vice President of the United States');
    });

    it('Executive Branch sub-sections have correct routes', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const executive = branches?.children?.find((item) => item.label === 'Executive Branch');
      const subSections = executive?.children;

      expect(subSections?.[0].href).toBe('/knowledge-base/government/executive/administrations');
      expect(subSections?.[1].href).toBe('/knowledge-base/government/executive/eop');
      expect(subSections?.[2].href).toBe('/knowledge-base/government/executive/cabinet');
      expect(subSections?.[3].href).toBe('/knowledge-base/government/executive/independent-agencies');
      expect(subSections?.[4].href).toBe('/knowledge-base/government/executive/corporations');
    });

    it('Executive Branch sub-sections all have icons', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const executive = branches?.children?.find((item) => item.label === 'Executive Branch');

      executive?.children?.forEach((subSection) => {
        expect(subSection.icon).toBeDefined();
      });
    });

    it('Legislative Branch has 4 sub-sections', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const legislative = branches?.children?.find((item) => item.label === 'Legislative Branch');

      expect(legislative?.children).toHaveLength(4);
      expect(legislative?.children?.map((c) => c.label)).toEqual([
        'Senate',
        'House of Representatives',
        'Support Services',
        'Committees',
      ]);
    });

    it('Legislative Branch sub-sections have correct routes', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const legislative = branches?.children?.find((item) => item.label === 'Legislative Branch');
      const subSections = legislative?.children;

      expect(subSections?.[0].href).toBe('/knowledge-base/government/legislative/senate');
      expect(subSections?.[1].href).toBe('/knowledge-base/government/legislative/house');
      expect(subSections?.[2].href).toBe('/knowledge-base/government/legislative/support-services');
      expect(subSections?.[3].href).toBe('/knowledge-base/government/legislative/committees');
    });

    it('Legislative Branch sub-sections all have icons', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const legislative = branches?.children?.find((item) => item.label === 'Legislative Branch');

      legislative?.children?.forEach((subSection) => {
        expect(subSection.icon).toBeDefined();
      });
    });

    it('Judicial Branch has 4 sub-sections', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const judicial = branches?.children?.find((item) => item.label === 'Judicial Branch');

      expect(judicial?.children).toHaveLength(4);
      expect(judicial?.children?.map((c) => c.label)).toEqual([
        'Supreme Court',
        'Courts of Appeals',
        'District Courts',
        'Specialized Courts',
      ]);
    });

    it('Judicial Branch sub-sections have correct routes', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const judicial = branches?.children?.find((item) => item.label === 'Judicial Branch');
      const subSections = judicial?.children;

      expect(subSections?.[0].href).toBe('/knowledge-base/government/judicial/supreme-court');
      expect(subSections?.[1].href).toBe('/knowledge-base/government/judicial/courts-of-appeals');
      expect(subSections?.[2].href).toBe('/knowledge-base/government/judicial/district-courts');
      expect(subSections?.[3].href).toBe('/knowledge-base/government/judicial/specialized-courts');
    });

    it('Judicial Branch sub-sections all have icons', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const branches = government?.children?.find((item) => item.label === 'Branches');
      const judicial = branches?.children?.find((item) => item.label === 'Judicial Branch');

      judicial?.children?.forEach((subSection) => {
        expect(subSection.icon).toBeDefined();
      });
    });

    it('U.S. Code (Federal Laws) has correct route', () => {
      const government = publicMenuItemsFlat.find(
        (item) => item.label === 'U.S. Federal Government'
      );
      const usCode = government?.children?.find(
        (item) => item.label === 'U.S. Code (Federal Laws)'
      );

      expect(usCode).toBeDefined();
      expect(usCode?.href).toBe('/knowledge-base/government/us-code');
    });

    it('all menu items have icons', () => {
      publicMenuItemsFlat.forEach((item) => {
        expect(item.icon).toBeDefined();
      });
    });
  });

  // ====== Article Analyzer Menu Config Tests ======

  describe('articleAnalyzerMenuItems', () => {
    it('has three menu items', () => {
      expect(articleAnalyzerMenuItems).toHaveLength(3);
    });

    it('Analyze Article is disabled', () => {
      const analyze = articleAnalyzerMenuItems.find((item) => item.label === 'Analyze Article');
      expect(analyze).toBeDefined();
      expect(analyze?.disabled).toBe(true);
      expect(analyze?.href).toBe('/article-analyzer/analyze');
    });

    it('Articles route is correct', () => {
      const articles = articleAnalyzerMenuItems.find((item) => item.label === 'Articles');
      expect(articles).toBeDefined();
      expect(articles?.href).toBe('/article-analyzer/articles');
      expect(articles?.disabled).toBeFalsy();
    });

    it('Entities route is correct', () => {
      const entities = articleAnalyzerMenuItems.find((item) => item.label === 'Entities');
      expect(entities).toBeDefined();
      expect(entities?.href).toBe('/article-analyzer/entities');
      expect(entities?.disabled).toBeFalsy();
    });

    it('all menu items have icons', () => {
      articleAnalyzerMenuItems.forEach((item) => {
        expect(item.icon).toBeDefined();
      });
    });
  });

  // ====== Route Validity Tests ======

  describe('Route Validity', () => {
    it('all KB routes start with /knowledge-base', () => {
      const checkRoutes = (items: typeof publicMenuItemsFlat) => {
        items.forEach((item) => {
          if (item.href) {
            expect(item.href.startsWith('/knowledge-base')).toBe(true);
          }
          if (item.children) {
            checkRoutes(item.children);
          }
        });
      };
      checkRoutes(publicMenuItemsFlat);
    });

    it('all AA routes start with /article-analyzer', () => {
      articleAnalyzerMenuItems.forEach((item) => {
        if (item.href) {
          expect(item.href.startsWith('/article-analyzer')).toBe(true);
        }
      });
    });

    it('no routes contain undefined or empty strings', () => {
      const checkRoutes = (items: typeof publicMenuItemsFlat) => {
        items.forEach((item) => {
          if (item.href !== undefined) {
            expect(item.href).not.toBe('');
            expect(item.href).not.toBe('undefined');
          }
          if (item.children) {
            checkRoutes(item.children);
          }
        });
      };
      checkRoutes(publicMenuItemsFlat);
      checkRoutes(articleAnalyzerMenuItems);
    });
  });
});
