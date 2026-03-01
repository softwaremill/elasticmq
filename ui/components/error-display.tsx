interface ErrorDisplayProps {
  message: string;
  onRetry?: () => void;
}

export function ErrorDisplay({ message, onRetry }: ErrorDisplayProps) {
  return (
    <div
      className="p-6 rounded-lg border text-center"
      style={{
        backgroundColor: 'var(--card-bg)',
        borderColor: 'var(--error)',
      }}
    >
      <div className="mb-4">
        <svg
          className="w-12 h-12 mx-auto"
          style={{ color: 'var(--error)' }}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
      </div>
      <h3 className="text-lg font-semibold mb-2" style={{ color: 'var(--error)' }}>
        Error
      </h3>
      <p className="mb-4" style={{ color: 'var(--muted)' }}>
        {message}
      </p>
      {message.includes('localhost:9324') && (
        <p className="text-sm mb-4" style={{ color: 'var(--muted)' }}>
          Make sure ElasticMQ is running on localhost:9324
        </p>
      )}
      {onRetry && (
        <button
          onClick={onRetry}
          className="px-4 py-2 rounded font-medium transition-colors"
          style={{
            backgroundColor: 'var(--accent)',
            color: 'white',
          }}
        >
          Retry
        </button>
      )}
    </div>
  );
}
