'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ChevronRight, Home } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Breadcrumb item configuration
 */
interface BreadcrumbItem {
  label: string;
  href: string;
}

/**
 * Route segment to label mapping
 */
const segmentLabels: Record<string, string> = {
  'knowledge-base': 'Knowledge Base',
  'government': 'U.S. Federal Government',
  'executive': 'Executive Branch',
  'legislative': 'Legislative Branch',
  'judicial': 'Judicial Branch',
  'us-code': 'U.S. Code',
  'organizations': 'Organizations',
  'people': 'People',
  'committees': 'Committees',
  // Executive Branch sub-sections (UI-6.3)
  'president': 'President',
  'vice-president': 'Vice President',
  'eop': 'Executive Office',
  'cabinet': 'Cabinet Departments',
  'independent-agencies': 'Independent Agencies',
  'corporations': 'Government Corporations',
  'administrations': 'Presidential Administrations',
};

/**
 * Build breadcrumb items from pathname
 */
function buildBreadcrumbs(pathname: string): BreadcrumbItem[] {
  const segments = pathname.split('/').filter(Boolean);
  const breadcrumbs: BreadcrumbItem[] = [];

  let currentPath = '';
  for (const segment of segments) {
    currentPath += `/${segment}`;

    // Skip dynamic segments like [entityType] or [id] - they would be handled by entity detail pages
    if (segment.startsWith('[')) continue;

    // Check if it's a known segment
    const label = segmentLabels[segment];
    if (label) {
      breadcrumbs.push({
        label,
        href: currentPath,
      });
    }
  }

  return breadcrumbs;
}

interface KBBreadcrumbsProps {
  /** Optional className for the nav element */
  className?: string;
  /** Whether to show the home icon */
  showHome?: boolean;
}

/**
 * KBBreadcrumbs - Displays navigation breadcrumbs for Knowledge Base pages.
 * Automatically builds breadcrumb path from the current URL.
 */
export function KBBreadcrumbs({ className, showHome = true }: KBBreadcrumbsProps) {
  const pathname = usePathname();

  // Only show breadcrumbs on knowledge-base routes
  if (!pathname.startsWith('/knowledge-base')) {
    return null;
  }

  const breadcrumbs = buildBreadcrumbs(pathname);

  // Don't show if only one item (we're at root)
  if (breadcrumbs.length <= 1) {
    return null;
  }

  return (
    <nav
      aria-label="Breadcrumb"
      className={cn('flex items-center text-sm', className)}
    >
      <ol className="flex items-center flex-wrap gap-1">
        {showHome && (
          <>
            <li>
              <Link
                href="/"
                className="text-muted-foreground hover:text-foreground transition-colors p-1"
                aria-label="Home"
              >
                <Home className="h-4 w-4" />
              </Link>
            </li>
            <li aria-hidden="true">
              <ChevronRight className="h-4 w-4 text-muted-foreground" />
            </li>
          </>
        )}

        {breadcrumbs.map((crumb, index) => {
          const isLast = index === breadcrumbs.length - 1;

          return (
            <li key={crumb.href} className="flex items-center gap-1">
              {index > 0 && (
                <ChevronRight
                  className="h-4 w-4 text-muted-foreground flex-shrink-0"
                  aria-hidden="true"
                />
              )}
              {isLast ? (
                <span
                  className="font-medium text-foreground truncate max-w-[200px]"
                  aria-current="page"
                >
                  {crumb.label}
                </span>
              ) : (
                <Link
                  href={crumb.href}
                  className="text-muted-foreground hover:text-foreground transition-colors truncate max-w-[200px]"
                >
                  {crumb.label}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
