'use client'

import { useState, useEffect } from 'react';
import { QueueData } from '@/lib/types';
import { getQueues } from '@/lib/actions';
import { QueueCard } from './queue-card';
import { LoadingSkeleton } from './loading-skeleton';
import { ErrorDisplay } from './error-display';

export function QueueList() {
  const [queues, setQueues] = useState<QueueData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const fetchQueues = async (showLoading = false) => {
    try {
      if (showLoading) {
        setIsRefreshing(true);
      }
      const data = await getQueues();
      setQueues(data);
      setError(null);
      setLastUpdate(new Date());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setIsLoading(false);
      if (showLoading) {
        setIsRefreshing(false);
      }
    }
  };

  useEffect(() => {
    fetchQueues();
    const interval = setInterval(() => fetchQueues(), 1000);
    return () => clearInterval(interval);
  }, []);

  const handleManualRefresh = () => {
    fetchQueues(true);
  };

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  if (error) {
    return <ErrorDisplay message={error} onRetry={handleManualRefresh} />;
  }

  if (queues.length === 0) {
    return (
      <div
        className="p-8 rounded-lg border text-center"
        style={{
          backgroundColor: 'var(--card-bg)',
          borderColor: 'var(--card-border)',
        }}
      >
        <h3 className="text-lg font-semibold mb-2" style={{ color: 'var(--foreground)' }}>
          No queues found
        </h3>
        <p className="mb-4" style={{ color: 'var(--muted)' }}>
          Create a queue using the AWS CLI or SDK to get started.
        </p>
        <code
          className="block p-4 rounded text-sm text-left"
          style={{
            backgroundColor: 'var(--background)',
            color: 'var(--foreground)',
          }}
        >
          aws --endpoint-url=http://localhost:9324 sqs create-queue --queue-name my-queue
        </code>
      </div>
    );
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          {lastUpdate && (
            <p className="text-sm" style={{ color: 'var(--muted)' }}>
              Last updated: {lastUpdate.toLocaleTimeString()}
            </p>
          )}
        </div>
        <button
          onClick={handleManualRefresh}
          disabled={isRefreshing}
          className="px-4 py-2 rounded font-medium transition-colors disabled:opacity-50"
          style={{
            backgroundColor: 'var(--accent)',
            color: 'white',
          }}
        >
          {isRefreshing ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>
      <div className="space-y-4">
        {queues.map((queue) => (
          <QueueCard key={queue.url} queue={queue} />
        ))}
      </div>
    </div>
  );
}
