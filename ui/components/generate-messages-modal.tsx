'use client'

import { useState, useEffect, useRef, useId } from 'react';
import { sendMessageBatch } from '@/lib/actions';
import { MessageAttributeEntry } from '@/lib/types';

interface GenerateMessagesModalProps {
  queueName: string;
  queueUrl: string;
  onClose: () => void;
}

const DATA_TYPES = ['String', 'Number', 'Binary', 'String.custom', 'Number.custom'] as const;
const TYPE_COLORS: Record<string, { bg: string; text: string }> = {
  String:          { bg: 'rgba(59,130,246,0.12)',  text: '#3b82f6' },
  Number:          { bg: 'rgba(16,185,129,0.12)',  text: '#10b981' },
  Binary:          { bg: 'rgba(245,158,11,0.12)',   text: '#f59e0b' },
  'String.custom': { bg: 'rgba(139,92,246,0.12)',  text: '#8b5cf6' },
  'Number.custom': { bg: 'rgba(236,72,153,0.12)',  text: '#ec4899' },
};
function typeColor(dt: string) {
  return TYPE_COLORS[dt] ?? { bg: 'rgba(107,114,128,0.12)', text: '#6b7280' };
}
function isFifo(name: string) { return name.endsWith('.fifo'); }

interface Progress { sent: number; failed: number; batch: number; totalBatches: number; }

