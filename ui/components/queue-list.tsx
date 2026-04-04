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
      if (showLoading) setIsRefreshing(true);
      const data = await getQueues();
      setQueues(data);
      setError(null);
      setLastUpdate(new Date());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setIsLoading(false);
      if (showLoading) setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchQueues();
    const interval = setInterval(() => fetchQueues(), 1000);
    return () => clearInterval(interval);
  }, []);

  if (isLoading) return <LoadingSkeleton />;
  if (error) return <ErrorDisplay message={error} onRetry={() => fetchQueues(true)} />;

  if (queues.length === 0) {
    return (
      <div style={{
        padding: '48px 32px',
        borderRadius: 10,
        border: '1px dashed var(--card-border)',
        textAlign: 'center',
        animation: 'fadeUp 300ms ease both',
      }}>
        <div style={{ fontSize: 32, marginBottom: 12, opacity: 0.4 }}>⬡</div>
        <p style={{ fontWeight: 600, color: 'var(--foreground)', marginBottom: 6, fontSize: 15 }}>No queues found</p>
        <p style={{ color: 'var(--muted)', fontSize: 13, marginBottom: 20 }}>
          Create a queue to get started.
        </p>
        <code style={{
          display: 'inline-block',
          padding: '8px 14px',
          borderRadius: 6,
          background: 'var(--card-bg)',
          border: '1px solid var(--card-border)',
          color: 'var(--foreground)',
          fontFamily: 'var(--font-geist-mono), monospace',
          fontSize: 12,
        }}>
          aws --endpoint-url=http://localhost:9324 sqs create-queue --queue-name my-queue
        </code>
      </div>
    );
  }

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* Live dot */}
          <span style={{
            display: 'inline-block', width: 7, height: 7, borderRadius: '50%',
            background: 'var(--green)',
            boxShadow: '0 0 6px var(--green)',
            animation: 'pulse-dot 2s ease-in-out infinite',
          }} />
          <span style={{ fontSize: 12, color: 'var(--muted)', fontVariantNumeric: 'tabular-nums' }}>
            {queues.length} queue{queues.length !== 1 ? 's' : ''}
            {lastUpdate && ` · ${lastUpdate.toLocaleTimeString()}`}
          </span>
        </div>

        <button
          onClick={() => fetchQueues(true)}
          disabled={isRefreshing}
          style={{
            padding: '5px 14px',
            borderRadius: 6,
            border: '1px solid var(--card-border)',
            background: 'var(--card-bg)',
            color: 'var(--muted)',
            fontSize: 12,
            fontWeight: 500,
            cursor: isRefreshing ? 'not-allowed' : 'pointer',
            opacity: isRefreshing ? 0.5 : 1,
            transition: 'color 150ms, border-color 150ms',
          }}
          onMouseEnter={e => {
            if (!isRefreshing) {
              (e.currentTarget as HTMLButtonElement).style.color = 'var(--foreground)';
              (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--muted)';
            }
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLButtonElement).style.color = 'var(--muted)';
            (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--card-border)';
          }}
        >
          {isRefreshing ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </div>

      {/* Queue cards */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {queues.map((queue, i) => (
          <QueueCard key={queue.url} queue={queue} index={i} />
        ))}
      </div>
    </div>
  );
}
