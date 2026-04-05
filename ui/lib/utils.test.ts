import { describe, it, expect } from 'vitest';
import { formatTimestamp, formatRedrivePolicy, formatAttributeValue, HIDDEN_ATTRIBUTES } from './utils';

describe('formatTimestamp', () => {
  it('converts a Unix timestamp to an ISO string', () => {
    expect(formatTimestamp('1704067200')).toBe('2024-01-01T00:00:00.000Z');
  });

  it('returns the original value when input is not a number', () => {
    expect(formatTimestamp('not-a-number')).toBe('not-a-number');
  });

  it('returns the original value for an empty string', () => {
    expect(formatTimestamp('')).toBe('');
  });
});

describe('formatRedrivePolicy', () => {
  it('formats a valid redrive policy JSON', () => {
    const policy = JSON.stringify({
      deadLetterTargetArn: 'arn:aws:sqs:us-east-1:123456789012:dlq',
      maxReceiveCount: 5,
    });
    expect(formatRedrivePolicy(policy)).toBe(
      'DLQ: arn:aws:sqs:us-east-1:123456789012:dlq, MaxReceiveCount: 5'
    );
  });

  it('shows N/A for missing fields in redrive policy', () => {
    expect(formatRedrivePolicy('{}')).toBe('DLQ: N/A, MaxReceiveCount: N/A');
  });

  it('returns the original value for invalid JSON', () => {
    expect(formatRedrivePolicy('invalid-json')).toBe('invalid-json');
  });
});

describe('formatAttributeValue', () => {
  it('formats CreatedTimestamp as an ISO date string', () => {
    const result = formatAttributeValue('CreatedTimestamp', '1704067200');
    expect(result).toBe('2024-01-01T00:00:00.000Z');
  });

  it('formats LastModifiedTimestamp as an ISO date string', () => {
    const result = formatAttributeValue('LastModifiedTimestamp', '1704067200');
    expect(result).toBe('2024-01-01T00:00:00.000Z');
  });

  it('formats RedrivePolicy as a human-readable string', () => {
    const policy = JSON.stringify({ deadLetterTargetArn: 'arn:dlq', maxReceiveCount: 3 });
    expect(formatAttributeValue('RedrivePolicy', policy)).toBe('DLQ: arn:dlq, MaxReceiveCount: 3');
  });

  it('returns the value unchanged for unknown attribute keys', () => {
    expect(formatAttributeValue('VisibilityTimeout', '30')).toBe('30');
  });
});

describe('HIDDEN_ATTRIBUTES', () => {
  it('includes the three message count attributes', () => {
    expect(HIDDEN_ATTRIBUTES).toContain('ApproximateNumberOfMessages');
    expect(HIDDEN_ATTRIBUTES).toContain('ApproximateNumberOfMessagesDelayed');
    expect(HIDDEN_ATTRIBUTES).toContain('ApproximateNumberOfMessagesNotVisible');
  });
});
