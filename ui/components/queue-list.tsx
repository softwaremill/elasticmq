'use client'

import { useState, useEffect } from 'react';

const autoRefreshDisabled = process.env.NEXT_PUBLIC_AUTO_REFRESH_DISABLED === 'true';
import { QueueData } from '@/lib/types';
import { getQueues } from '@/lib/actions';
import { QueueCard } from './queue-card';
import { LoadingSkeleton } from './loading-skeleton';
import { ErrorDisplay } from './error-display';
import { CreateQueueModal } from './create-queue-modal';
import { Hexagon, RefreshCw, ListPlus } from 'lucide-react';

export function QueueList() {
  const [queues, setQueues] = useState<QueueData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showCreate, setShowCreate] = useState(false);

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
    if (autoRefreshDisabled) return;
    const interval = setInterval(() => fetchQueues(), 1000);
    return () => clearInterval(interval);
  }, []);

  if (isLoading) return <LoadingSkeleton />;
  if (error) return <ErrorDisplay message={error} onRetry={() => fetchQueues(true)} />;

  if (queues.length === 0) {
    return (
      <>
        <div style={{
          padding: '48px 32px',
          borderRadius: 10,
          border: '1px dashed var(--card-border)',
          textAlign: 'center',
          animation: 'fadeUp 300ms ease both',
        }}>
          <div style={{ marginBottom: 12, opacity: 0.4, display: 'flex', justifyContent: 'center' }}><Hexagon size={32} /></div>
          <p style={{ fontWeight: 600, color: 'var(--foreground)', marginBottom: 6, fontSize: 15 }}>No queues found</p>
          <p style={{ color: 'var(--muted)', fontSize: 13, marginBottom: 20 }}>
            Create a queue to get started.
          </p>
          <button
            onClick={() => setShowCreate(true)}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              padding: '8px 20px', borderRadius: 7, fontSize: 13, fontWeight: 600, cursor: 'pointer',
              border: '1px solid var(--accent)',
              background: 'var(--accent-dim)',
              color: 'var(--accent)',
              transition: 'all 150ms ease',
            }}
            onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--accent)'; (e.currentTarget as HTMLButtonElement).style.color = '#fff'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--accent-dim)'; (e.currentTarget as HTMLButtonElement).style.color = 'var(--accent)'; }}
          >
            <ListPlus size={15} />Create Queue
          </button>
        </div>
        {showCreate && (
          <CreateQueueModal
            onClose={() => setShowCreate(false)}
            onCreated={() => { setShowCreate(false); fetchQueues(); }}
          />
        )}
      </>
    );
  }

  return (
    <>
      <div>
        {/* Toolbar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {/* Live/static dot */}
            <span style={{
              display: 'inline-block', width: 7, height: 7, borderRadius: '50%',
              background: autoRefreshDisabled ? 'var(--muted)' : 'var(--green)',
              boxShadow: autoRefreshDisabled ? 'none' : '0 0 6px var(--green)',
              animation: autoRefreshDisabled ? 'none' : 'pulse-dot 2s ease-in-out infinite',
            }} />
            <span style={{ fontSize: 12, color: 'var(--muted)', fontVariantNumeric: 'tabular-nums' }}>
              {queues.length} queue{queues.length !== 1 ? 's' : ''}
              {lastUpdate && ` · ${autoRefreshDisabled ? 'manual' : lastUpdate.toLocaleTimeString()}`}
            </span>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <button
              onClick={() => fetchQueues(true)}
              disabled={isRefreshing}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 5,
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
              {isRefreshing ? 'Refreshing…' : <><RefreshCw size={11} />Refresh</>}
            </button>
            <button
              onClick={() => setShowCreate(true)}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 5,
                padding: '5px 14px',
                borderRadius: 6,
                border: '1px solid var(--accent)',
                background: 'var(--accent-dim)',
                color: 'var(--accent)',
                fontSize: 12,
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 150ms ease',
              }}
              onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--accent)'; (e.currentTarget as HTMLButtonElement).style.color = '#fff'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--accent-dim)'; (e.currentTarget as HTMLButtonElement).style.color = 'var(--accent)'; }}
            >
              <ListPlus size={13} />Create Queue
            </button>
          </div>
        </div>

        {/* Queue cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {queues.map((queue, i) => (
            <QueueCard key={queue.url} queue={queue} index={i} />
          ))}
        </div>
      </div>
      {showCreate && (
        <CreateQueueModal
          onClose={() => setShowCreate(false)}
          onCreated={() => { setShowCreate(false); fetchQueues(); }}
        />
      )}
    </>
  );
}
