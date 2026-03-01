import { QueueDetails } from '@/components/queue-details';

interface QueuePageProps {
  params: Promise<{
    name: string;
  }>;
}

export default async function QueuePage({ params }: QueuePageProps) {
  const { name } = await params;
  const queueName = decodeURIComponent(name);

  return (
    <div className="min-h-screen" style={{ padding: '32px 24px' }}>
      <div style={{ maxWidth: 860, margin: '0 auto' }}>
        <QueueDetails queueName={queueName} />
      </div>
    </div>
  );
}
