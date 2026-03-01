import { QueueList } from '@/components/queue-list';
import { PageHeader } from '@/components/page-header';

export default function Home() {
  return (
    <div className="min-h-screen p-8">
      <div className="max-w-6xl mx-auto">
        <PageHeader />
        <QueueList />
      </div>
    </div>
  );
}