export function GenerateMessagesModal({ queueName, queueUrl, onClose }: GenerateMessagesModalProps) {
  const uid = useId();
  const bodyRef = useRef<HTMLTextAreaElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const abortRef = useRef(false);

  // Form state
  const [repeat, setRepeat] = useState('10');
  const [body, setBody] = useState('');
  const [delay, setDelay] = useState('');
  const [attrs, setAttrs] = useState<MessageAttributeEntry[]>([]);
  const [traceHeader, setTraceHeader] = useState('');
  const [groupId, setGroupId] = useState('');
  const [dedupId, setDedupId] = useState('');

  // UI state
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showFifo, setShowFifo] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [done, setDone] = useState<{ sent: number; failed: number } | null>(null);

  const fifo = isFifo(queueName);
  const repeatNum = Math.max(1, parseInt(repeat) || 1);
  const usesIndex = body.includes('$i');

  useEffect(() => {
    setMounted(true);
    bodyRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onCloseRef.current(); };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = ''; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const addAttr = () => setAttrs(prev => [...prev, { name: '', dataType: 'String', value: '' }]);
  const removeAttr = (i: number) => setAttrs(prev => prev.filter((_, idx) => idx !== i));
  const updateAttr = (i: number, field: keyof MessageAttributeEntry, value: string) =>
    setAttrs(prev => prev.map((a, idx) => idx === i ? { ...a, [field]: value } : a));

  const handleGenerate = async () => {
    if (!body.trim()) { setError('Message body is required.'); return; }
    setError(null);
    setDone(null);
    abortRef.current = false;

    const totalBatches = Math.ceil(repeatNum / 10);
    const validAttrs = attrs.filter(a => a.name.trim());
    let totalSent = 0;
    let totalFailed = 0;

    for (let b = 0; b < totalBatches; b++) {
      if (abortRef.current) break;

      const batchStart = b * 10;
      const batchEnd = Math.min(batchStart + 10, repeatNum);

      setProgress({ sent: totalSent, failed: totalFailed, batch: b + 1, totalBatches });

      const entries = [];
      for (let i = batchStart; i < batchEnd; i++) {
        const idx = i + 1; // 1-based
        const messageBody = body.replace(/\$i/g, String(idx));
        const dedup = dedupId ? `${dedupId}_${idx}` : undefined;
        entries.push({
          id: `msg-${i - batchStart}`,
          messageBody,
          delaySeconds: delay !== '' ? parseInt(delay) : undefined,
          messageAttributes: validAttrs,
          messageGroupId: groupId || undefined,
          messageDeduplicationId: dedup,
        });
      }

      try {
        const result = await sendMessageBatch({ queueUrl, entries, awsTraceHeader: traceHeader || undefined });
        totalSent += result.successful;
        totalFailed += result.failed.length;
      } catch (err) {
        totalFailed += entries.length;
        setError(err instanceof Error ? err.message : 'Batch failed');
        break;
      }
    }

    setProgress(null);
    setDone({ sent: totalSent, failed: totalFailed });
  };

  const handleReset = () => {
    setDone(null);
    setError(null);
    setBody('');
    setRepeat('10');
    setDelay('');
    setAttrs([]);
    setTimeout(() => bodyRef.current?.focus(), 50);
  };

  const isSending = progress !== null;
  const pct = progress ? Math.round((progress.batch / progress.totalBatches) * 100) : 0;

  return (
    <>
      {/* Backdrop */}
      <div onClick={onClose} style={{
        position: 'fixed', inset: 0, zIndex: 50,
        background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(4px)',
        opacity: mounted ? 1 : 0, transition: 'opacity 200ms ease',
      }} />

      {/* Panel */}
      <div role="dialog" aria-modal="true" aria-labelledby={`${uid}-title`} style={{
        position: 'fixed', inset: 0, zIndex: 51,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: '1rem', pointerEvents: 'none',
      }}>
        <div style={{
          width: '100%', maxWidth: 680, maxHeight: '90vh',
          display: 'flex', flexDirection: 'column',
          borderRadius: 12,
          background: 'var(--background)',
          border: '1px solid var(--card-border)',
          boxShadow: '0 24px 64px rgba(0,0,0,0.35)',
          pointerEvents: 'auto',
          transform: mounted ? 'translateY(0) scale(1)' : 'translateY(20px) scale(0.97)',
          opacity: mounted ? 1 : 0,
          transition: 'transform 220ms cubic-bezier(0.34,1.56,0.64,1), opacity 200ms ease',
        }}>

          {/* Header */}
          <div style={{
            padding: '20px 24px 16px',
            borderBottom: '1px solid var(--card-border)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
            flexShrink: 0,
          }}>
            <div>
              <p style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--accent)', marginBottom: 4, fontWeight: 600 }}>
                Generate Messages
              </p>
              <h2 id={`${uid}-title`} style={{ fontSize: 18, fontWeight: 700, color: 'var(--foreground)', fontFamily: 'var(--font-geist-mono), monospace', wordBreak: 'break-all' }}>
                {queueName}
                {fifo && <span style={{ marginLeft: 8, fontSize: 11, padding: '2px 7px', borderRadius: 4, background: 'rgba(139,92,246,0.12)', color: '#8b5cf6', fontFamily: 'sans-serif', fontWeight: 600, verticalAlign: 'middle', letterSpacing: '0.06em' }}>FIFO</span>}
              </h2>
            </div>
            <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', padding: 4, lineHeight: 1, fontSize: 20 }} aria-label="Close">✕</button>
          </div>

          {/* Scrollable body */}
          <div style={{ overflowY: 'auto', flex: 1 }}>

            {done ? (
              /* ── Done state ── */
              <div style={{ padding: 32, textAlign: 'center' }}>
                <div style={{ fontSize: 40, marginBottom: 12 }}>{done.failed === 0 ? '✓' : '⚠'}</div>
                <p style={{ fontSize: 16, fontWeight: 700, color: 'var(--foreground)', marginBottom: 6 }}>
                  Generation Complete
                </p>
                <div style={{ display: 'inline-flex', gap: 24, margin: '16px 0 24px', padding: '14px 24px', borderRadius: 10, background: 'var(--card-bg)', border: '1px solid var(--card-border)' }}>
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--muted)', letterSpacing: '0.07em', textTransform: 'uppercase', marginBottom: 4 }}>Sent</div>
                    <div style={{ fontSize: 28, fontWeight: 700, fontFamily: 'var(--font-geist-mono), monospace', color: 'var(--green)', letterSpacing: '-0.03em' }}>{done.sent}</div>
                  </div>
                  {done.failed > 0 && (
                    <div>
                      <div style={{ fontSize: 11, color: 'var(--muted)', letterSpacing: '0.07em', textTransform: 'uppercase', marginBottom: 4 }}>Failed</div>
                      <div style={{ fontSize: 28, fontWeight: 700, fontFamily: 'var(--font-geist-mono), monospace', color: 'var(--error)', letterSpacing: '-0.03em' }}>{done.failed}</div>
                    </div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
                  <Btn variant="secondary" onClick={onClose}>Close</Btn>
                  <Btn variant="primary" onClick={handleReset}>Generate Again</Btn>
                </div>
              </div>
            ) : (
              /* ── Form ── */
              <div style={{ padding: '20px 24px 24px', display: 'flex', flexDirection: 'column', gap: 18 }}>

                {error && (
                  <div style={{ padding: '10px 14px', borderRadius: 8, background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)', color: 'var(--error)', fontSize: 13 }}>
                    {error}
                  </div>
                )}

                {/* Progress bar */}
                {isSending && (
                  <div style={{ borderRadius: 8, padding: '12px 14px', background: 'var(--card-bg)', border: '1px solid var(--card-border)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 12 }}>
                      <span style={{ color: 'var(--foreground)', fontWeight: 600 }}>
                        Sending batch {progress!.batch} / {progress!.totalBatches}
                      </span>
                      <span style={{ color: 'var(--muted)', fontFamily: 'var(--font-geist-mono), monospace' }}>
                        {progress!.sent} sent · {progress!.failed} failed
                      </span>
                    </div>
                    <div style={{ height: 4, borderRadius: 2, background: 'var(--card-border)', overflow: 'hidden' }}>
                      <div style={{
                        height: '100%', borderRadius: 2,
                        background: 'var(--accent)',
                        width: `${pct}%`,
                        transition: 'width 200ms ease',
                        boxShadow: '0 0 6px var(--accent)',
                      }} />
                    </div>
                  </div>
                )}

                {/* Repeat + Delay side by side */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <Field label="Repeat" hint="number of messages" required htmlFor={`${uid}-repeat`}>
                    <input
                      id={`${uid}-repeat`}
                      type="number"
                      min={1}
                      value={repeat}
                      onChange={e => setRepeat(e.target.value)}
                      disabled={isSending}
                      style={inputStyle}
                    />
                  </Field>
                  <Field label="Delay Seconds" hint="0 – 900" htmlFor={`${uid}-delay`}>
                    <input
                      id={`${uid}-delay`}
                      type="number"
                      min={0}
                      max={900}
                      value={delay}
                      onChange={e => setDelay(e.target.value)}
                      placeholder="Queue default"
                      disabled={isSending}
                      style={inputStyle}
                    />
                  </Field>
                </div>

                {/* Message body */}
                <Field
                  label="Message Body"
                  hint={usesIndex ? `$i → 1…${repeatNum}` : 'use $i for message index'}
                  required
                  htmlFor={`${uid}-body`}
                >
                  <textarea
                    id={`${uid}-body`}
                    ref={bodyRef}
                    value={body}
                    onChange={e => setBody(e.target.value)}
                    rows={5}
                    disabled={isSending}
                    placeholder={'{"index": $i, "key": "value"}'}
                    style={{
                      ...inputStyle,
                      fontFamily: 'var(--font-geist-mono), monospace',
                      fontSize: 13, resize: 'vertical', lineHeight: 1.6,
                    }}
                  />
                  {usesIndex && (
                    <p style={{ fontSize: 11, color: 'var(--accent)', marginTop: 5 }}>
                      <code style={{ fontFamily: 'var(--font-geist-mono), monospace' }}>$i</code> will be replaced with each message's index (1 to {repeatNum})
                    </p>
                  )}
                </Field>

                {/* Message attributes */}
                <section>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <Label>Message Attributes <Hint>up to 10 · applied to every message</Hint></Label>
                    {attrs.length < 10 && !isSending && (
                      <button onClick={addAttr} style={{ fontSize: 13, color: 'var(--accent)', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600, padding: 0 }}>
                        + Add Attribute
                      </button>
                    )}
                  </div>
                  {attrs.length === 0 && (
                    <div style={{ padding: '10px 14px', borderRadius: 8, border: '1px dashed var(--card-border)', textAlign: 'center', color: 'var(--muted)', fontSize: 13 }}>
                      No attributes — <button onClick={addAttr} disabled={isSending} style={{ background: 'none', border: 'none', color: 'var(--accent)', cursor: 'pointer', fontSize: 13, padding: 0 }}>add one</button>
                    </div>
                  )}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {attrs.map((attr, i) => {
                      const tc = typeColor(attr.dataType);
                      return (
                        <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr auto', gap: 8, alignItems: 'center', padding: '10px 12px', borderRadius: 8, background: 'var(--card-bg)', border: '1px solid var(--card-border)' }}>
                          <input placeholder="Name" value={attr.name} onChange={e => updateAttr(i, 'name', e.target.value)} disabled={isSending} style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 12 }} />
                          <select value={attr.dataType} onChange={e => updateAttr(i, 'dataType', e.target.value)} disabled={isSending} style={{ ...inputStyle, background: tc.bg, color: tc.text, fontWeight: 600, fontSize: 12, cursor: 'pointer', minWidth: 110 }}>
                            {DATA_TYPES.map(dt => <option key={dt} value={dt} style={{ background: 'var(--background)', color: 'var(--foreground)' }}>{dt}</option>)}
                          </select>
                          <input placeholder="Value" value={attr.value} onChange={e => updateAttr(i, 'value', e.target.value)} disabled={isSending} style={{ ...inputStyle, fontSize: 13 }} />
                          <button onClick={() => removeAttr(i)} disabled={isSending} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', fontSize: 16, lineHeight: 1, padding: '0 2px' }}>×</button>
                        </div>
                      );
                    })}
                  </div>
                </section>

                {/* System attributes */}
                <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: 14 }}>
                  <button onClick={() => setShowAdvanced(v => !v)} style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', fontSize: 13, fontWeight: 600, padding: 0 }}>
                    <Arrow open={showAdvanced} />
                    System Attributes
                  </button>
                  {showAdvanced && (
                    <div style={{ marginTop: 14 }}>
                      <Field label="AWS Trace Header" hint="X-Ray tracing ID" htmlFor={`${uid}-trace`}>
                        <input id={`${uid}-trace`} value={traceHeader} onChange={e => setTraceHeader(e.target.value)} disabled={isSending} placeholder="Root=1-...;Parent=...;Sampled=1" style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 12 }} />
                      </Field>
                    </div>
                  )}
                </div>

                {/* FIFO options */}
                {fifo && (
                  <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: 14 }}>
                    <button onClick={() => setShowFifo(v => !v)} style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: '#8b5cf6', fontSize: 13, fontWeight: 600, padding: 0 }}>
                      <Arrow open={showFifo} color="#8b5cf6" />
                      FIFO Options
                    </button>
                    {showFifo && (
                      <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 14 }}>
                        <Field label="Message Group ID" hint="Required for FIFO" htmlFor={`${uid}-group`} required>
                          <input id={`${uid}-group`} value={groupId} onChange={e => setGroupId(e.target.value)} disabled={isSending} placeholder="e.g. order-processing" style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 13 }} />
                        </Field>
                        <Field label="Deduplication ID Prefix" hint="Each message gets prefix_i — leave blank if ContentBasedDeduplication is enabled" htmlFor={`${uid}-dedup`}>
                          <input id={`${uid}-dedup`} value={dedupId} onChange={e => setDedupId(e.target.value)} disabled={isSending} placeholder="e.g. run-001 → run-001_1, run-001_2, …" style={{ ...inputStyle, fontFamily: 'monospace', fontSize: 13 }} />
                        </Field>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Footer */}
          {!done && (
            <div style={{ padding: '14px 24px', borderTop: '1px solid var(--card-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
              <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                {repeatNum} message{repeatNum !== 1 ? 's' : ''} · {Math.ceil(repeatNum / 10)} batch{Math.ceil(repeatNum / 10) !== 1 ? 'es' : ''}
              </span>
              <div style={{ display: 'flex', gap: 10 }}>
                <Btn variant="secondary" onClick={onClose} disabled={isSending}>Cancel</Btn>
                <Btn variant="primary" onClick={handleGenerate} disabled={isSending}>
                  {isSending ? `Sending ${progress!.batch}/${progress!.totalBatches}…` : `Generate ${repeatNum}`}
                </Btn>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

// ─── Sub-components (same as send-message-modal) ─────────────────────────────

const inputStyle: React.CSSProperties = {
  width: '100%', boxSizing: 'border-box',
  padding: '8px 10px', borderRadius: 6,
  background: 'var(--card-bg)',
  border: '1px solid var(--card-border)',
  color: 'var(--foreground)',
  fontSize: 14, outline: 'none',
};

function Label({ children }: { children: React.ReactNode }) {
  return <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--foreground)', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 6 }}>{children}</p>;
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

function Arrow({ open, color = 'var(--muted)' }: { open: boolean; color?: string }) {
  return <span style={{ display: 'inline-block', transform: open ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 160ms ease', color, fontSize: 10 }}>▶</span>;
}

function Btn({ variant, onClick, disabled, children }: { variant: 'primary' | 'secondary'; onClick: () => void; disabled?: boolean; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        padding: '8px 18px', borderRadius: 7, fontSize: 14, fontWeight: 600,
        cursor: disabled ? 'not-allowed' : 'pointer',
        border: 'none', transition: 'opacity 150ms',
        opacity: disabled ? 0.55 : 1,
        background: variant === 'primary' ? 'var(--accent)' : 'var(--card-bg)',
        color: variant === 'primary' ? '#fff' : 'var(--foreground)',
        outline: variant === 'secondary' ? '1px solid var(--card-border)' : 'none',
      }}
    >
      {children}
    </button>
  );
}
