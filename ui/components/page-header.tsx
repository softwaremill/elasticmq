'use client'

import { ThemeSwitcher } from './theme-switcher';

export function PageHeader() {
  return (
    <header style={{ marginBottom: 36 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          {/* Logo mark */}
          <div style={{
            width: 38, height: 38, borderRadius: 10,
            background: 'var(--accent-dim)',
            border: '1px solid var(--accent)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
              <rect x="1" y="5" width="16" height="3.5" rx="1.5" fill="var(--accent)" opacity="0.9"/>
              <rect x="1" y="9.5" width="16" height="3.5" rx="1.5" fill="var(--accent)" opacity="0.55"/>
              <circle cx="14.5" cy="3" r="2" fill="var(--accent)">
                <animate attributeName="opacity" values="1;0.3;1" dur="2s" repeatCount="indefinite"/>
              </circle>
            </svg>
          </div>

          <div>
            <h1 style={{
              fontFamily: 'var(--font-outfit), sans-serif',
              fontSize: 22,
              fontWeight: 700,
              letterSpacing: '-0.02em',
              color: 'var(--foreground)',
              lineHeight: 1,
              marginBottom: 3,
            }}>
              ElasticMQ
            </h1>
            <p style={{
              fontSize: 12,
              color: 'var(--muted)',
              letterSpacing: '0.04em',
              lineHeight: 1,
            }}>
              Message Queue Monitor
            </p>
          </div>
        </div>

        <ThemeSwitcher />
      </div>

      {/* Divider */}
      <div style={{
        marginTop: 20,
        height: 1,
        background: 'linear-gradient(to right, var(--accent), var(--card-border) 40%, transparent)',
        opacity: 0.6,
      }} />
    </header>
  );
}
