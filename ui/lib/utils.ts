export function formatTimestamp(value: string): string {
  const timestamp = parseInt(value);
  if (isNaN(timestamp)) return value;
  return new Date(timestamp * 1000).toISOString();
}

export function formatRedrivePolicy(value: string): string {
  try {
    const policy = JSON.parse(value);
    return `DLQ: ${policy.deadLetterTargetArn || 'N/A'}, MaxReceiveCount: ${policy.maxReceiveCount || 'N/A'}`;
  } catch {
    return value;
  }
}

export function formatAttributeValue(key: string, value: string): string {
  if (key === 'CreatedTimestamp' || key === 'LastModifiedTimestamp') {
    return formatTimestamp(value);
  }
  if (key === 'RedrivePolicy') {
    return formatRedrivePolicy(value);
  }
  return value;
}

// Attributes that are shown as stats and should be hidden from attributes table
export const HIDDEN_ATTRIBUTES = [
  'ApproximateNumberOfMessages',
  'ApproximateNumberOfMessagesDelayed',
  'ApproximateNumberOfMessagesNotVisible',
];
