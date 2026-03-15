'use client';

import {
  Database,
  Building2,
  Landmark,
  Scale,
  Gavel,
  ScrollText,
  Search,
  Github,
  BookOpen,
  Building,
  Briefcase,
  Factory,
  Home,
  HeartHandshake,
  Users2,
  MapPin,
} from 'lucide-react';
import { useSidebarStore } from '@/stores/sidebarStore';
import { BaseSidebar, SidebarMenuItem, MenuItemData } from '@/components/sidebar';

// Menu structure mirroring public Knowledge Base
const menuItems: MenuItemData[] = [
  {
    label: 'Knowledge Base',
    icon: BookOpen,
    children: [
      {
        label: 'U.S. Federal Government',
        icon: Building2,
        href: '/admin/knowledge-base/government',
        children: [
          {
            label: 'Executive Branch',
            icon: Building,
            href: '/admin/knowledge-base/government/executive',
            children: [
              { label: 'Presidential Administrations', href: '/admin/knowledge-base/government/executive/administrations', icon: Landmark },
              { label: 'Executive Office', href: '/admin/knowledge-base/government/executive/eop', icon: Building },
              { label: 'Cabinet Departments', href: '/admin/knowledge-base/government/executive/cabinet', icon: Briefcase },
              { label: 'Independent Agencies', href: '/admin/knowledge-base/government/executive/independent-agencies', icon: Building2 },
              { label: 'Gov. Corporations', href: '/admin/knowledge-base/government/executive/corporations', icon: Factory },
            ],
          },
          {
            label: 'Legislative Branch',
            icon: Landmark,
            href: '/admin/knowledge-base/government/legislative',
            children: [
              { label: 'Senate', href: '/admin/knowledge-base/government/legislative/senate', icon: Building2 },
              { label: 'House of Representatives', href: '/admin/knowledge-base/government/legislative/house', icon: Home },
              { label: 'Support Services', href: '/admin/knowledge-base/government/legislative/support-services', icon: HeartHandshake },
              { label: 'Committees', href: '/admin/knowledge-base/government/legislative/committees', icon: Users2 },
            ],
          },
          {
            label: 'Judicial Branch',
            icon: Scale,
            href: '/admin/knowledge-base/government/judicial',
            children: [
              { label: 'Supreme Court', href: '/admin/knowledge-base/government/judicial/supreme-court', icon: Gavel },
              { label: 'Courts of Appeals', href: '/admin/knowledge-base/government/judicial/courts-of-appeals', icon: Building },
              { label: 'District Courts', href: '/admin/knowledge-base/government/judicial/district-courts', icon: MapPin },
              { label: 'Specialized Courts', href: '/admin/knowledge-base/government/judicial/specialized-courts', icon: Briefcase },
            ],
          },
          {
            label: 'U.S. Code',
            icon: BookOpen,
            href: '/admin/knowledge-base/government/us-code',
          },
        ],
      },
    ],
  },
  // TECHNICAL DEBT: Legacy Factbase section - to be removed after migration complete
  // See docs/stories/UI-6/ for migration tracking
  {
    label: 'Factbase (Legacy)',
    icon: Database,
    children: [
      {
        label: 'Government Entities',
        icon: Building2,
        children: [
          {
            label: 'Executive Branch',
            icon: Landmark,
            children: [
              { label: 'Agencies & Departments', href: '/admin/factbase/executive/agencies' },
              { label: 'Positions & Appointees', href: '/admin/factbase/executive/positions' },
              { label: 'GOVMAN Import', href: '/admin/factbase/executive/govman' },
            ],
          },
          {
            label: 'Legislative Branch',
            icon: Scale,
            children: [
              { label: 'Members', href: '/admin/factbase/legislative/members' },
              { label: 'Search Congress.gov', href: '/admin/factbase/legislative/members/search', icon: Search },
              { label: 'Legislators Repo', href: '/admin/factbase/legislative/legislators-repo', icon: Github },
              { label: 'Committees', href: '/admin/factbase/legislative/committees' },
            ],
          },
          {
            label: 'Judicial Branch',
            icon: Gavel,
            children: [
              { label: 'Courts', href: '/admin/factbase/judicial/courts' },
            ],
          },
        ],
      },
      {
        label: 'Federal Laws & Regulations',
        icon: ScrollText,
        children: [
          { label: 'Regulations (Federal Register)', href: '/admin/factbase/regulations/federal-register' },
          { label: 'Search Federal Register', href: '/admin/factbase/regulations/search', icon: Search },
          { label: 'US Code', href: '/admin/factbase/regulations/us-code' },
        ],
      },
    ],
  },
];

interface AdminSidebarProps {
  className?: string;
}

export function AdminSidebar({ className }: AdminSidebarProps) {
  const { isCollapsed, toggle, closeMobile } = useSidebarStore();

  return (
    <BaseSidebar
      menuItems={menuItems}
      isCollapsed={isCollapsed}
      onToggle={toggle}
      header={<span className="font-semibold text-lg">Admin</span>}
      footer={
        <SidebarMenuItem
          item={{
            label: 'Dashboard',
            href: '/admin',
            icon: Database,
          }}
          isCollapsed={isCollapsed}
          onNavigate={closeMobile}
        />
      }
      className={className}
      ariaLabel="Admin navigation"
      onNavigate={closeMobile}
    />
  );
}
