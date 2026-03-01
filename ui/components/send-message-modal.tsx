'use client'

import { useState, useEffect, useRef, useId } from 'react';
import { sendMessage } from '@/lib/actions';
import { MessageAttributeEntry, SendMessageResult } from '@/lib/types';

interface SendMessageModalProps {
  queueName: string;
  queueUrl: string;
  onClose: () => void;
}

const DATA_TYPES = ['String', 'Number', 'Binary', 'String.custom', 'Number.custom'] as const;

const TYPE_COLORS: Record<string, { bg: string; text: string }> = {
  String:        { bg: 'rgba(59,130,246,0.12)', text: '#3b82f6' },
  Number:        { bg: 'rgba(16,185,129,0.12)', text: '#10b981' },
  Binary:        { bg: 'rgba(245,158,11,0.12)',  text: '#f59e0b' },
  'String.custom': { bg: 'rgba(139,92,246,0.12)', text: '#8b5cf6' },
  'Number.custom': { bg: 'rgba(236,72,153,0.12)', text: '#ec4899' },
};

function typeColor(dt: string) {
  return TYPE_COLORS[dt] ?? { bg: 'rgba(107,114,128,0.12)', text: '#6b7280' };
}

function isFifo(name: string) {
  return name.endsWith('.fifo');
}

