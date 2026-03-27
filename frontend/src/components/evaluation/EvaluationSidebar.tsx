'use client';

import { Database } from 'lucide-react';
import { useEvaluationSidebarStore } from '@/stores/evaluationSidebarStore';
import { BaseSidebar } from '@/components/sidebar';
import { evaluationMenuItems } from '@/lib/menu-config';
import { cn } from '@/lib/utils';
import Link from 'next/link';

interface EvaluationSidebarProps {
  className?: string;
}

/**
 * EvaluationSidebar - Sidebar navigation for the AI Evaluation section.
 * Provides navigation between Overview, Results, Dataset Explorer, and Methodology.
 * Includes a footer link to Knowledge Base.
 */
export function EvaluationSidebar({ className }: EvaluationSidebarProps) {
  const { isCollapsed, toggle, closeMobile } = useEvaluationSidebarStore();

  const kbFooter = (
    <Link
      href="/knowledge-base"
      className={cn(
        'flex items-center gap-3 w-full rounded-md py-2 px-3 text-sm font-medium',
        'hover:bg-accent hover:text-accent-foreground transition-colors',
        isCollapsed && 'justify-center px-0'
      )}
      title="Knowledge Base"
    >
      <Database className="h-5 w-5 shrink-0" />
      {!isCollapsed && <span>Knowledge Base</span>}
    </Link>
  );

  return (
    <BaseSidebar
      menuItems={evaluationMenuItems}
      isCollapsed={isCollapsed}
      onToggle={toggle}
      header={
        <Link
          href="/evaluation"
          className="font-semibold text-lg hover:text-primary transition-colors"
        >
          AI Evaluation
        </Link>
      }
      footer={kbFooter}
      className={className}
      ariaLabel="AI Evaluation navigation"
      onNavigate={closeMobile}
    />
  );
}
