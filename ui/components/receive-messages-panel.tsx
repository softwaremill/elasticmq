'use client'

import { useState, useEffect, useRef } from 'react';
import { ChevronRight, X } from 'lucide-react';
import { receiveMessages, deleteMessage } from '@/lib/actions';
import { ReceivedMessage } from '@/lib/types';

interface ReceiveMessagesPanelProps {
  queueName: string;
  queueUrl: string;
  onClose: () => void;
}

// System attribute keys that contain Unix timestamps (seconds)
const TIMESTAMP_ATTRS = ['SentTimestamp', 'ApproximateFirstReceiveTimestamp'];

function formatSysAttrValue(key: string, value: string): string {
  if (TIMESTAMP_ATTRS.includes(key)) {
    const ms = parseInt(value);
    if (!isNaN(ms)) return new Date(ms).toLocaleString();
  }
  return value;
}

function tryFormatJson(raw: string): { formatted: string; isJson: boolean } {
  try {
    const parsed = JSON.parse(raw);
    return { formatted: JSON.stringify(parsed, null, 2), isJson: true };
  } catch {
    return { formatted: raw, isJson: false };
  }
}

// ─── Message Card ────────────────────────────────────────────────────────────

function MessageCard({
  message,
  onDelete,
}: {
  message: ReceivedMessage;
  onDelete: (receiptHandle: string) => Promise<void>;
}) {
  const [expanded, setExpanded] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [deleted, setDeleted] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const { formatted: bodyFormatted, isJson: bodyIsJson } = tryFormatJson(message.body);

  const hasMessageAttributes = Object.keys(message.messageAttributes).length > 0;
  const hasSystemAttributes = Object.keys(message.systemAttributes).length > 0;

  const handleDelete = async () => {
    setDeleting(true);
    setDeleteError(null);
    try {
      await onDelete(message.receiptHandle);
      setDeleted(true);
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Delete failed');
      setDeleting(false);
    }
  };

  if (deleted) {
    return (
      <div style={{
        padding: '12px 16px',
        borderRadius: 8,
        border: '1px solid var(--card-border)',
        background: 'var(--card-bg)',
        opacity: 0.5,
        fontSize: 13,
        color: 'var(--muted)',
        fontStyle: 'italic',
      }}>
        Message deleted — {message.messageId}
      </div>
    );
  }

  return (
    <div style={{
      borderRadius: 8,
      border: '1px solid var(--card-border)',
      background: 'var(--card-bg)',
      overflow: 'hidden',
    }}>
      {/* Card header */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '10px 14px',
        borderBottom: expanded ? '1px solid var(--card-border)' : 'none',
        gap: 10,
      }}>
        <button
          onClick={() => setExpanded(v => !v)}
          style={{
            display: 'flex', alignItems: 'center', gap: 8,
            background: 'none', border: 'none', cursor: 'pointer', padding: 0,
            flex: 1, minWidth: 0, textAlign: 'left',
          }}
        >
          <span style={{
            display: 'inline-block',
            transform: expanded ? 'rotate(90deg)' : 'rotate(0deg)',
            transition: 'transform 160ms ease',
            color: 'var(--muted)', flexShrink: 0,
          }}><ChevronRight size={12} /></span>
          <code style={{
            fontSize: 12, color: 'var(--muted)',
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>
            {message.messageId}
          </code>
        </button>

        <button
          onClick={handleDelete}
          disabled={deleting}
          style={{
            padding: '4px 12px', borderRadius: 5, fontSize: 12, fontWeight: 600,
            cursor: deleting ? 'not-allowed' : 'pointer', border: 'none',
            background: 'rgba(239,68,68,0.1)', color: 'var(--error)',
            opacity: deleting ? 0.5 : 1, flexShrink: 0,
            transition: 'opacity 150ms, background 150ms',
          }}
          onMouseEnter={e => { if (!deleting) (e.currentTarget.style.background = 'rgba(239,68,68,0.2)'); }}
          onMouseLeave={e => { (e.currentTarget.style.background = 'rgba(239,68,68,0.1)'); }}
        >
          {deleting ? 'Deleting…' : 'Delete'}
        </button>
      </div>

      {deleteError && (
        <div style={{ padding: '8px 14px', fontSize: 12, color: 'var(--error)', background: 'rgba(239,68,68,0.06)' }}>
          {deleteError}
        </div>
      )}

      {/* Expanded content */}
      {expanded && (
        <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* Body */}
          <section>
            <SectionLabel>
              Body
              {bodyIsJson && <Tag>JSON</Tag>}
            </SectionLabel>
            <pre style={{
              margin: 0, padding: '10px 12px', borderRadius: 6,
              background: 'var(--background)',
              border: '1px solid var(--card-border)',
              fontSize: 12, lineHeight: 1.6,
              color: 'var(--foreground)',
              fontFamily: 'monospace',
              whiteSpace: 'pre-wrap', wordBreak: 'break-word',
              maxHeight: 240, overflowY: 'auto',
            }}>
              {bodyFormatted}
            </pre>
          </section>

          {/* System Attributes */}
          {hasSystemAttributes && (
            <section>
              <SectionLabel>Properties</SectionLabel>
              <AttrTable>
                {Object.entries(message.systemAttributes).map(([key, value]) => (
                  <AttrRow key={key} name={key} value={formatSysAttrValue(key, value)} mono={false} />
                ))}
                <AttrRow name="MD5 of Body" value={message.md5OfBody} mono />
                {message.md5OfMessageAttributes && (
                  <AttrRow name="MD5 of Attributes" value={message.md5OfMessageAttributes} mono />
                )}
              </AttrTable>
            </section>
          )}

          {/* Message Attributes */}
          {hasMessageAttributes && (
            <section>
              <SectionLabel>Message Attributes</SectionLabel>
              <AttrTable>
                {Object.entries(message.messageAttributes).map(([key, attr]) => (
                  <AttrRow
                    key={key}
                    name={key}
                    value={attr.stringValue ?? (attr.binaryValue ? `[Binary] ${attr.binaryValue}` : '')}
                    tag={attr.dataType}
                    mono
                  />
                ))}
              </AttrTable>
            </section>
          )}

          {/* Receipt Handle */}
          <section>
            <SectionLabel>Receipt Handle</SectionLabel>
            <code style={{
              display: 'block', fontSize: 11, padding: '6px 10px',
              borderRadius: 5, background: 'var(--background)',
              border: '1px solid var(--card-border)',
              color: 'var(--muted)',
              wordBreak: 'break-all', fontFamily: 'monospace',
            }}>
              {message.receiptHandle}
            </code>
          </section>
        </div>
      )}
    </div>
  );
}

