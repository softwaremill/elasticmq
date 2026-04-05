import { TriangleAlert } from 'lucide-react';

interface ErrorDisplayProps {
  message: string;
  onRetry?: () => void;
}

export function ErrorDisplay({ message, onRetry }: ErrorDisplayProps) {
  return (
    <div style={{
      padding: '28px 24px',
      borderRadius: 10,
      border: '1px solid var(--error)',
      background: 'rgba(251,75,110,0.05)',
      animation: 'fadeUp 300ms ease both',
    }}>
      <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
        <div style={{
          width: 32, height: 32, borderRadius: 8, flexShrink: 0,
          background: 'rgba(251,75,110,0.1)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--error)',
        }}>
          <TriangleAlert size={16} />
        </div>
        <div style={{ flex: 1 }}>
          <p style={{ fontWeight: 600, color: 'var(--error)', marginBottom: 4, fontSize: 14 }}>
            Connection Error
          </p>
          <p style={{ color: 'var(--muted)', fontSize: 13, lineHeight: 1.5, marginBottom: onRetry ? 16 : 0 }}>
            {message}
          </p>
          {onRetry && (
            <button
              onClick={onRetry}
              style={{
                padding: '6px 16px',
                borderRadius: 6,
                border: '1px solid var(--error)',
                background: 'rgba(251,75,110,0.08)',
                color: 'var(--error)',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'background 150ms',
              }}
              onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(251,75,110,0.18)'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(251,75,110,0.08)'; }}
            >
              Retry
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
