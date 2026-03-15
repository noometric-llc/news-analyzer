'use client';

/**
 * IndividualEditModal (KB-2.4 Task 5)
 *
 * Edit form for individual (person) fields: firstName, lastName, birthDate,
 * deathDate, birthPlace, imageUrl.
 * Uses PUT /api/admin/individuals/{id} (KB-2.5).
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
import { useUpdateIndividual } from '@/hooks/useAdminPresidency';

interface IndividualEditModalProps {
  individualId: string | null;
  fullName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function IndividualEditModal({
  individualId,
  fullName,
  open,
  onOpenChange,
}: IndividualEditModalProps) {
  const { toast } = useToast();
  const mutation = useUpdateIndividual();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [deathDate, setDeathDate] = useState('');
  const [birthPlace, setBirthPlace] = useState('');
  const [imageUrl, setImageUrl] = useState('');

  // Parse first/last from fullName as initial values
  useEffect(() => {
    if (fullName) {
      const parts = fullName.split(' ');
      setFirstName(parts[0] || '');
      setLastName(parts.slice(1).join(' ') || '');
    }
    // Reset other fields since we don't have them from the table
    setBirthDate('');
    setDeathDate('');
    setBirthPlace('');
    setImageUrl('');
  }, [fullName, individualId]);

  const handleSave = async () => {
    if (!individualId) return;

    try {
      await mutation.mutateAsync({
        id: individualId,
        data: {
          firstName: firstName || undefined,
          lastName: lastName || undefined,
          birthDate: birthDate || null,
          deathDate: deathDate || null,
          birthPlace: birthPlace || null,
          imageUrl: imageUrl || null,
        },
      });

      toast({
        title: 'Individual Updated',
        description: `${firstName} ${lastName} updated successfully.`,
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
          <DialogTitle>Edit Individual — {fullName}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="ind-first">First Name</Label>
              <Input
                id="ind-first"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="ind-last">Last Name</Label>
              <Input
                id="ind-last"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="ind-birth">Birth Date</Label>
              <Input
                id="ind-birth"
                type="date"
                value={birthDate}
                onChange={(e) => setBirthDate(e.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="ind-death">Death Date</Label>
              <Input
                id="ind-death"
                type="date"
                value={deathDate}
                onChange={(e) => setDeathDate(e.target.value)}
              />
            </div>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="ind-birthplace">Birth Place</Label>
            <Input
              id="ind-birthplace"
              value={birthPlace}
              onChange={(e) => setBirthPlace(e.target.value)}
              placeholder="e.g. Queens, New York"
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="ind-image">Image URL</Label>
            <Input
              id="ind-image"
              value={imageUrl}
              onChange={(e) => setImageUrl(e.target.value)}
              placeholder="https://..."
            />
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
