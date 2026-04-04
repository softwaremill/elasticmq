'use client'

import { useState } from 'react';
import Link from 'next/link';
import { QueueData } from '@/lib/types';
import { SendMessageModal } from './send-message-modal';
import { GenerateMessagesModal } from './generate-messages-modal';

interface QueueCardProps {
  queue: QueueData;
  index?: number;
}

export function QueueCard({ queue, index = 0 }: QueueCardProps) {
  const [showModal, setShowModal] = useState(false);
  const [showGenerate, setShowGenerate] = useState(false);
  const [hovered, setHovered] = useState(false);
  const hasMessages = queue.stats.approximateNumberOfMessages > 0;

  return (
    <>
      <div
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        style={{
          position: 'relative',
          borderRadius: 10,
          border: '1px solid',
          borderColor: hovered ? 'var(--accent)' : 'var(--card-border)',
          background: hovered ? 'var(--card-hover)' : 'var(--card-bg)',
          transition: 'border-color 200ms ease, background 200ms ease, box-shadow 200ms ease',
          boxShadow: hovered
            ? '0 0 0 1px var(--accent-dim), 0 4px 24px rgba(0,0,0,0.15)'
            : '0 1px 4px rgba(0,0,0,0.08)',
          overflow: 'hidden',
          animation: 'fadeUp 300ms ease both',
          animationDelay: `${index * 60}ms`,
        }}
      >
        {/* Left accent bar */}
        <div style={{
          position: 'absolute', left: 0, top: 0, bottom: 0, width: 3,
          background: hasMessages ? 'var(--accent)' : 'var(--card-border)',
          transition: 'background 400ms ease',
          boxShadow: hasMessages ? '0 0 8px var(--accent)' : 'none',
        }} />

        {/* Main clickable area */}
        <Link
          href={`/queues/${encodeURIComponent(queue.name)}`}
          style={{ display: 'block', padding: '16px 20px 14px 22px', textDecoration: 'none' }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
            <div style={{ minWidth: 0 }}>
              <code style={{
                fontFamily: 'var(--font-geist-mono), monospace',
                fontSize: 14,
                fontWeight: 600,
                color: 'var(--foreground)',
                letterSpacing: '-0.01em',
                display: 'block',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}>
                {queue.name}
              </code>
            </div>
            <svg
              width="14" height="14" viewBox="0 0 14 14" fill="none"
              style={{
                flexShrink: 0, marginLeft: 12, marginTop: 2,
                color: hovered ? 'var(--accent)' : 'var(--muted)',
                transition: 'color 200ms, transform 200ms',
                transform: hovered ? 'translateX(2px)' : 'translateX(0)',
              }}
            >
              <path d="M3 7h8M7.5 3.5L11 7l-3.5 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>

          {/* Stats row */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
            <Stat label="Available" value={queue.stats.approximateNumberOfMessages} highlight />
            <Stat label="Delayed"   value={queue.stats.approximateNumberOfMessagesDelayed} />
            <Stat label="In Flight" value={queue.stats.approximateNumberOfMessagesNotVisible} />
          </div>
        </Link>

        {/* Action bar */}
        <div style={{
          padding: '8px 20px 10px 22px',
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 8,
          borderTop: '1px solid var(--card-border)',
        }}>
          <CardBtn label="Generate" onClick={() => setShowGenerate(true)} muted />
          <CardBtn label="Send →" onClick={() => setShowModal(true)} />
        </div>
      </div>

      {showModal && (
        <SendMessageModal queueName={queue.name} queueUrl={queue.url} onClose={() => setShowModal(false)} />
      )}
      {showGenerate && (
        <GenerateMessagesModal queueName={queue.name} queueUrl={queue.url} onClose={() => setShowGenerate(false)} />
      )}
    </>
  );
}

function CardBtn({ label, onClick, muted }: { label: string; onClick: () => void; muted?: boolean }) {
  return (
    <button
      onClick={e => { e.preventDefault(); onClick(); }}
      style={{
        padding: '4px 12px', borderRadius: 6, fontSize: 12, fontWeight: 600, cursor: 'pointer',
        letterSpacing: '0.02em', transition: 'background 150ms, color 150ms',
        border: muted ? '1px solid var(--card-border)' : '1px solid var(--accent)',
        background: muted ? 'transparent' : 'var(--accent-dim)',
        color: muted ? 'var(--muted)' : 'var(--accent)',
      }}
      onMouseEnter={e => {
        const el = e.currentTarget as HTMLButtonElement;
        el.style.background = muted ? 'var(--card-bg)' : 'var(--accent)';
        el.style.color = muted ? 'var(--foreground)' : '#fff';
      }}
      onMouseLeave={e => {
        const el = e.currentTarget as HTMLButtonElement;
        el.style.background = muted ? 'transparent' : 'var(--accent-dim)';
        el.style.color = muted ? 'var(--muted)' : 'var(--accent)';
      }}
    >
      {label}
    </button>
  );
}

function Stat({ label, value, highlight }: { label: string; value: number; highlight?: boolean }) {
  return (
    <div>
      <div style={{
        fontSize: 10,
        fontWeight: 600,
        letterSpacing: '0.08em',
        textTransform: 'uppercase',
        color: 'var(--muted)',
        marginBottom: 3,
      }}>
        {label}
      </div>
      <div style={{
        fontFamily: 'var(--font-geist-mono), monospace',
        fontSize: 24,
        fontWeight: 700,
        fontVariantNumeric: 'tabular-nums',
        color: highlight && value > 0 ? 'var(--accent)' : 'var(--foreground)',
        lineHeight: 1,
        letterSpacing: '-0.03em',
      }}>
        {value}
      </div>
    </div>
  );
}