export function SendMessageModal({ queueName, queueUrl, onClose }: SendMessageModalProps) {
  const uid = useId();
  const bodyRef = useRef<HTMLTextAreaElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  // Form state
  const [body, setBody] = useState('');
  const [delay, setDelay] = useState('');
  const [attrs, setAttrs] = useState<MessageAttributeEntry[]>([]);
  const [traceHeader, setTraceHeader] = useState('');
  const [groupId, setGroupId] = useState('');
  const [dedupId, setDedupId] = useState('');

  // UI state
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showFifo, setShowFifo] = useState(false);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<SendMessageResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mounted, setMounted] = useState(false);

  const fifo = isFifo(queueName);

  useEffect(() => {
    setMounted(true);
    bodyRef.current?.focus();

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Attribute helpers
  const addAttr = () =>
    setAttrs(prev => [...prev, { name: '', dataType: 'String', value: '' }]);

  const removeAttr = (i: number) =>
    setAttrs(prev => prev.filter((_, idx) => idx !== i));

  const updateAttr = (i: number, field: keyof MessageAttributeEntry, value: string) =>
    setAttrs(prev => prev.map((a, idx) => idx === i ? { ...a, [field]: value } : a));

  const handleSend = async () => {
    if (!body.trim()) {
      setError('Message body is required.');
      return;
    }
    setError(null);
    setSending(true);
    try {
      const res = await sendMessage({
        queueUrl,
        messageBody: body,
        delaySeconds: delay !== '' ? parseInt(delay) : undefined,
        messageAttributes: attrs.filter(a => a.name.trim()),
        awsTraceHeader: traceHeader || undefined,
        messageGroupId: groupId || undefined,
        messageDeduplicationId: dedupId || undefined,
      });
      setResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to send message');
    } finally {
      setSending(false);
    }
  };

  const handleSendAnother = () => {
    setResult(null);
    setBody('');
    setDelay('');
    setAttrs([]);
    setTraceHeader('');
    setGroupId('');
    setDedupId('');
    setTimeout(() => bodyRef.current?.focus(), 50);
  };

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
            width: '100%', maxWidth: 680,
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
          {/* ── Header ── */}
          <div style={{
            padding: '20px 24px 16px',
            borderBottom: '1px solid var(--card-border)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
            flexShrink: 0,
          }}>
            <div>
              <p style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 4 }}>
                Send Message
              </p>
              <h2
                id={`${uid}-title`}
                style={{ fontSize: 18, fontWeight: 700, color: 'var(--foreground)', fontFamily: 'monospace', wordBreak: 'break-all' }}
              >
                {queueName}
                {fifo && (
                  <span style={{
                    marginLeft: 8, fontSize: 11, padding: '2px 7px', borderRadius: 4,
                    background: 'rgba(139,92,246,0.12)', color: '#8b5cf6',
                    fontFamily: 'sans-serif', fontWeight: 600, verticalAlign: 'middle',
                    letterSpacing: '0.06em',
                  }}>
                    FIFO
                  </span>
                )}
              </h2>
            </div>
            <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', padding: 4, lineHeight: 1, fontSize: 20 }} aria-label="Close">
              ✕
            </button>
          </div>

          {/* ── Scrollable Body ── */}
          <div style={{ overflowY: 'auto', flex: 1 }}>

            {result ? (
              /* ─── Success State ─── */
              <div style={{ padding: 32, textAlign: 'center' }}>
                <div style={{ fontSize: 40, marginBottom: 16 }}>✓</div>
                <p style={{ fontSize: 16, fontWeight: 600, color: 'var(--foreground)', marginBottom: 8 }}>Message Sent</p>
                <div style={{ margin: '20px 0', padding: 16, borderRadius: 8, background: 'var(--card-bg)', border: '1px solid var(--card-border)', textAlign: 'left' }}>
                  <Row label="Message ID">
                    <code style={{ fontFamily: 'monospace', fontSize: 13, color: 'var(--foreground)', wordBreak: 'break-all' }}>
                      {result.messageId}
                    </code>
                  </Row>
                  {result.sequenceNumber && (
                    <Row label="Sequence Number">
                      <code style={{ fontFamily: 'monospace', fontSize: 13, color: 'var(--foreground)' }}>
                        {result.sequenceNumber}
                      </code>
                    </Row>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
                  <Btn variant="secondary" onClick={onClose}>Close</Btn>
                  <Btn variant="primary" onClick={handleSendAnother}>Send Another</Btn>
                </div>
              </div>
            ) : (
              /* ─── Form ─── */
              <div style={{ padding: '20px 24px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>

                {/* Error */}
                {error && (
                  <div style={{ padding: '10px 14px', borderRadius: 8, background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)', color: 'var(--error)', fontSize: 14 }}>
                    {error}
                  </div>
                )}

                {/* ── Message Body ── */}
                <Field label="Message Body" required htmlFor={`${uid}-body`}>
                  <textarea
                    id={`${uid}-body`}
                    ref={bodyRef}
                    value={body}
                    onChange={e => setBody(e.target.value)}
                    rows={5}
                    placeholder='{"key": "value"}'
                    style={{
                      width: '100%', boxSizing: 'border-box',
                      padding: '10px 12px', borderRadius: 8,
                      background: 'var(--card-bg)',
                      border: '1px solid var(--card-border)',
                      color: 'var(--foreground)',
                      fontFamily: 'monospace', fontSize: 13,
                      resize: 'vertical', outline: 'none',
                      lineHeight: 1.6,
                    }}
                  />
                </Field>

                {/* ── Delay Seconds ── */}
                <Field label="Delay Seconds" hint="0 – 900 (overrides queue default)" htmlFor={`${uid}-delay`}>
                  <input
                    id={`${uid}-delay`}
                    type="number"
                    min={0}
                    max={900}
                    value={delay}
                    onChange={e => setDelay(e.target.value)}
                    placeholder="Queue default"
                    style={inputStyle}
                  />
                </Field>

                {/* ── Message Attributes ── */}
                <section>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <Label>Message Attributes <Hint>up to 10</Hint></Label>
                    {attrs.length < 10 && (
                      <button onClick={addAttr} style={{ fontSize: 13, color: 'var(--accent)', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600, padding: 0 }}>
                        + Add Attribute
                      </button>
                    )}
                  </div>

                  {attrs.length === 0 && (
                    <div style={{ padding: '12px 14px', borderRadius: 8, border: '1px dashed var(--card-border)', textAlign: 'center', color: 'var(--muted)', fontSize: 13 }}>
                      No attributes — <button onClick={addAttr} style={{ background: 'none', border: 'none', color: 'var(--accent)', cursor: 'pointer', fontSize: 13, padding: 0 }}>add one</button>
                    </div>
                  )}

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {attrs.map((attr, i) => {
                      const tc = typeColor(attr.dataType);
                      return (
                        <div key={i} style={{
                          display: 'grid', gridTemplateColumns: '1fr auto 1fr auto',
                          gap: 8, alignItems: 'center',
                          padding: '10px 12px', borderRadius: 8,
                          background: 'var(--card-bg)',
                          border: '1px solid var(--card-border)',
                        }}>
                          <input
                            placeholder="Name"
                            value={attr.name}
                            onChange={e => updateAttr(i, 'name', e.target.value)}
                            style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 12 }}
                          />
                          <select
                            value={attr.dataType}
                            onChange={e => updateAttr(i, 'dataType', e.target.value)}
                            style={{
                              ...inputStyle,
                              background: tc.bg, color: tc.text,
                              fontWeight: 600, fontSize: 12, cursor: 'pointer',
                              minWidth: 110,
                            }}
                          >
                            {DATA_TYPES.map(dt => (
                              <option key={dt} value={dt} style={{ background: 'var(--background)', color: 'var(--foreground)' }}>{dt}</option>
                            ))}
                            <option value={attr.dataType} style={{ background: 'var(--background)', color: 'var(--foreground)' }}>
                              {!DATA_TYPES.includes(attr.dataType as typeof DATA_TYPES[number]) ? attr.dataType : null}
                            </option>
                          </select>
                          <input
                            placeholder="Value"
                            value={attr.value}
                            onChange={e => updateAttr(i, 'value', e.target.value)}
                            style={{ ...inputStyle, fontFamily: attr.dataType === 'Binary' ? 'monospace' : undefined, fontSize: 13 }}
                          />
                          <button
                            onClick={() => removeAttr(i)}
                            aria-label="Remove attribute"
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', fontSize: 16, lineHeight: 1, padding: '0 2px' }}
                          >
                            ×
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </section>

                {/* ── Advanced ── */}
                <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: 16 }}>
                  <button
                    onClick={() => setShowAdvanced(v => !v)}
                    style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', fontSize: 13, fontWeight: 600, padding: 0, letterSpacing: '0.02em' }}
                  >
                    <Arrow open={showAdvanced} />
                    System Attributes
                  </button>

                  {showAdvanced && (
                    <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 14 }}>
                      <Field label="AWS Trace Header" hint="X-Ray tracing ID" htmlFor={`${uid}-trace`}>
                        <input
                          id={`${uid}-trace`}
                          value={traceHeader}
                          onChange={e => setTraceHeader(e.target.value)}
                          placeholder="Root=1-5e272390-8a...;Parent=...;Sampled=1"
                          style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 12 }}
                        />
                      </Field>
                    </div>
                  )}
                </div>

                {/* ── FIFO Options ── */}
                {fifo && (
                  <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: 16 }}>
                    <button
                      onClick={() => setShowFifo(v => !v)}
                      style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: '#8b5cf6', fontSize: 13, fontWeight: 600, padding: 0, letterSpacing: '0.02em' }}
                    >
                      <Arrow open={showFifo} color="#8b5cf6" />
                      FIFO Options
                    </button>

                    {showFifo && (
                      <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 14 }}>
                        <Field
                          label="Message Group ID"
                          hint="Required for FIFO — messages in the same group are processed in order"
                          htmlFor={`${uid}-group`}
                          required
                        >
                          <input
                            id={`${uid}-group`}
                            value={groupId}
                            onChange={e => setGroupId(e.target.value)}
                            placeholder="e.g. order-processing"
                            style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 13 }}
                          />
                        </Field>
                        <Field
                          label="Message Deduplication ID"
                          hint="Prevents duplicate messages within a 5-minute window (required if ContentBasedDeduplication is disabled)"
                          htmlFor={`${uid}-dedup`}
                        >
                          <input
                            id={`${uid}-dedup`}
                            value={dedupId}
                            onChange={e => setDedupId(e.target.value)}
                            placeholder="e.g. unique-token-abc123"
                            style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 13 }}
                          />
                        </Field>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* ── Footer ── */}
          {!result && (
            <div style={{
              padding: '14px 24px',
              borderTop: '1px solid var(--card-border)',
              display: 'flex', justifyContent: 'flex-end', gap: 10,
              flexShrink: 0,
            }}>
              <Btn variant="secondary" onClick={onClose}>Cancel</Btn>
              <Btn variant="primary" onClick={handleSend} disabled={sending}>
                {sending ? 'Sending…' : 'Send Message'}
              </Btn>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

// ─── Small sub-components ───────────────────────────────

const inputStyle: React.CSSProperties = {
  width: '100%', boxSizing: 'border-box',
  padding: '8px 10px', borderRadius: 6,
  background: 'var(--card-bg)',
  border: '1px solid var(--card-border)',
  color: 'var(--foreground)',
  fontSize: 14, outline: 'none',
};

function Label({ children }: { children: React.ReactNode }) {
  return (
    <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--foreground)', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
      {children}
    </p>
  );
}

function Hint({ children }: { children: React.ReactNode }) {
  return <span style={{ fontSize: 11, color: 'var(--muted)', fontWeight: 400 }}>{children}</span>;
}

function Field({ label, hint, required, htmlFor, children }: { label: string; hint?: string; required?: boolean; htmlFor?: string; children: React.ReactNode }) {
  return (
    <div>
      <label htmlFor={htmlFor}>
        <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--foreground)', marginBottom: 4 }}>
          {label}
          {required && <span style={{ color: 'var(--error)', marginLeft: 3 }}>*</span>}
          {hint && <span style={{ fontSize: 11, color: 'var(--muted)', fontWeight: 400, marginLeft: 6 }}>{hint}</span>}
        </p>
      </label>
      {children}
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, marginBottom: 10 }}>
      <span style={{ fontSize: 11, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.07em' }}>{label}</span>
      {children}
    </div>
  );
}

function Arrow({ open, color = 'var(--muted)' }: { open: boolean; color?: string }) {
  return (
    <span style={{ display: 'inline-block', transform: open ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 160ms ease', color, fontSize: 10 }}>
      ▶
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