// ─── Panel ───────────────────────────────────────────────────────────────────

export function ReceiveMessagesPanel({ queueName, queueUrl, onClose }: ReceiveMessagesPanelProps) {
  const [maxMessages, setMaxMessages] = useState(10);
  const [visibilityTimeout, setVisibilityTimeout] = useState('');
  const [waitTime, setWaitTime] = useState(0);

  const [messages, setMessages] = useState<ReceivedMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [received, setReceived] = useState(false);
  const [mounted, setMounted] = useState(false);

  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    setMounted(true);
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onCloseRef.current(); };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, []);

  const handleReceive = async () => {
    setLoading(true);
    setError(null);
    try {
      const msgs = await receiveMessages({
        queueUrl,
        maxNumberOfMessages: maxMessages,
        visibilityTimeout: visibilityTimeout !== '' ? parseInt(visibilityTimeout) : undefined,
        waitTimeSeconds: waitTime,
      });
      setMessages(msgs);
      setReceived(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to receive messages');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (receiptHandle: string) => {
    await deleteMessage(queueUrl, receiptHandle);
  };

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed', inset: 0, zIndex: 50,
          background: 'rgba(0,0,0,0.35)',
          backdropFilter: 'blur(2px)',
          opacity: mounted ? 1 : 0,
          transition: 'opacity 200ms ease',
        }}
      />

      {/* Drawer */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`Receive messages from ${queueName}`}
        style={{
          position: 'fixed', top: 0, right: 0, bottom: 0, zIndex: 51,
          width: '100%', maxWidth: 660,
          display: 'flex', flexDirection: 'column',
          background: 'var(--background)',
          borderLeft: '1px solid var(--card-border)',
          boxShadow: '-16px 0 48px rgba(0,0,0,0.2)',
          transform: mounted ? 'translateX(0)' : 'translateX(100%)',
          transition: 'transform 250ms cubic-bezier(0.32,0.72,0,1)',
        }}
      >
        {/* Header */}
        <div style={{
          padding: '18px 22px 14px',
          borderBottom: '1px solid var(--card-border)',
          flexShrink: 0,
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <p style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 3 }}>
                Receive Messages
              </p>
              <h2 style={{ fontSize: 17, fontWeight: 700, color: 'var(--foreground)', fontFamily: 'monospace', wordBreak: 'break-all' }}>
                {queueName}
              </h2>
            </div>
            <button
              onClick={onClose}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', lineHeight: 1, padding: 4, display: 'flex' }}
              aria-label="Close"
            >
              <X size={18} />
            </button>
          </div>

          {/* Controls */}
          <div style={{ display: 'flex', gap: 10, marginTop: 14, flexWrap: 'wrap', alignItems: 'flex-end' }}>
            <ControlField label="Max Messages">
              <select
                value={maxMessages}
                onChange={e => setMaxMessages(parseInt(e.target.value))}
                style={controlInput}
              >
                {[1,2,3,4,5,6,7,8,9,10].map(n => (
                  <option key={n} value={n} style={{ background: 'var(--background)' }}>{n}</option>
                ))}
              </select>
            </ControlField>

            <ControlField label="Visibility Timeout (s)">
              <input
                type="number"
                min={0}
                max={43200}
                value={visibilityTimeout}
                onChange={e => setVisibilityTimeout(e.target.value)}
                placeholder="Queue default"
                style={{ ...controlInput, width: 120 }}
              />
            </ControlField>

            <ControlField label="Wait Time (s)">
              <select
                value={waitTime}
                onChange={e => setWaitTime(parseInt(e.target.value))}
                style={controlInput}
              >
                {Array.from({ length: 21 }, (_, i) => i).map(n => (
                  <option key={n} value={n} style={{ background: 'var(--background)' }}>{n}s</option>
                ))}
              </select>
            </ControlField>

            <button
              onClick={handleReceive}
              disabled={loading}
              style={{
                padding: '7px 16px', borderRadius: 7, fontSize: 13, fontWeight: 600,
                cursor: loading ? 'not-allowed' : 'pointer', border: 'none',
                background: 'var(--accent)', color: '#fff',
                opacity: loading ? 0.6 : 1, transition: 'opacity 150ms',
                flexShrink: 0,
              }}
            >
              {loading ? 'Receiving…' : 'Receive'}
            </button>
          </div>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '16px 22px' }}>
          {error && (
            <div style={{
              padding: '10px 14px', borderRadius: 8, marginBottom: 16,
              background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)',
              color: 'var(--error)', fontSize: 13,
            }}>
              {error}
            </div>
          )}

          {!received && !loading && (
            <div style={{ textAlign: 'center', padding: '48px 0', color: 'var(--muted)', fontSize: 14 }}>
              Configure options above and press <strong style={{ color: 'var(--foreground)' }}>Receive</strong> to fetch messages.
            </div>
          )}

          {loading && (
            <div style={{ textAlign: 'center', padding: '48px 0', color: 'var(--muted)', fontSize: 14 }}>
              {waitTime > 0
                ? `Long-polling for up to ${waitTime}s…`
                : 'Fetching messages…'}
            </div>
          )}

          {received && !loading && (
            <>
              <p style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 14 }}>
                {messages.length === 0
                  ? 'No messages available in the queue.'
                  : `${messages.length} message${messages.length !== 1 ? 's' : ''} received`}
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {messages.map(msg => (
                  <MessageCard key={msg.receiptHandle} message={msg} onDelete={handleDelete} />
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

const controlInput: React.CSSProperties = {
  padding: '6px 8px', borderRadius: 6, fontSize: 13,
  background: 'var(--card-bg)',
  border: '1px solid var(--card-border)',
  color: 'var(--foreground)',
  outline: 'none',
};

function ControlField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 11, color: 'var(--muted)', fontWeight: 600, letterSpacing: '0.04em' }}>{label}</span>
      {children}
    </div>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p style={{
      fontSize: 11, fontWeight: 700, letterSpacing: '0.07em',
      textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 6,
      display: 'flex', alignItems: 'center', gap: 6,
    }}>
      {children}
    </p>
  );
}

