'use client'

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { QueueData } from '@/lib/types';
import { getQueueDetails } from '@/lib/actions';
import { LoadingSkeleton } from './loading-skeleton';
import { ErrorDisplay } from './error-display';
import { formatAttributeValue, HIDDEN_ATTRIBUTES } from '@/lib/utils';
import { ThemeSwitcher } from './theme-switcher';
import { SendMessageModal } from './send-message-modal';
import { ReceiveMessagesPanel } from './receive-messages-panel';
import { GenerateMessagesModal } from './generate-messages-modal';

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
  const [showReceive, setShowReceive] = useState(false);
  const [showGenerate, setShowGenerate] = useState(false);

  const fetchQueueDetails = useCallback(async (showLoading = false) => {
    try {
      if (showLoading) setIsRefreshing(true);
      const data = await getQueueDetails(queueName);
      if (!data) { setError('Queue not found'); return; }
      setQueue(data);
      setError(null);
      setLastUpdate(new Date());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setIsLoading(false);
      if (showLoading) setIsRefreshing(false);
    }
  }, [queueName]);

  useEffect(() => {
    fetchQueueDetails();
    const interval = setInterval(() => fetchQueueDetails(), 1000);
    return () => clearInterval(interval);
  }, [fetchQueueDetails]);

  const navBar = (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28 }}>
      <button
        onClick={() => router.push('/')}
        style={{
          display: 'flex', alignItems: 'center', gap: 6,
          padding: '5px 12px', borderRadius: 6,
          border: '1px solid var(--card-border)',
          background: 'var(--card-bg)',
          color: 'var(--muted)',
          fontSize: 13, fontWeight: 500, cursor: 'pointer',
          transition: 'color 150ms, border-color 150ms',
        }}
        onMouseEnter={e => {
          (e.currentTarget as HTMLButtonElement).style.color = 'var(--foreground)';
          (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--muted)';
        }}
        onMouseLeave={e => {
          (e.currentTarget as HTMLButtonElement).style.color = 'var(--muted)';
          (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--card-border)';
        }}
      >
        ← Queues
      </button>
      <ThemeSwitcher />
    </div>
  );

  if (isLoading) return <div>{navBar}<LoadingSkeleton /></div>;
  if (error || !queue) return <div>{navBar}<ErrorDisplay message={error || 'Queue not found'} onRetry={() => fetchQueueDetails(true)} /></div>;

  const visibleAttributes = Object.entries(queue.attributes).filter(
    ([key]) => !HIDDEN_ATTRIBUTES.includes(key)
  );

  return (
    <>
    <div style={{ animation: 'fadeUp 300ms ease both' }}>
      {navBar}

      {/* Queue name + action buttons */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, marginBottom: 28, flexWrap: 'wrap' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 5 }}>
            <div style={{
              width: 4, height: 22, borderRadius: 2,
              background: 'var(--accent)',
              boxShadow: '0 0 8px var(--accent)',
            }} />
            <h1 style={{
              fontFamily: 'var(--font-geist-mono), monospace',
              fontSize: 22,
              fontWeight: 700,
              color: 'var(--foreground)',
              letterSpacing: '-0.02em',
              margin: 0,
            }}>
              {queue.name}
            </h1>
          </div>
          {lastUpdate && (
            <p style={{ fontSize: 11, color: 'var(--muted)', marginLeft: 12, letterSpacing: '0.02em' }}>
              <span style={{
                display: 'inline-block', width: 6, height: 6, borderRadius: '50%',
                background: 'var(--green)',
                marginRight: 5, verticalAlign: 'middle',
                boxShadow: '0 0 4px var(--green)',
                animation: 'pulse-dot 2s ease-in-out infinite',
              }} />
              Live · {lastUpdate.toLocaleTimeString()}
            </p>
          )}
        </div>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <ActionBtn onClick={() => fetchQueueDetails(true)} disabled={isRefreshing} variant="ghost">
            {isRefreshing ? 'Refreshing…' : '↻ Refresh'}
          </ActionBtn>
          <ActionBtn onClick={() => setShowReceive(true)} variant="ghost">
            ↓ Receive
          </ActionBtn>
          <ActionBtn onClick={() => setShowGenerate(true)} variant="ghost">
            ⚡ Generate
          </ActionBtn>
          <ActionBtn onClick={() => setShowModal(true)} variant="primary">
            ↑ Send Message
          </ActionBtn>
        </div>
      </div>

      {/* Stats */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginBottom: 14,
      }}>
        <StatCard label="Available"  value={queue.stats.approximateNumberOfMessages}          accent />
        <StatCard label="Delayed"    value={queue.stats.approximateNumberOfMessagesDelayed}   />
        <StatCard label="In Flight"  value={queue.stats.approximateNumberOfMessagesNotVisible} />
      </div>

      {/* Attributes table */}
      <div style={{
        borderRadius: 10,
        border: '1px solid var(--card-border)',
        background: 'var(--card-bg)',
        overflow: 'hidden',
      }}>
        <div style={{
          padding: '12px 20px',
          borderBottom: '1px solid var(--card-border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <span style={{
            fontSize: 11, fontWeight: 700, letterSpacing: '0.08em',
            textTransform: 'uppercase', color: 'var(--muted)',
          }}>
            Queue Attributes
          </span>
          <span style={{
            fontSize: 11, color: 'var(--muted)',
            fontFamily: 'var(--font-geist-mono), monospace',
          }}>
            {visibleAttributes.length} fields
          </span>
        </div>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <tbody>
            {visibleAttributes.map(([key, value], i) => (
              <tr
                key={key}
                style={{
                  borderBottom: i < visibleAttributes.length - 1 ? '1px solid var(--card-border)' : 'none',
                }}
              >
                <td style={{
                  padding: '9px 20px',
                  fontFamily: 'var(--font-geist-mono), monospace',
                  fontSize: 12,
                  color: 'var(--muted)',
                  width: '45%',
                  whiteSpace: 'nowrap',
                }}>
                  {key}
                </td>
                <td style={{
                  padding: '9px 20px',
                  fontFamily: 'var(--font-geist-mono), monospace',
                  fontSize: 12,
                  color: 'var(--foreground)',
                  wordBreak: 'break-word',
                }}>
                  {formatAttributeValue(key, value)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

    </div>

      {showModal && (
        <SendMessageModal queueName={queue.name} queueUrl={queue.url} onClose={() => setShowModal(false)} />
      )}
      {showReceive && (
        <ReceiveMessagesPanel queueName={queue.name} queueUrl={queue.url} onClose={() => setShowReceive(false)} />
      )}
      {showGenerate && (
        <GenerateMessagesModal queueName={queue.name} queueUrl={queue.url} onClose={() => setShowGenerate(false)} />
      )}
    </>
  );
}

function StatCard({ label, value, accent }: { label: string; value: number; accent?: boolean }) {
  return (
    <div style={{
      borderRadius: 10,
      border: '1px solid var(--card-border)',
      background: 'var(--card-bg)',
      padding: '16px 20px',
    }}>
      <div style={{
        fontSize: 10, fontWeight: 700, letterSpacing: '0.09em',
        textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8,
      }}>
        {label}
      </div>
      <div style={{
        fontFamily: 'var(--font-geist-mono), monospace',
        fontSize: 36,
        fontWeight: 700,
        fontVariantNumeric: 'tabular-nums',
        letterSpacing: '-0.04em',
        lineHeight: 1,
        color: accent && value > 0 ? 'var(--accent)' : 'var(--foreground)',
      }}>
        {value}
      </div>
    </div>
  );
}

function ActionBtn({ children, onClick, disabled, variant }: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  variant: 'primary' | 'ghost';
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        padding: '7px 16px',
        borderRadius: 7,
        fontSize: 13,
        fontWeight: 600,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition: 'all 150ms ease',
        border: variant === 'primary' ? '1px solid var(--accent)' : '1px solid var(--card-border)',
        background: variant === 'primary' ? 'var(--accent-dim)' : 'var(--card-bg)',
        color: variant === 'primary' ? 'var(--accent)' : 'var(--muted)',
      }}
      onMouseEnter={e => {
        if (disabled) return;
        const el = e.currentTarget as HTMLButtonElement;
        if (variant === 'primary') {
          el.style.background = 'var(--accent)';
          el.style.color = '#fff';
        } else {
          el.style.color = 'var(--foreground)';
          el.style.borderColor = 'var(--muted)';
        }
      }}
      onMouseLeave={e => {
        if (disabled) return;
        const el = e.currentTarget as HTMLButtonElement;
        if (variant === 'primary') {
          el.style.background = 'var(--accent-dim)';
          el.style.color = 'var(--accent)';
        } else {
          el.style.color = 'var(--muted)';
          el.style.borderColor = 'var(--card-border)';
        }
      }}
    >
      {children}
    </button>
  );
}
