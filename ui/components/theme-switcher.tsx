'use client'

import { useState, useEffect } from 'react';
import React from 'react';
import { Sun, Moon, Hexagon } from 'lucide-react';
import { useTheme } from '@/lib/theme-provider';

export function ThemeSwitcher() {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => { const raf = requestAnimationFrame(() => setMounted(true)); return () => cancelAnimationFrame(raf); }, []);

  const options: { value: 'light' | 'dark' | 'auto'; label: React.ReactNode; title: string }[] = [
    { value: 'light', label: <Sun size={13} />,     title: 'Light' },
    { value: 'dark',  label: <Moon size={13} />,    title: 'Dark'  },
    { value: 'auto',  label: <Hexagon size={13} />, title: 'Auto'  },
  ];

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
        const active = mounted && theme === o.value;
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
              display: 'flex', alignItems: 'center', justifyContent: 'center',
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
