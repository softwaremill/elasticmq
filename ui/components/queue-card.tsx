import Link from 'next/link';
import { QueueData } from '@/lib/types';

interface QueueCardProps {
  queue: QueueData;
}

export function QueueCard({ queue }: QueueCardProps) {
  return (
    <Link href={`/queues/${encodeURIComponent(queue.name)}`}>
      <div
        className="p-6 rounded-lg border cursor-pointer transition-all hover:shadow-lg"
        style={{
          backgroundColor: 'var(--card-bg)',
          borderColor: 'var(--card-border)',
        }}
      >
        <h3 className="text-xl font-semibold mb-4" style={{ color: 'var(--foreground)' }}>
          {queue.name}
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <p className="text-sm" style={{ color: 'var(--muted)' }}>
              Messages
            </p>
            <p className="text-2xl font-bold" style={{ color: 'var(--foreground)' }}>
              {queue.stats.approximateNumberOfMessages}
            </p>
          </div>
          <div>
            <p className="text-sm" style={{ color: 'var(--muted)' }}>
              Delayed
            </p>
            <p className="text-2xl font-bold" style={{ color: 'var(--foreground)' }}>
              {queue.stats.approximateNumberOfMessagesDelayed}
            </p>
          </div>
          <div>
            <p className="text-sm" style={{ color: 'var(--muted)' }}>
              In Flight
            </p>
            <p className="text-2xl font-bold" style={{ color: 'var(--foreground)' }}>
              {queue.stats.approximateNumberOfMessagesNotVisible}
            </p>
          </div>
        </div>
      </div>
    </Link>
  );
}
