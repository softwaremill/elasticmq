'use client'

import { useState } from 'react';
import Link from 'next/link';
import { QueueData } from '@/lib/types';
import { SendMessageModal } from './send-message-modal';

interface QueueCardProps {
  queue: QueueData;
}

export function QueueCard({ queue }: QueueCardProps) {
  const [showModal, setShowModal] = useState(false);

  return (
    <>
      <div
        className="rounded-lg border transition-all hover:shadow-lg"
        style={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)' }}
      >
        {/* Clickable area → queue details */}
        <Link href={`/queues/${encodeURIComponent(queue.name)}`} className="block p-6 pb-4">
          <h3 className="text-xl font-semibold mb-4" style={{ color: 'var(--foreground)' }}>
            {queue.name}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <p className="text-sm" style={{ color: 'var(--muted)' }}>Messages</p>
              <p className="text-2xl font-bold" style={{ color: 'var(--foreground)' }}>
                {queue.stats.approximateNumberOfMessages}
              </p>
            </div>
            <div>
              <p className="text-sm" style={{ color: 'var(--muted)' }}>Delayed</p>
              <p className="text-2xl font-bold" style={{ color: 'var(--foreground)' }}>
                {queue.stats.approximateNumberOfMessagesDelayed}
              </p>
            </div>
            <div>
              <p className="text-sm" style={{ color: 'var(--muted)' }}>In Flight</p>
              <p className="text-2xl font-bold" style={{ color: 'var(--foreground)' }}>
                {queue.stats.approximateNumberOfMessagesNotVisible}
              </p>
            </div>
          </div>
        </Link>

        {/* Action bar */}
        <div
          className="px-6 py-3 flex justify-end"
          style={{ borderTop: '1px solid var(--card-border)' }}
        >
          <button
            onClick={() => setShowModal(true)}
            className="px-3 py-1.5 rounded text-sm font-semibold transition-opacity hover:opacity-80"
            style={{ background: 'var(--accent)', color: '#fff' }}
          >
            Send Message
          </button>
        </div>
      </div>

      {showModal && (
        <SendMessageModal
          queueName={queue.name}
          queueUrl={queue.url}
          onClose={() => setShowModal(false)}
        />
      )}
    </>
  );
}
