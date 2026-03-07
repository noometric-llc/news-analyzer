'use client';

/**
 * PLUM Sync Card Component
 *
 * Displays PLUM (executive branch appointees) sync status and provides
 * a button to trigger synchronization from OPM's PLUM CSV.
 */

import { useState } from 'react';
import { Loader2, RefreshCw, Building2, Users, Briefcase, AlertCircle } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
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
import { usePlumSyncStatus, usePlumSync } from '@/hooks/usePlumSync';

export function PlumSyncCard() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const { toast } = useToast();
  const { data: status, isLoading, error, refetch } = usePlumSyncStatus();
  const syncMutation = usePlumSync();

  const handleSync = async () => {
    try {
      await syncMutation.mutateAsync();

      toast({
        title: 'PLUM Sync Started',
        description: 'Import is running in the background. Status will update automatically.',
        variant: 'success',
      });

      setDialogOpen(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error occurred';
      toast({
        title: 'PLUM Sync Failed',
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
    if (status?.lastImport) {
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

  const formatDuration = (seconds: number) => {
    if (seconds < 60) return `${seconds}s`;
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}m ${secs}s`;
  };

  const formatRelativeTime = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins} minute${diffMins !== 1 ? 's' : ''} ago`;
    if (diffHours < 24) return `${diffHours} hour${diffHours !== 1 ? 's' : ''} ago`;
    return `${diffDays} day${diffDays !== 1 ? 's' : ''} ago`;
  };

  if (isLoading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
              <Building2 className="h-4 w-4" />
              Executive Appointees (PLUM)
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
              <Building2 className="h-4 w-4" />
              Executive Appointees (PLUM)
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

  const lastImport = status?.lastImport;

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
            <Building2 className="h-4 w-4" />
            Executive Appointees (PLUM)
          </CardTitle>
          {getStatusBadge()}
        </div>
      </CardHeader>
      <CardContent>
        {lastImport ? (
          <>
            <div className="space-y-2 mb-4">
              <div className="flex items-center gap-2 text-sm">
                <Users className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground">Persons:</span>
                <span className="font-medium">
                  {(lastImport.personsCreated + lastImport.personsUpdated).toLocaleString()}
                </span>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <Briefcase className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground">Positions:</span>
                <span className="font-medium">
                  {(lastImport.positionsCreated + lastImport.positionsUpdated).toLocaleString()}
                </span>
              </div>
              {lastImport.errors > 0 && (
                <div className="flex items-center gap-2 text-sm">
                  <AlertCircle className="h-4 w-4 text-amber-500" />
                  <span className="text-muted-foreground">Errors:</span>
                  <span className="font-medium text-amber-600">{lastImport.errors}</span>
                </div>
              )}
            </div>
            <p className="text-xs text-muted-foreground mb-3">
              Last sync: {formatRelativeTime(lastImport.endTime)} ({formatDuration(lastImport.durationSeconds)})
            </p>
          </>
        ) : (
          <p className="text-sm text-muted-foreground mb-4">
            No previous sync. Click below to import executive branch appointee data.
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
                  Sync PLUM Data
                </>
              )}
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <span className="text-amber-500">&#9888;</span>
                Sync PLUM Data?
              </DialogTitle>
              <DialogDescription className="space-y-3 pt-2">
                <p>
                  This will download and import executive branch appointee data from OPM&apos;s
                  PLUM (Policy and Supporting Positions) CSV file.
                </p>
                <ul className="list-disc pl-5 space-y-1 text-sm">
                  <li>Downloads ~21,000 records from OPM website</li>
                  <li>Creates/updates Person, Position, and Holding records</li>
                  <li>Estimated duration: 2-5 minutes</li>
                  <li>Safe to run multiple times (idempotent)</li>
                </ul>
                <p className="font-medium">Are you sure you want to proceed?</p>
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
