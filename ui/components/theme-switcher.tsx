'use client'

import { useTheme } from '@/lib/theme-provider';

export function ThemeSwitcher() {
  const { theme, setTheme } = useTheme();

  const themes = [
    { value: 'light', label: 'Light', icon: '☀️' },
    { value: 'dark', label: 'Dark', icon: '🌙' },
    { value: 'auto', label: 'Auto', icon: '💻' },
  ] as const;

  return (
    <div className="flex gap-2 items-center">
      <span className="text-sm mr-2" style={{ color: 'var(--muted)' }}>
        Theme:
      </span>
      <div
        className="flex rounded-lg p-1"
        style={{
          backgroundColor: 'var(--card-bg)',
          borderColor: 'var(--card-border)',
          border: '1px solid',
        }}
      >
        {themes.map((t) => (
          <button
            key={t.value}
            onClick={() => setTheme(t.value)}
            className="px-3 py-1.5 rounded text-sm font-medium transition-all"
            style={{
              backgroundColor: theme === t.value ? 'var(--accent)' : 'transparent',
              color: theme === t.value ? 'white' : 'var(--foreground)',
            }}
            title={t.label}
          >
            <span className="mr-1.5">{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>
    </div>
  );
}
