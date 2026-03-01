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
    <div className="min-h-screen p-8">
      <div className="max-w-6xl mx-auto">
        <QueueDetails queueName={queueName} />
      </div>
    </div>
  );
}
