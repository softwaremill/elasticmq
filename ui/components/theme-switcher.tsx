'use client'

import { useTheme } from '@/lib/theme-provider';

export function ThemeSwitcher() {
  const { theme, setTheme } = useTheme();

  const options = [
    { value: 'light', label: '☀', title: 'Light' },
    { value: 'dark',  label: '☾', title: 'Dark'  },
    { value: 'auto',  label: '⬡', title: 'Auto'  },
  ] as const;

  return (
    <div
      style={{
        display: 'flex',
        background: 'var(--card-bg)',
        border: '1px solid var(--card-border)',
        borderRadius: 8,
        padding: 3,
        gap: 2,
      }}
    >
      {options.map(o => {
        const active = theme === o.value;
        return (
          <button
            key={o.value}
            onClick={() => setTheme(o.value)}
            title={o.title}
            style={{
              width: 30, height: 28,
              borderRadius: 5,
              border: 'none',
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: active ? 700 : 400,
              background: active ? 'var(--accent-dim)' : 'transparent',
              color: active ? 'var(--accent)' : 'var(--muted)',
              transition: 'all 150ms ease',
              outline: active ? '1px solid var(--accent)' : 'none',
              outlineOffset: -1,
            }}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
