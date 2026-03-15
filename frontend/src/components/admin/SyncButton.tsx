'use client';

/**
 * Sync Button Component
 *
 * Button with confirmation dialog for triggering async data synchronization.
 * POST fires async (returns 202 + jobId), then polls for completion via useSyncJob.
 */

import { useState, useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { useToast } from '@/hooks/use-toast';
import { useQueryClient } from '@tanstack/react-query';
import { useMemberSync, useEnrichmentSync, memberKeys } from '@/hooks/useMembers';
import { useCommitteeSync, useMembershipSync, committeeKeys } from '@/hooks/useCommittees';
import { useGovernmentOrgSync, govOrgKeys } from '@/hooks/useGovernmentOrgs';
import { useSyncJob } from '@/hooks/useSyncJob';
import type { SyncJobStatus } from '@/types/sync';

type SyncType = 'members' | 'committees' | 'memberships' | 'enrichment' | 'gov-orgs';

interface SyncButtonProps {
  type: SyncType;
  title: string;
  description: string;
  warning: string;
}

/** Map sync types to the query keys that should be invalidated on completion */
const invalidationKeys: Record<SyncType, readonly string[]> = {
  'members': memberKeys.all,
  'committees': committeeKeys.all,
  'memberships': committeeKeys.all,
  'enrichment': memberKeys.all,
  'gov-orgs': govOrgKeys.all,
};

export function SyncButton({ type, title, description, warning }: SyncButtonProps) {
  const [open, setOpen] = useState(false);
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const memberSync = useMemberSync();
  const committeeSync = useCommitteeSync();
  const membershipSync = useMembershipSync();
  const enrichmentSync = useEnrichmentSync();
  const govOrgSync = useGovernmentOrgSync();

  // Poll for job status while a sync is active
  const { data: job } = useSyncJob(activeJobId);

  const isMutating = memberSync.isPending || committeeSync.isPending ||
    membershipSync.isPending || enrichmentSync.isPending || govOrgSync.isPending;
  const isRunning = !!activeJobId && job?.state === 'RUNNING';
  const isLoading = isMutating || isRunning;

  // Watch job state for completion/failure
  useEffect(() => {
    if (!job || !activeJobId) return;

    if (job.state === 'COMPLETED') {
      toast({
        title: 'Sync completed',
        description: `${title} sync finished successfully.`,
        variant: 'success',
      });
      // Invalidate relevant queries so UI refreshes with new data
      queryClient.invalidateQueries({ queryKey: [...invalidationKeys[type]] });
      if (type === 'gov-orgs') {
        queryClient.invalidateQueries({ queryKey: govOrgKeys.syncStatus() });
      }
      setActiveJobId(null);
    } else if (job.state === 'FAILED') {
      toast({
        title: 'Sync failed',
        description: job.errorMessage || `${title} sync encountered an error.`,
        variant: 'destructive',
      });
      setActiveJobId(null);
    }
  }, [job?.state]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSync = async () => {
    try {
      let result: SyncJobStatus;

      switch (type) {
        case 'members':
          result = await memberSync.mutateAsync();
          break;
        case 'committees':
          result = await committeeSync.mutateAsync();
          break;
        case 'memberships':
          result = await membershipSync.mutateAsync(118);
          break;
        case 'enrichment':
          result = await enrichmentSync.mutateAsync(false);
          break;
        case 'gov-orgs':
          result = await govOrgSync.mutateAsync();
          break;
      }

      // Store the job ID to start polling
      setActiveJobId(result.jobId);

      toast({
        title: 'Sync started',
        description: `${title} sync has been initiated. You can close this dialog.`,
        variant: 'success',
      });
      setOpen(false);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error occurred';
      toast({
        title: 'Sync failed to start',
        description: errorMessage,
        variant: 'destructive',
      });
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {isRunning ? `${title} (running...)` : title}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <span className="text-amber-500">&#9888;</span>
            {title}?
          </DialogTitle>
          <DialogDescription asChild>
            <div className="space-y-3 pt-2 text-sm text-muted-foreground">
              <p>{description}</p>
              <ul className="list-disc pl-5 space-y-1">
                {warning.split('.').filter(Boolean).map((point, i) => (
                  <li key={i}>{point.trim()}</li>
                ))}
              </ul>
              <p className="font-medium">Are you sure you want to proceed?</p>
            </div>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleSync} disabled={isLoading}>
            {isMutating && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Confirm Sync
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
