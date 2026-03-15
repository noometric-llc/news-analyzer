'use client';

/**
 * AdministrationDataSection (KB-2.4 Task 3)
 *
 * Two tables:
 * 1. Presidencies table — all presidencies with [Edit] and [Staff] actions
 * 2. Staff table — VP, CoS, Cabinet filtered by selected presidency
 */

import { useState } from 'react';
import { Edit, Users, Plus, Trash2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  useAllPresidencies,
  usePresidencyAdministration,
} from '@/hooks/usePresidencySync';
import type {
  PresidencyDTO,
  OfficeholderDTO,
  CabinetMemberDTO,
} from '@/hooks/usePresidencySync';

// Staff row type — normalised shape for the staff table
export interface StaffRow {
  holdingId: string;
  individualId: string;
  fullName: string;
  positionTitle: string;
  startDate: string;
  endDate: string | null;
  category: 'Vice President' | 'Chief of Staff' | 'Cabinet';
}

interface AdministrationDataSectionProps {
  onEditPresidency: (presidency: PresidencyDTO) => void;
  onEditIndividual: (individualId: string, fullName: string) => void;
  onEditStaff: (row: StaffRow) => void;
  onAddStaff: (presidencyId: string) => void;
  onDeleteStaff: (row: StaffRow) => void;
}

export function AdministrationDataSection({
  onEditPresidency,
  onEditIndividual,
  onEditStaff,
  onAddStaff,
  onDeleteStaff,
}: AdministrationDataSectionProps) {
  const { data: presidencies, isLoading, error } = useAllPresidencies();
  const [selectedPresidencyId, setSelectedPresidencyId] = useState<string | null>(null);

  const selectedPresidency = presidencies?.find((p) => p.id === selectedPresidencyId) ?? null;

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-64 w-full rounded-lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-6 text-center">
        <p className="text-destructive font-medium">Failed to load presidencies</p>
        <p className="text-sm text-muted-foreground mt-1">
          {error instanceof Error ? error.message : 'An unexpected error occurred'}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Presidencies Table */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Presidencies</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left">
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">#</th>
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">President</th>
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">Party</th>
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">Term</th>
                  <th className="pb-2 font-medium text-muted-foreground text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {presidencies?.map((p) => (
                  <tr
                    key={p.id}
                    className={`border-b last:border-0 ${
                      p.id === selectedPresidencyId ? 'bg-primary/5' : ''
                    }`}
                  >
                    <td className="py-2 pr-4 font-medium">{p.number}</td>
                    <td className="py-2 pr-4">
                      <button
                        className="text-left hover:underline text-primary"
                        onClick={() => onEditIndividual(p.individualId, p.presidentFullName)}
                      >
                        {p.presidentFullName}
                      </button>
                      {p.current && (
                        <Badge variant="outline" className="ml-2 text-xs bg-green-100 text-green-800 border-green-200">
                          Current
                        </Badge>
                      )}
                    </td>
                    <td className="py-2 pr-4">{p.party}</td>
                    <td className="py-2 pr-4">{p.termLabel}</td>
                    <td className="py-2 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => onEditPresidency(p)}
                          title="Edit presidency"
                        >
                          <Edit className="h-4 w-4" />
                        </Button>
                        <Button
                          variant={p.id === selectedPresidencyId ? 'secondary' : 'ghost'}
                          size="sm"
                          onClick={() =>
                            setSelectedPresidencyId(
                              p.id === selectedPresidencyId ? null : p.id
                            )
                          }
                          title="View staff"
                        >
                          <Users className="h-4 w-4" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Staff Table (shown when a presidency is selected) */}
      {selectedPresidencyId && selectedPresidency && (
        <StaffTable
          presidencyId={selectedPresidencyId}
          presidencyLabel={`${selectedPresidency.ordinalLabel} — ${selectedPresidency.presidentFullName}`}
          onEditStaff={onEditStaff}
          onAddStaff={() => onAddStaff(selectedPresidencyId)}
          onDeleteStaff={onDeleteStaff}
        />
      )}
    </div>
  );
}

// ============================================================================
// Staff Table (internal)
// ============================================================================

interface StaffTableProps {
  presidencyId: string;
  presidencyLabel: string;
  onEditStaff: (row: StaffRow) => void;
  onAddStaff: () => void;
  onDeleteStaff: (row: StaffRow) => void;
}

function StaffTable({
  presidencyId,
  presidencyLabel,
  onEditStaff,
  onAddStaff,
  onDeleteStaff,
}: StaffTableProps) {
  const { data: admin, isLoading } = usePresidencyAdministration(presidencyId);

  if (isLoading) {
    return <Skeleton className="h-48 w-full rounded-lg" />;
  }

  // Normalise the different officeholder types into a flat list
  const rows: StaffRow[] = [];

  admin?.vicePresidents.forEach((vp: OfficeholderDTO) => {
    rows.push({
      holdingId: vp.holdingId,
      individualId: vp.individualId,
      fullName: vp.fullName,
      positionTitle: vp.positionTitle || 'Vice President',
      startDate: vp.startDate,
      endDate: vp.endDate,
      category: 'Vice President',
    });
  });

  admin?.chiefsOfStaff.forEach((cos: OfficeholderDTO) => {
    rows.push({
      holdingId: cos.holdingId,
      individualId: cos.individualId,
      fullName: cos.fullName,
      positionTitle: cos.positionTitle || 'Chief of Staff',
      startDate: cos.startDate,
      endDate: cos.endDate,
      category: 'Chief of Staff',
    });
  });

  admin?.cabinetMembers.forEach((cm: CabinetMemberDTO) => {
    rows.push({
      holdingId: cm.holdingId,
      individualId: cm.individualId,
      fullName: cm.fullName,
      positionTitle: `${cm.positionTitle} — ${cm.departmentName}`,
      startDate: cm.startDate,
      endDate: cm.endDate,
      category: 'Cabinet',
    });
  });

  const categoryBadgeColor = (cat: StaffRow['category']) => {
    switch (cat) {
      case 'Vice President':
        return 'bg-indigo-100 text-indigo-800 border-indigo-200';
      case 'Chief of Staff':
        return 'bg-amber-100 text-amber-800 border-amber-200';
      case 'Cabinet':
        return 'bg-blue-100 text-blue-800 border-blue-200';
    }
  };

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">
            Staff — {presidencyLabel}
          </CardTitle>
          <Button variant="outline" size="sm" onClick={onAddStaff} className="gap-1">
            <Plus className="h-4 w-4" />
            Add Staff
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            No staff records found for this administration.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left">
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">Name</th>
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">Position</th>
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">Category</th>
                  <th className="pb-2 pr-4 font-medium text-muted-foreground">Term</th>
                  <th className="pb-2 font-medium text-muted-foreground text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.holdingId} className="border-b last:border-0">
                    <td className="py-2 pr-4">{row.fullName}</td>
                    <td className="py-2 pr-4 text-muted-foreground">{row.positionTitle}</td>
                    <td className="py-2 pr-4">
                      <Badge variant="outline" className={`text-xs ${categoryBadgeColor(row.category)}`}>
                        {row.category}
                      </Badge>
                    </td>
                    <td className="py-2 pr-4 text-muted-foreground">
                      {row.startDate} — {row.endDate ?? 'present'}
                    </td>
                    <td className="py-2 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => onEditStaff(row)}
                          title="Edit position holding"
                        >
                          <Edit className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => onDeleteStaff(row)}
                          title="Delete position holding"
                          className="text-destructive hover:text-destructive"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
