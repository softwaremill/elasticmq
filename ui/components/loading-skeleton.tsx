export function LoadingSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="p-6 rounded-lg border animate-pulse"
          style={{
            backgroundColor: 'var(--card-bg)',
            borderColor: 'var(--card-border)',
          }}
        >
          <div className="h-6 bg-gray-300 dark:bg-gray-700 rounded w-1/3 mb-4"></div>
          <div className="space-y-2">
            <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-1/4"></div>
            <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-1/4"></div>
            <div className="h-4 bg-gray-300 dark:bg-gray-700 rounded w-1/4"></div>
          </div>
        </div>
      ))}
    </div>
  );
}
