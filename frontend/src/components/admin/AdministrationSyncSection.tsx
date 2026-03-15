'use client';

/**
 * AdministrationSyncSection (KB-2.4 Task 2)
 *
 * Renders two sync cards side-by-side:
 * 1. PresidencySyncCard (existing) — syncs presidential data
 * 2. EOSyncCard (new) — syncs executive order data
 */

import { useState } from 'react';
import { Loader2, RefreshCw, ScrollText, AlertCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
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
import { PresidencySyncCard } from './PresidencySyncCard';
import { useEOSyncStatus, useEOSync } from '@/hooks/usePresidencySync';

export function AdministrationSyncSection() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <PresidencySyncCard />
      <EOSyncCard />
    </div>
  );
}

/**
 * EOSyncCard — mirrors PresidencySyncCard pattern for Executive Orders.
 */
function EOSyncCard() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const { toast } = useToast();
  const { data: status, isLoading, error, refetch } = useEOSyncStatus();
  const syncMutation = useEOSync();

  const handleSync = async () => {
    try {
      await syncMutation.mutateAsync();

      toast({
        title: 'Executive Order Sync Started',
        description: 'Sync is running in the background. Status will update automatically.',
        variant: 'success',
      });

      setDialogOpen(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error occurred';
      toast({
        title: 'Executive Order Sync Failed',
        description: message,
        variant: 'destructive',
      });
    }
  };

  const getStatusBadge = () => {
    if (error) {
      return (
        <Badge variant="outline" className="bg-red-100 text-red-800 border-red-200">
          Error
        </Badge>
      );
    }
    if (status?.inProgress) {
      return (
        <Badge variant="outline" className="bg-blue-100 text-blue-800 border-blue-200">
          <Loader2 className="mr-1 h-3 w-3 animate-spin" />
          Syncing
        </Badge>
      );
    }
    if (status?.eoCounts && Object.keys(status.eoCounts).length > 0) {
      return (
        <Badge variant="outline" className="bg-green-100 text-green-800 border-green-200">
          Ready
        </Badge>
      );
    }
    return (
      <Badge variant="outline" className="bg-gray-100 text-gray-600 border-gray-200">
        Never Synced
      </Badge>
    );
  };

  const totalEOs = status?.eoCounts
    ? Object.values(status.eoCounts).reduce((sum, count) => sum + count, 0)
    : 0;

  const presidenciesWithEOs = status?.eoCounts
    ? Object.keys(status.eoCounts).length
    : 0;

  if (isLoading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
              <ScrollText className="h-4 w-4" />
              Executive Orders
            </CardTitle>
            <Skeleton className="h-5 w-20" />
          </div>
        </CardHeader>
        <CardContent>
          <Skeleton className="h-8 w-32 mb-3" />
          <div className="space-y-2">
            <Skeleton className="h-4 w-36" />
            <Skeleton className="h-4 w-28" />
          </div>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
              <ScrollText className="h-4 w-4" />
              Executive Orders
            </CardTitle>
            {getStatusBadge()}
          </div>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-destructive mb-3">Failed to load status</p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            className="gap-2"
          >
            <RefreshCw className="h-4 w-4" />
            Retry
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
            <ScrollText className="h-4 w-4" />
            Executive Orders
          </CardTitle>
          {getStatusBadge()}
        </div>
      </CardHeader>
      <CardContent>
        {totalEOs > 0 ? (
          <div className="space-y-2 mb-4">
            <div className="flex items-center gap-2 text-sm">
              <ScrollText className="h-4 w-4 text-muted-foreground" />
              <span className="text-muted-foreground">Total EOs:</span>
              <span className="font-medium">{totalEOs.toLocaleString()}</span>
            </div>
            <div className="flex items-center gap-2 text-sm">
              <AlertCircle className="h-4 w-4 text-muted-foreground" />
              <span className="text-muted-foreground">Presidencies with EOs:</span>
              <span className="font-medium">{presidenciesWithEOs}</span>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground mb-4">
            No executive orders imported. Click below to sync EO data from the Federal Register.
          </p>
        )}

        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogTrigger asChild>
            <Button
              variant="outline"
              size="sm"
              disabled={status?.inProgress || syncMutation.isPending}
              className="w-full gap-2"
            >
              {(status?.inProgress || syncMutation.isPending) ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Syncing...
                </>
              ) : (
                <>
                  <RefreshCw className="h-4 w-4" />
                  Sync Executive Orders
                </>
              )}
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <span className="text-amber-500">&#9888;</span>
                Sync Executive Orders?
              </DialogTitle>
              <DialogDescription asChild>
                <div className="space-y-3 pt-2">
                  <p>
                    This will fetch executive order data from the Federal Register
                    for all presidencies.
                  </p>
                  <ul className="list-disc pl-5 space-y-1 text-sm">
                    <li>Fetches EO data from the Federal Register API</li>
                    <li>Creates/updates EO records for each presidency</li>
                    <li>May take several minutes for a full sync</li>
                    <li>Safe to run multiple times (idempotent)</li>
                  </ul>
                  <p className="font-medium">Are you sure you want to proceed?</p>
                </div>
              </DialogDescription>
            </DialogHeader>
            <DialogFooter className="gap-2 sm:gap-0">
              <Button variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button
                onClick={handleSync}
                disabled={syncMutation.isPending}
              >
                {syncMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Confirm Sync
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardContent>
    </Card>
  );
}
