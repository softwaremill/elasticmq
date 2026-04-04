import { QueueList } from '@/components/queue-list';
import { PageHeader } from '@/components/page-header';

export default function Home() {
  return (
    <div className="min-h-screen" style={{ padding: '32px 24px' }}>
      <div style={{ maxWidth: 860, margin: '0 auto' }}>
        <PageHeader />
        <QueueList />
      </div>
    </div>
  );
}
