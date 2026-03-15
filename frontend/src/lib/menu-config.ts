import {
  Database,
  Building,
  Building2,
  Landmark,
  Scale,
  FileText,
  List,
  GitBranch,
  BookOpen,
  Briefcase,
  Factory,
  Home,
  HeartHandshake,
  Users2,
  Gavel,
  MapPin,
} from 'lucide-react';
import { MenuItemData } from '@/components/sidebar/types';

/**
 * Public sidebar menu configuration for Knowledge Base.
 *
 * Structure matches the actual KB route hierarchy:
 * - U.S. Federal Government → /knowledge-base/government
 *   - Branches (non-clickable grouping) → executive/legislative/judicial
 *   - U.S. Code (Federal Laws) → /knowledge-base/government/us-code
 */
export const publicMenuConfig: MenuItemData[] = [
  {
    label: 'Knowledge Base',
    icon: Database,
    children: [
      {
        label: 'U.S. Federal Government',
        icon: Building,
        href: '/knowledge-base/government',
        children: [
          {
            label: 'Branches',
            icon: GitBranch,
            // No href - non-clickable grouping
            children: [
              {
                label: 'Executive Branch',
                icon: Building,
                href: '/knowledge-base/government/executive',
                children: [
                  {
                    label: 'Presidential Administrations',
                    icon: Landmark,
                    href: '/knowledge-base/government/executive/administrations',
                  },
                  {
                    label: 'Executive Office of the President',
                    icon: Building,
                    href: '/knowledge-base/government/executive/eop',
                  },
                  {
                    label: 'Cabinet Departments',
                    icon: Briefcase,
                    href: '/knowledge-base/government/executive/cabinet',
                  },
                  {
                    label: 'Independent Agencies',
                    icon: Building2,
                    href: '/knowledge-base/government/executive/independent-agencies',
                  },
                  {
                    label: 'Government Corporations',
                    icon: Factory,
                    href: '/knowledge-base/government/executive/corporations',
                  },
                ],
              },
              {
                label: 'Legislative Branch',
                icon: Landmark,
                href: '/knowledge-base/government/legislative',
                children: [
                  {
                    label: 'Senate',
                    icon: Building2,
                    href: '/knowledge-base/government/legislative/senate',
                  },
                  {
                    label: 'House of Representatives',
                    icon: Home,
                    href: '/knowledge-base/government/legislative/house',
                  },
                  {
                    label: 'Support Services',
                    icon: HeartHandshake,
                    href: '/knowledge-base/government/legislative/support-services',
                  },
                  {
                    label: 'Committees',
                    icon: Users2,
                    href: '/knowledge-base/government/legislative/committees',
                  },
                ],
              },
              {
                label: 'Judicial Branch',
                icon: Scale,
                href: '/knowledge-base/government/judicial',
                children: [
                  {
                    label: 'Supreme Court',
                    icon: Gavel,
                    href: '/knowledge-base/government/judicial/supreme-court',
                  },
                  {
                    label: 'Courts of Appeals',
                    icon: Building,
                    href: '/knowledge-base/government/judicial/courts-of-appeals',
                  },
                  {
                    label: 'District Courts',
                    icon: MapPin,
                    href: '/knowledge-base/government/judicial/district-courts',
                  },
                  {
                    label: 'Specialized Courts',
                    icon: Briefcase,
                    href: '/knowledge-base/government/judicial/specialized-courts',
                  },
                ],
              },
            ],
          },
          {
            label: 'U.S. Code (Federal Laws)',
            icon: BookOpen,
            href: '/knowledge-base/government/us-code',
          },
        ],
      },
    ],
  },
];

/**
 * Flattened menu config without the Knowledge Base wrapper.
 * Use this when the sidebar header already displays "Knowledge Base".
 */
export const publicMenuItemsFlat: MenuItemData[] = publicMenuConfig[0]?.children ?? [];

/**
 * Article Analyzer sidebar menu configuration.
 * Matches the routes in /article-analyzer section.
 */
export const articleAnalyzerMenuItems: MenuItemData[] = [
  {
    label: 'Analyze Article',
    icon: FileText,
    href: '/article-analyzer/analyze',
    disabled: true, // Phase 4 feature
  },
  {
    label: 'Articles',
    icon: List,
    href: '/article-analyzer/articles',
  },
  {
    label: 'Entities',
    icon: Database,
    href: '/article-analyzer/entities',
  },
];
