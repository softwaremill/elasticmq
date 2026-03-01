'use client'

import { ThemeSwitcher } from './theme-switcher';

export function PageHeader() {
  return (
    <header className="mb-8">
      <div className="flex justify-between items-start mb-4">
        <div>
          <h1 className="text-4xl font-bold mb-2" style={{ color: 'var(--foreground)' }}>
            ElasticMQ
          </h1>
          <p style={{ color: 'var(--muted)' }}>
            Monitor and manage your message queues
          </p>
        </div>
        <ThemeSwitcher />
      </div>
    </header>
  );
}