function Tag({ children }: { children: React.ReactNode }) {
  return (
    <span style={{
      fontSize: 10, padding: '1px 6px', borderRadius: 4,
      background: 'rgba(59,130,246,0.12)', color: '#3b82f6',
      fontWeight: 600, letterSpacing: '0.04em', textTransform: 'none',
    }}>
      {children}
    </span>
  );
}

function AttrTable({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      borderRadius: 6, overflow: 'hidden',
      border: '1px solid var(--card-border)',
    }}>
      {children}
    </div>
  );
}

function AttrRow({ name, value, mono, tag }: { name: string; value: string; mono?: boolean; tag?: string }) {
  return (
    <div style={{
      display: 'grid', gridTemplateColumns: '200px 1fr',
      padding: '7px 10px',
      borderBottom: '1px solid var(--card-border)',
      fontSize: 12, gap: 12,
    }}>
      <span style={{ color: 'var(--muted)', fontFamily: 'monospace', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {name}
      </span>
      <span style={{ color: 'var(--foreground)', fontFamily: mono ? 'monospace' : undefined, wordBreak: 'break-word', display: 'flex', alignItems: 'center', gap: 6 }}>
        {value}
        {tag && (
          <span style={{
            fontSize: 10, padding: '1px 5px', borderRadius: 3, flexShrink: 0,
            background: 'rgba(107,114,128,0.1)', color: 'var(--muted)', fontFamily: 'sans-serif',
          }}>
            {tag}
          </span>
        )}
      </span>
    </div>
  );
}
