'use client';

/**
 * AdminAdministrationPage (KB-2.4 Task 7)
 *
 * Container component that composes:
 * - AdministrationSyncSection (presidency + EO sync controls)
 * - AdministrationDataSection (presidencies table + staff table)
 * - Three edit modals + one delete dialog
 *
 * Manages modal open/close state and passes callbacks to children.
 */

import { useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, Landmark } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { AdministrationSyncSection } from './AdministrationSyncSection';
import { AdministrationDataSection } from './AdministrationDataSection';
import { PresidencyEditModal } from './PresidencyEditModal';
import { IndividualEditModal } from './IndividualEditModal';
import { PositionHoldingEditModal, DeleteStaffDialog } from './PositionHoldingEditModal';
import type { PresidencyDTO } from '@/hooks/usePresidencySync';
import type { StaffRow } from './AdministrationDataSection';

export function AdminAdministrationPage() {
  // Presidency edit modal
  const [editPresidency, setEditPresidency] = useState<PresidencyDTO | null>(null);
  const [presEditOpen, setPresEditOpen] = useState(false);

  // Individual edit modal
  const [editIndividualId, setEditIndividualId] = useState<string | null>(null);
  const [editIndividualName, setEditIndividualName] = useState('');
  const [indEditOpen, setIndEditOpen] = useState(false);

  // Position holding edit modal (create or edit)
  const [editStaffRow, setEditStaffRow] = useState<StaffRow | null>(null);
  const [staffPresidencyId, setStaffPresidencyId] = useState<string | null>(null);
  const [staffEditOpen, setStaffEditOpen] = useState(false);

  // Delete staff dialog
  const [deleteStaffRow, setDeleteStaffRow] = useState<StaffRow | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);

  // Handlers passed to AdministrationDataSection
  const handleEditPresidency = (presidency: PresidencyDTO) => {
    setEditPresidency(presidency);
    setPresEditOpen(true);
  };

  const handleEditIndividual = (individualId: string, fullName: string) => {
    setEditIndividualId(individualId);
    setEditIndividualName(fullName);
    setIndEditOpen(true);
  };

  const handleEditStaff = (row: StaffRow) => {
    setEditStaffRow(row);
    setStaffPresidencyId(null);
    setStaffEditOpen(true);
  };

  const handleAddStaff = (presidencyId: string) => {
    setEditStaffRow(null); // null = create mode
    setStaffPresidencyId(presidencyId);
    setStaffEditOpen(true);
  };

  const handleDeleteStaff = (row: StaffRow) => {
    setDeleteStaffRow(row);
    setDeleteOpen(true);
  };

  return (
    <div className="container py-8">
      {/* Back link */}
      <div className="mb-4">
        <Button variant="ghost" size="sm" asChild className="-ml-2">
          <Link href="/admin/knowledge-base/government/executive">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Executive Branch
          </Link>
        </Button>
      </div>

      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-4">
          <div className="p-3 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
            <Landmark className="h-8 w-8" />
          </div>
          <h1 className="text-3xl font-bold">Manage Administrations</h1>
        </div>
        <p className="text-muted-foreground">
          Sync external data sources and manage presidential administration records.
        </p>
      </div>

      {/* Sync Section */}
      <section className="mb-10">
        <h2 className="text-xl font-semibold mb-4">Data Sync</h2>
        <AdministrationSyncSection />
      </section>

      {/* Data Management Section */}
      <section className="mb-10">
        <h2 className="text-xl font-semibold mb-4">Administration Data</h2>
        <AdministrationDataSection
          onEditPresidency={handleEditPresidency}
          onEditIndividual={handleEditIndividual}
          onEditStaff={handleEditStaff}
          onAddStaff={handleAddStaff}
          onDeleteStaff={handleDeleteStaff}
        />
      </section>

      {/* Modals */}
      <PresidencyEditModal
        presidency={editPresidency}
        open={presEditOpen}
        onOpenChange={setPresEditOpen}
      />

      <IndividualEditModal
        individualId={editIndividualId}
        fullName={editIndividualName}
        open={indEditOpen}
        onOpenChange={setIndEditOpen}
      />

      <PositionHoldingEditModal
        staffRow={editStaffRow}
        presidencyId={staffPresidencyId}
        open={staffEditOpen}
        onOpenChange={setStaffEditOpen}
      />

      <DeleteStaffDialog
        staffRow={deleteStaffRow}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
      />
    </div>
  );
}
