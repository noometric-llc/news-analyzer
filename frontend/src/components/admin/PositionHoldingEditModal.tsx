'use client';

/**
 * PositionHoldingEditModal (KB-2.4 Task 6)
 *
 * Create/Edit/Delete position holdings (VP, CoS, Cabinet members).
 * - Create mode: POST /api/admin/position-holdings (KB-2.5)
 * - Edit mode:   PUT  /api/admin/position-holdings/{id} (KB-2.5)
 * - Delete:      DELETE /api/admin/position-holdings/{id} (KB-2.5)
 */

import { useState, useEffect } from 'react';
import { Loader2, Trash2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useToast } from '@/hooks/use-toast';
import {
  useCreatePositionHolding,
  useUpdatePositionHolding,
  useDeletePositionHolding,
} from '@/hooks/useAdminPresidency';
import type { StaffRow } from './AdministrationDataSection';

interface PositionHoldingEditModalProps {
  /** null = create mode, StaffRow = edit mode */
  staffRow: StaffRow | null;
  /** required for create mode — the presidency we're adding staff to */
  presidencyId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function PositionHoldingEditModal({
  staffRow,
  presidencyId,
  open,
  onOpenChange,
}: PositionHoldingEditModalProps) {
  const { toast } = useToast();
  const createMutation = useCreatePositionHolding();
  const updateMutation = useUpdatePositionHolding();

  const isCreateMode = staffRow === null;

  const [individualId, setIndividualId] = useState('');
  const [positionId, setPositionId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  useEffect(() => {
    if (staffRow) {
      setIndividualId(staffRow.individualId);
      setPositionId(''); // not available in StaffRow
      setStartDate(staffRow.startDate);
      setEndDate(staffRow.endDate || '');
    } else {
      setIndividualId('');
      setPositionId('');
      setStartDate('');
      setEndDate('');
    }
  }, [staffRow, open]);

  const handleSave = async () => {
    try {
      if (isCreateMode) {
        if (!presidencyId || !individualId || !positionId || !startDate) {
          toast({
            title: 'Validation Error',
            description: 'Individual ID, Position ID, and Start Date are required.',
            variant: 'destructive',
          });
          return;
        }

        await createMutation.mutateAsync({
          individualId,
          positionId,
          presidencyId,
          startDate,
          endDate: endDate || null,
        });

        toast({
          title: 'Position Holding Created',
          description: 'New staff position created successfully.',
          variant: 'success',
        });
      } else {
        await updateMutation.mutateAsync({
          id: staffRow!.holdingId,
          data: {
            startDate: startDate || undefined,
            endDate: endDate || null,
          },
        });

        toast({
          title: 'Position Holding Updated',
          description: `${staffRow!.fullName}'s position updated successfully.`,
          variant: 'success',
        });
      }

      onOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      toast({
        title: isCreateMode ? 'Create Failed' : 'Update Failed',
        description: message,
        variant: 'destructive',
      });
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isCreateMode
              ? 'Add Staff Position'
              : `Edit Position — ${staffRow?.fullName}`}
          </DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          {isCreateMode && (
            <>
              <div className="grid gap-2">
                <Label htmlFor="ph-individual">Individual ID</Label>
                <Input
                  id="ph-individual"
                  value={individualId}
                  onChange={(e) => setIndividualId(e.target.value)}
                  placeholder="UUID of the individual"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="ph-position">Position ID</Label>
                <Input
                  id="ph-position"
                  value={positionId}
                  onChange={(e) => setPositionId(e.target.value)}
                  placeholder="UUID of the position"
                />
              </div>
            </>
          )}

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="ph-start">Start Date</Label>
              <Input
                id="ph-start"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="ph-end">End Date</Label>
              <Input
                id="ph-end"
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </div>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={isPending}>
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {isCreateMode ? 'Create' : 'Save Changes'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ============================================================================
// Delete Confirmation Dialog (AC7)
// ============================================================================

interface DeleteStaffDialogProps {
  staffRow: StaffRow | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function DeleteStaffDialog({
  staffRow,
  open,
  onOpenChange,
}: DeleteStaffDialogProps) {
  const { toast } = useToast();
  const deleteMutation = useDeletePositionHolding();

  const handleDelete = async () => {
    if (!staffRow) return;

    try {
      await deleteMutation.mutateAsync(staffRow.holdingId);

      toast({
        title: 'Position Holding Deleted',
        description: `${staffRow.fullName}'s ${staffRow.positionTitle} position has been removed.`,
        variant: 'success',
      });

      onOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      toast({
        title: 'Delete Failed',
        description: message,
        variant: 'destructive',
      });
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Position Holding?</AlertDialogTitle>
          <AlertDialogDescription>
            This will permanently remove{' '}
            <strong>{staffRow?.fullName}</strong>&apos;s{' '}
            <strong>{staffRow?.positionTitle}</strong> position record.
            This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleDelete}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            disabled={deleteMutation.isPending}
          >
            {deleteMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            <Trash2 className="mr-2 h-4 w-4" />
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
