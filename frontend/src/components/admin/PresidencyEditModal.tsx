'use client';

/**
 * PresidencyEditModal (KB-2.4 Task 4)
 *
 * Edit form for presidency fields: party, startDate, endDate, electionYear, endReason.
 * Uses PUT /api/admin/presidencies/{id} (KB-2.5).
 */

import { useState, useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useToast } from '@/hooks/use-toast';
import { useUpdatePresidency } from '@/hooks/useAdminPresidency';
import type { PresidencyDTO } from '@/hooks/usePresidencySync';

interface PresidencyEditModalProps {
  presidency: PresidencyDTO | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function PresidencyEditModal({
  presidency,
  open,
  onOpenChange,
}: PresidencyEditModalProps) {
  const { toast } = useToast();
  const mutation = useUpdatePresidency();

  const [party, setParty] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [electionYear, setElectionYear] = useState('');
  const [endReason, setEndReason] = useState('');

  // Populate form when presidency changes
  useEffect(() => {
    if (presidency) {
      setParty(presidency.party || '');
      setStartDate(presidency.startDate || '');
      setEndDate(presidency.endDate || '');
      setElectionYear(presidency.electionYear?.toString() || '');
      setEndReason(presidency.endReason || '');
    }
  }, [presidency]);

  const handleSave = async () => {
    if (!presidency) return;

    try {
      await mutation.mutateAsync({
        id: presidency.id,
        data: {
          party: party || undefined,
          startDate: startDate || undefined,
          endDate: endDate || null,
          electionYear: electionYear ? parseInt(electionYear, 10) : null,
          endReason: endReason || null,
        },
      });

      toast({
        title: 'Presidency Updated',
        description: `${presidency.ordinalLabel} presidency updated successfully.`,
        variant: 'success',
      });

      onOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      toast({
        title: 'Update Failed',
        description: message,
        variant: 'destructive',
      });
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            Edit Presidency — {presidency?.ordinalLabel}
          </DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="pres-party">Party</Label>
            <Input
              id="pres-party"
              value={party}
              onChange={(e) => setParty(e.target.value)}
              placeholder="e.g. Republican"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="pres-start">Start Date</Label>
              <Input
                id="pres-start"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="pres-end">End Date</Label>
              <Input
                id="pres-end"
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="pres-election">Election Year</Label>
              <Input
                id="pres-election"
                type="number"
                value={electionYear}
                onChange={(e) => setElectionYear(e.target.value)}
                placeholder="e.g. 2024"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="pres-reason">End Reason</Label>
              <Input
                id="pres-reason"
                value={endReason}
                onChange={(e) => setEndReason(e.target.value)}
                placeholder="e.g. Term ended"
              />
            </div>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={mutation.isPending}>
            {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Save Changes
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
