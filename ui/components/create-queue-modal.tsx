'use client'

import { useState, useEffect, useRef, useId } from 'react';
import { X, ChevronRight } from 'lucide-react';
import { createQueue } from '@/lib/actions';

interface CreateQueueModalProps {
  onClose: () => void;
  onCreated: (queueName: string) => void;
}

export function CreateQueueModal({ onClose, onCreated }: CreateQueueModalProps) {
  const uid = useId();
  const nameRef = useRef<HTMLInputElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  const [name, setName] = useState('');
  const [fifo, setFifo] = useState(false);
  const [contentBasedDedup, setContentBasedDedup] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [visibilityTimeout, setVisibilityTimeout] = useState('');
  const [messageRetention, setMessageRetention] = useState('');
  const [delaySeconds, setDelaySeconds] = useState('');
  const [waitTime, setWaitTime] = useState('');

  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    nameRef.current?.focus();

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, []);

  const displayName = fifo && name && !name.endsWith('.fifo') ? `${name}.fifo` : name;

  const handleCreate = async () => {
    setError(null);
    setCreating(true);
    try {
      await createQueue({
        name,
        fifo,
        contentBasedDeduplication: fifo ? contentBasedDedup : undefined,
        visibilityTimeout: visibilityTimeout !== '' ? parseInt(visibilityTimeout) : undefined,
        messageRetentionPeriod: messageRetention !== '' ? parseInt(messageRetention) : undefined,
        delaySeconds: delaySeconds !== '' ? parseInt(delaySeconds) : undefined,
        receiveMessageWaitTimeSeconds: waitTime !== '' ? parseInt(waitTime) : undefined,
      });
      onCreated(displayName);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create queue');
    } finally {
      setCreating(false);
    }
  };

  const canSubmit = name.trim().length > 0 && !creating;

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed', inset: 0, zIndex: 50,
          background: 'rgba(0,0,0,0.55)',
          backdropFilter: 'blur(4px)',
          opacity: mounted ? 1 : 0,
          transition: 'opacity 200ms ease',
        }}
      />

      {/* Panel */}
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${uid}-title`}
        style={{
          position: 'fixed', inset: 0, zIndex: 51,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          padding: '1rem',
          pointerEvents: 'none',
        }}
      >
        <div
          style={{
            width: '100%', maxWidth: 520,
            maxHeight: '90vh',
            display: 'flex', flexDirection: 'column',
            borderRadius: 12,
            background: 'var(--background)',
            border: '1px solid var(--card-border)',
            boxShadow: '0 24px 64px rgba(0,0,0,0.30)',
            pointerEvents: 'auto',
            transform: mounted ? 'translateY(0) scale(1)' : 'translateY(20px) scale(0.97)',
            opacity: mounted ? 1 : 0,
            transition: 'transform 220ms cubic-bezier(0.34,1.56,0.64,1), opacity 200ms ease',
          }}
        >
          {/* Header */}
          <div style={{
            padding: '20px 24px 16px',
            borderBottom: '1px solid var(--card-border)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
            flexShrink: 0,
          }}>
            <div>
              <p style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 4 }}>
                New Queue
              </p>
              <h2
                id={`${uid}-title`}
                style={{ fontSize: 18, fontWeight: 700, color: 'var(--foreground)' }}
              >
                Create Queue
              </h2>
            </div>
            <button
              onClick={onClose}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', padding: 4, lineHeight: 1, display: 'flex' }}
              aria-label="Close"
            >
              <X size={18} />
            </button>
          </div>

          {/* Body */}
          <div style={{ overflowY: 'auto', flex: 1, padding: '20px 24px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>

            {error && (
              <div style={{ padding: '10px 14px', borderRadius: 8, background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)', color: 'var(--error, #ef4444)', fontSize: 14 }}>
                {error}
              </div>
            )}

            {/* Queue Name */}
            <Field label="Queue Name" required htmlFor={`${uid}-name`}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 0 }}>
                <input
                  id={`${uid}-name`}
                  ref={nameRef}
                  value={name}
                  onChange={e => setName(e.target.value.replace(/\s/g, '-'))}
                  onKeyDown={e => { if (e.key === 'Enter' && canSubmit) handleCreate(); }}
                  placeholder="my-queue"
                  style={{
                    ...inputStyle,
                    fontFamily: 'var(--font-geist-mono), monospace',
                    fontSize: 14,
                    borderRadius: fifo ? '6px 0 0 6px' : 6,
                    flex: 1,
                  }}
                />
                {fifo && (
                  <span style={{
                    padding: '8px 10px',
                    background: 'rgba(139,92,246,0.12)',
                    border: '1px solid var(--card-border)',
                    borderLeft: 'none',
                    borderRadius: '0 6px 6px 0',
                    fontFamily: 'var(--font-geist-mono), monospace',
                    fontSize: 13,
                    color: '#8b5cf6',
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    boxSizing: 'border-box',
                  }}>
                    .fifo
                  </span>
                )}
              </div>
            </Field>

            {/* FIFO Toggle */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <Toggle
                id={`${uid}-fifo`}
                label="FIFO Queue"
                hint="Guarantees ordering and exactly-once processing"
                checked={fifo}
                onChange={v => { setFifo(v); if (!v) setContentBasedDedup(false); }}
              />

              {fifo && (
                <Toggle
                  id={`${uid}-dedup`}
                  label="Content-based Deduplication"
                  hint="Use SHA-256 hash of message body as deduplication ID"
                  checked={contentBasedDedup}
                  onChange={setContentBasedDedup}
                  indent
                />
              )}
            </div>

            {/* Advanced */}
            <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: 16 }}>
              <button
                onClick={() => setShowAdvanced(v => !v)}
                style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', fontSize: 13, fontWeight: 600, padding: 0, letterSpacing: '0.02em' }}
              >
                <Arrow open={showAdvanced} />
                Advanced Settings
              </button>

              {showAdvanced && (
                <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
                  <Field label="Visibility Timeout" hint="seconds (0–43200, default 30)" htmlFor={`${uid}-vt`}>
                    <input id={`${uid}-vt`} type="number" min={0} max={43200} value={visibilityTimeout}
                      onChange={e => setVisibilityTimeout(e.target.value)}
                      placeholder="30" style={inputStyle} />
                  </Field>
                  <Field label="Message Retention Period" hint="seconds (60–1209600, default 345600)" htmlFor={`${uid}-ret`}>
                    <input id={`${uid}-ret`} type="number" min={60} max={1209600} value={messageRetention}
                      onChange={e => setMessageRetention(e.target.value)}
                      placeholder="345600" style={inputStyle} />
                  </Field>
                  <Field label="Delay Seconds" hint="0–900, default 0" htmlFor={`${uid}-delay`}>
                    <input id={`${uid}-delay`} type="number" min={0} max={900} value={delaySeconds}
                      onChange={e => setDelaySeconds(e.target.value)}
                      placeholder="0" style={inputStyle} />
                  </Field>
                  <Field label="Receive Message Wait Time" hint="seconds (0–20, enables long polling)" htmlFor={`${uid}-wait`}>
                    <input id={`${uid}-wait`} type="number" min={0} max={20} value={waitTime}
                      onChange={e => setWaitTime(e.target.value)}
                      placeholder="0" style={inputStyle} />
                  </Field>
                </div>
              )}
            </div>
          </div>

          {/* Footer */}
          <div style={{
            padding: '14px 24px',
            borderTop: '1px solid var(--card-border)',
            display: 'flex', justifyContent: 'flex-end', gap: 10,
            flexShrink: 0,
          }}>
            <Btn variant="secondary" onClick={onClose}>Cancel</Btn>
            <Btn variant="primary" onClick={handleCreate} disabled={!canSubmit}>
              {creating ? 'Creating…' : 'Create Queue'}
            </Btn>
          </div>
        </div>
      </div>
    </>
  );
}

// ─── Sub-components ───────────────────────────────────────

const inputStyle: React.CSSProperties = {
  width: '100%', boxSizing: 'border-box',
  padding: '8px 10px', borderRadius: 6,
  background: 'var(--card-bg)',
  border: '1px solid var(--card-border)',
  color: 'var(--foreground)',
  fontSize: 14, outline: 'none',
};

function Field({ label, hint, required, htmlFor, children }: { label: string; hint?: string; required?: boolean; htmlFor?: string; children: React.ReactNode }) {
  return (
    <div>
      <label htmlFor={htmlFor}>
        <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--foreground)', marginBottom: 4 }}>
          {label}
          {required && <span style={{ color: 'var(--error, #ef4444)', marginLeft: 3 }}>*</span>}
          {hint && <span style={{ fontSize: 11, color: 'var(--muted)', fontWeight: 400, marginLeft: 6 }}>{hint}</span>}
        </p>
      </label>
      {children}
    </div>
  );
}

function Toggle({ id, label, hint, checked, onChange, indent }: {
  id: string; label: string; hint?: string; checked: boolean; onChange: (v: boolean) => void; indent?: boolean;
}) {
  return (
    <label
      htmlFor={id}
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 12, cursor: 'pointer',
        padding: '10px 12px', borderRadius: 8,
        background: 'var(--card-bg)', border: '1px solid var(--card-border)',
        marginLeft: indent ? 16 : 0,
      }}
    >
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={e => onChange(e.target.checked)}
        style={{ marginTop: 2, accentColor: 'var(--accent)', width: 14, height: 14, flexShrink: 0 }}
      />
      <div>
        <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--foreground)', margin: 0 }}>{label}</p>
        {hint && <p style={{ fontSize: 11, color: 'var(--muted)', margin: '2px 0 0' }}>{hint}</p>}
      </div>
    </label>
  );
}

function Arrow({ open }: { open: boolean }) {
  return (
    <span style={{ display: 'inline-flex', transform: open ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 160ms ease' }}>
      <ChevronRight size={12} />
    </span>
  );
}

function Btn({ variant, onClick, disabled, children }: { variant: 'primary' | 'secondary'; onClick: () => void; disabled?: boolean; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        padding: '8px 18px', borderRadius: 7, fontSize: 14, fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer',
        border: 'none', transition: 'opacity 150ms',
        opacity: disabled ? 0.55 : 1,
        background: variant === 'primary' ? 'var(--accent)' : 'var(--card-bg)',
        color: variant === 'primary' ? '#fff' : 'var(--foreground)',
        boxShadow: variant === 'primary' ? '0 2px 8px rgba(59,130,246,0.25)' : 'none',
        outline: variant === 'secondary' ? '1px solid var(--card-border)' : 'none',
      }}
    >
      {children}
    </button>
  );
}
