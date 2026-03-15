'use client';

import Link from 'next/link';
import { ArrowLeft, Landmark, Users, History } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { KBBreadcrumbs } from './KBBreadcrumbs';

interface AdministrationPageProps {
  /** Force loading state (for testing/storybook) */
  isLoading?: boolean;
  /** Force error state (for testing/storybook) */
  error?: string | null;
}

/**
 * AdministrationPage - Page shell for Presidential Administrations (KB-2.1).
 *
 * Provides the layout structure with placeholder sections that
 * KB-2.2 (Current Administration) and KB-2.3 (Historical Administrations)
 * will replace with real content.
 */
export function AdministrationPage({ isLoading = false, error = null }: AdministrationPageProps) {
  if (isLoading) {
    return <AdministrationPageSkeleton />;
  }

  if (error) {
    return (
      <div className="container py-8">
        <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-6 text-center">
          <p className="text-destructive font-medium">Failed to load administration data</p>
          <p className="text-sm text-muted-foreground mt-1">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-8">
      {/* Back link */}
      <div className="mb-4">
        <Button variant="ghost" size="sm" asChild className="-ml-2">
          <Link href="/knowledge-base/government/executive">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Executive Branch
          </Link>
        </Button>
      </div>

      {/* Breadcrumbs */}
      <KBBreadcrumbs className="mb-6" />

      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-4">
          <div className="p-3 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
            <Landmark className="h-8 w-8" />
          </div>
          <h1 className="text-3xl font-bold">Presidential Administrations</h1>
        </div>
        <p className="text-lg text-muted-foreground max-w-3xl">
          Explore the executive branch through presidential administrations — from the current
          administration&apos;s leadership team to the complete historical record.
        </p>
      </div>

      {/* Current Administration Section Placeholder */}
      <section className="mb-10">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Users className="h-5 w-5" />
              Current Administration
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground">
              Coming in KB-2.2 — Current president, vice president, staff, and executive orders.
            </p>
          </CardContent>
        </Card>
      </section>

      {/* Historical Administrations Section Placeholder */}
      <section className="mb-10">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <History className="h-5 w-5" />
              Historical Administrations
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground">
              Coming in KB-2.3 — Browse all past presidential administrations with detailed records.
            </p>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}

/**
 * Loading skeleton for the AdministrationPage.
 */
function AdministrationPageSkeleton() {
  return (
    <div className="container py-8">
      <Skeleton className="h-8 w-48 mb-4" />
      <Skeleton className="h-4 w-64 mb-6" />
      <div className="mb-8">
        <Skeleton className="h-10 w-96 mb-4" />
        <Skeleton className="h-6 w-full max-w-3xl" />
      </div>
      <div className="space-y-6">
        <Skeleton className="h-32 w-full rounded-lg" />
        <Skeleton className="h-32 w-full rounded-lg" />
      </div>
    </div>
  );
}
