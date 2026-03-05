'use client';

import { Building2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { AdminBreadcrumb } from '@/components/admin/AdminBreadcrumb';
import { GovOrgSyncStatusCard } from '@/components/admin/GovOrgSyncStatusCard';
import { SyncButton } from '@/components/admin/SyncButton';
import { CsvImportButton } from '@/components/admin/CsvImportButton';
import { useGovernmentOrgSyncStatus } from '@/hooks/useGovernmentOrgs';

const breadcrumbs = [
  { label: 'Admin', href: '/admin' },
  { label: 'Factbase' },
  { label: 'Executive', href: '/admin/factbase/executive' },
  { label: 'Agencies & Departments' },
];

export default function AgenciesPage() {
  const { data: syncStatus } = useGovernmentOrgSyncStatus();

  return (
    <main className="container mx-auto py-8 px-4">
      <AdminBreadcrumb items={breadcrumbs} />

      <div className="flex items-center gap-3 mb-6">
        <Building2 className="h-8 w-8 text-primary" />
        <h1 className="text-3xl font-bold">Agencies & Departments</h1>
      </div>

      <p className="text-muted-foreground mb-8">
        Manage government organizations from the Federal Register API and CSV imports.
      </p>

      {/* Status Overview */}
      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-4">Status Overview</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <GovOrgSyncStatusCard />
        </div>
      </section>

      {/* Sync Actions */}
      <section className="mb-8">
        <Card>
          <CardHeader>
            <CardTitle>Sync Actions</CardTitle>
            <CardDescription>
              Synchronize government organization data from external sources.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-3">
              <SyncButton
                type="gov-orgs"
                title="Sync Government Orgs"
                description={`This will fetch ~300 agencies from the Federal Register API and update the database. Up to ${syncStatus?.maxNewOrgs ?? 50} new organizations will be added per sync.`}
                warning="May take 30-60 seconds to complete. Existing manually curated data will be preserved. Only executive branch agencies will be synced."
              />
            </div>
          </CardContent>
        </Card>
      </section>

      {/* Data Import */}
      <section className="mb-8">
        <Card>
          <CardHeader>
            <CardTitle>Data Import</CardTitle>
            <CardDescription>
              Import government organization data from CSV files. Use this for Legislative and Judicial branches.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-3">
              <CsvImportButton />
            </div>
          </CardContent>
        </Card>
      </section>
    </main>
  );
}
