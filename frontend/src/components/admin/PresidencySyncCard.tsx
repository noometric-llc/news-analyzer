'use client';

/**
 * Presidency Sync Card Component
 *
 * Displays presidential data sync status and provides
 * a button to trigger synchronization from seed data.
 */

import { useState } from 'react';
import { Loader2, RefreshCw, Crown, Users, AlertCircle } from 'lucide-react';
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
import { usePresidencySyncStatus, usePresidencySync } from '@/hooks/usePresidencySync';

export function PresidencySyncCard() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const { toast } = useToast();
  const { data: status, isLoading, error, refetch } = usePresidencySyncStatus();
  const syncMutation = usePresidencySync();

  const handleSync = async () => {
    try {
      await syncMutation.mutateAsync();

      toast({
        title: 'Presidential Sync Started',
        description: 'Sync is running in the background. Status will update automatically.',
        variant: 'success',
      });

      setDialogOpen(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error occurred';
      toast({
        title: 'Presidential Sync Failed',
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
    if (status?.totalPresidencies && status.totalPresidencies > 0) {
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

  if (isLoading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
              <Crown className="h-4 w-4" />
              Presidential Data
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
              <Crown className="h-4 w-4" />
              Presidential Data
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

  const lastSync = status?.lastSync;

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
            <Crown className="h-4 w-4" />
            Presidential Data
          </CardTitle>
          {getStatusBadge()}
        </div>
      </CardHeader>
      <CardContent>
        {status?.totalPresidencies && status.totalPresidencies > 0 ? (
          <>
            <div className="space-y-2 mb-4">
              <div className="flex items-center gap-2 text-sm">
                <Crown className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground">Presidencies:</span>
                <span className="font-medium">{status.totalPresidencies}</span>
              </div>
              {lastSync && (
                <>
                  <div className="flex items-center gap-2 text-sm">
                    <Users className="h-4 w-4 text-muted-foreground" />
                    <span className="text-muted-foreground">Persons:</span>
                    <span className="font-medium">
                      {lastSync.personsAdded + lastSync.personsUpdated}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 text-sm">
                    <Users className="h-4 w-4 text-muted-foreground" />
                    <span className="text-muted-foreground">VP Holdings:</span>
                    <span className="font-medium">{lastSync.vpHoldingsAdded}</span>
                  </div>
                  {lastSync.errors > 0 && (
                    <div className="flex items-center gap-2 text-sm">
                      <AlertCircle className="h-4 w-4 text-amber-500" />
                      <span className="text-muted-foreground">Errors:</span>
                      <span className="font-medium text-amber-600">{lastSync.errors}</span>
                    </div>
                  )}
                </>
              )}
            </div>
          </>
        ) : (
          <p className="text-sm text-muted-foreground mb-4">
            No presidential data imported. Click below to sync all 47 U.S. presidencies.
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
                  Sync Presidential Data
                </>
              )}
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <span className="text-amber-500">&#9888;</span>
                Sync Presidential Data?
              </DialogTitle>
              <DialogDescription asChild>
                <div className="space-y-3 pt-2">
                  <p>
                    This will import all 47 U.S. presidencies from the seed data file,
                    including president and vice president records.
                  </p>
                  <ul className="list-disc pl-5 space-y-1 text-sm">
                    <li>Creates/updates Person records for all presidents and VPs</li>
                    <li>Creates/updates Presidency records (1-47)</li>
                    <li>Links VP PositionHolding records to each presidency</li>
                    <li>Handles non-consecutive terms (Cleveland 22/24, Trump 45/47)</li>
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
