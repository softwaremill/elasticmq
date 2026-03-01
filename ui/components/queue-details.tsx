'use client'

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { QueueData } from '@/lib/types';
import { getQueueDetails } from '@/lib/actions';
import { LoadingSkeleton } from './loading-skeleton';
import { ErrorDisplay } from './error-display';
import { formatAttributeValue, HIDDEN_ATTRIBUTES } from '@/lib/utils';
import { ThemeSwitcher } from './theme-switcher';
import { SendMessageModal } from './send-message-modal';

interface QueueDetailsProps {
  queueName: string;
}

export function QueueDetails({ queueName }: QueueDetailsProps) {
  const router = useRouter();
  const [queue, setQueue] = useState<QueueData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showModal, setShowModal] = useState(false);

  const fetchQueueDetails = async (showLoading = false) => {
    try {
      if (showLoading) {
        setIsRefreshing(true);
      }
      const data = await getQueueDetails(queueName);
      if (!data) {
        setError('Queue not found');
        return;
      }
      setQueue(data);
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
    fetchQueueDetails();
    const interval = setInterval(() => fetchQueueDetails(), 1000);
    return () => clearInterval(interval);
  }, [queueName]);

  const handleManualRefresh = () => {
    fetchQueueDetails(true);
  };

  if (isLoading) {
    return (
      <div>
        <button
          onClick={() => router.push('/')}
          className="mb-6 px-4 py-2 rounded font-medium transition-colors"
          style={{
            backgroundColor: 'var(--card-bg)',
            borderColor: 'var(--card-border)',
            color: 'var(--foreground)',
          }}
        >
          ← Back to Queues
        </button>
        <LoadingSkeleton />
      </div>
    );
  }

  if (error || !queue) {
    return (
      <div>
        <button
          onClick={() => router.push('/')}
          className="mb-6 px-4 py-2 rounded font-medium transition-colors"
          style={{
            backgroundColor: 'var(--card-bg)',
            borderColor: 'var(--card-border)',
            color: 'var(--foreground)',
          }}
        >
          ← Back to Queues
        </button>
        <ErrorDisplay message={error || 'Queue not found'} onRetry={handleManualRefresh} />
      </div>
    );
  }

  const visibleAttributes = Object.entries(queue.attributes).filter(
    ([key]) => !HIDDEN_ATTRIBUTES.includes(key)
  );

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <button
          onClick={() => router.push('/')}
          className="px-4 py-2 rounded font-medium transition-colors"
          style={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--foreground)' }}
        >
          ← Back to Queues
        </button>
        <ThemeSwitcher />
      </div>

      <div className="flex items-end justify-between gap-4 mb-6 flex-wrap">
        <div>
          <h1 className="text-3xl font-bold" style={{ color: 'var(--foreground)' }}>
            {queue.name}
          </h1>
          {lastUpdate && (
            <p className="text-sm mt-1" style={{ color: 'var(--muted)' }}>
              Last updated: {lastUpdate.toLocaleTimeString()}
            </p>
          )}
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={handleManualRefresh}
            disabled={isRefreshing}
            className="px-4 py-2 rounded font-medium transition-colors disabled:opacity-50"
            style={{ backgroundColor: 'var(--card-bg)', color: 'var(--foreground)', border: '1px solid var(--card-border)' }}
          >
            {isRefreshing ? 'Refreshing...' : 'Refresh'}
          </button>
          <button
            onClick={() => setShowModal(true)}
            className="px-4 py-2 rounded font-semibold transition-opacity hover:opacity-80"
            style={{ backgroundColor: 'var(--accent)', color: 'white' }}
          >
            Send Message
          </button>
        </div>
      </div>

      {/* Statistics */}
      <div
        className="p-6 rounded-lg border mb-6"
        style={{
          backgroundColor: 'var(--card-bg)',
          borderColor: 'var(--card-border)',
        }}
      >
        <h2 className="text-xl font-semibold mb-4" style={{ color: 'var(--foreground)' }}>
          Statistics
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div>
            <p className="text-sm mb-2" style={{ color: 'var(--muted)' }}>
              Messages Available
            </p>
            <p className="text-3xl font-bold" style={{ color: 'var(--foreground)' }}>
              {queue.stats.approximateNumberOfMessages}
            </p>
          </div>
          <div>
            <p className="text-sm mb-2" style={{ color: 'var(--muted)' }}>
              Messages Delayed
            </p>
            <p className="text-3xl font-bold" style={{ color: 'var(--foreground)' }}>
              {queue.stats.approximateNumberOfMessagesDelayed}
            </p>
          </div>
          <div>
            <p className="text-sm mb-2" style={{ color: 'var(--muted)' }}>
              Messages In Flight
            </p>
            <p className="text-3xl font-bold" style={{ color: 'var(--foreground)' }}>
              {queue.stats.approximateNumberOfMessagesNotVisible}
            </p>
          </div>
        </div>
      </div>

      {/* Attributes */}
      <div
        className="p-6 rounded-lg border"
        style={{
          backgroundColor: 'var(--card-bg)',
          borderColor: 'var(--card-border)',
        }}
      >
        <h2 className="text-xl font-semibold mb-4" style={{ color: 'var(--foreground)' }}>
          Queue Attributes
        </h2>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr style={{ borderBottom: '1px solid var(--card-border)' }}>
                <th
                  className="text-left py-3 px-4 font-semibold"
                  style={{ color: 'var(--foreground)' }}
                >
                  Attribute
                </th>
                <th
                  className="text-left py-3 px-4 font-semibold"
                  style={{ color: 'var(--foreground)' }}
                >
                  Value
                </th>
              </tr>
            </thead>
            <tbody>
              {visibleAttributes.map(([key, value]) => (
                <tr
                  key={key}
                  style={{ borderBottom: '1px solid var(--card-border)' }}
                >
                  <td className="py-3 px-4 font-mono text-sm" style={{ color: 'var(--muted)' }}>
                    {key}
                  </td>
                  <td className="py-3 px-4 font-mono text-sm" style={{ color: 'var(--foreground)' }}>
                    {formatAttributeValue(key, value)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {showModal && queue && (
        <SendMessageModal
          queueName={queue.name}
          queueUrl={queue.url}
          onClose={() => setShowModal(false)}
        />
      )}
    </div>
  );
}
