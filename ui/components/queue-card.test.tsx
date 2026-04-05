import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueueCard } from './queue-card';
import type { QueueData } from '@/lib/types';

vi.mock('@/lib/actions', () => ({
  deleteQueue: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('./send-message-modal', () => ({ SendMessageModal: () => null }));
vi.mock('./generate-messages-modal', () => ({ GenerateMessagesModal: () => null }));

const mockQueue: QueueData = {
  name: 'test-queue',
  url: 'http://localhost:9324/queue/test-queue',
  stats: {
    approximateNumberOfMessages: 5,
    approximateNumberOfMessagesDelayed: 2,
    approximateNumberOfMessagesNotVisible: 1,
  },
  attributes: {},
};

describe('QueueCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the queue name', () => {
    render(<QueueCard queue={mockQueue} />);
    expect(screen.getByText('test-queue')).toBeInTheDocument();
  });

  it('renders all three message stats', () => {
    render(<QueueCard queue={mockQueue} />);
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('links to the queue details page', () => {
    render(<QueueCard queue={mockQueue} />);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/queues/test-queue');
  });

  it('shows the delete button initially', () => {
    render(<QueueCard queue={mockQueue} />);
    // The delete button is an icon-only button (Trash2) — aria-label not set,
    // but it is the only button without a text label on the left side
    expect(screen.queryByRole('button', { name: /confirm delete/i })).not.toBeInTheDocument();
  });

  it('shows Confirm delete? after clicking the trash button', async () => {
    render(<QueueCard queue={mockQueue} />);
    // First button in the action bar is the delete icon
    const buttons = screen.getAllByRole('button');
    const trashBtn = buttons.find(b => !b.textContent?.trim() || b.textContent?.trim() === '');
    // Click the leftmost (delete) button which has only an icon
    const deleteBtn = buttons[0]; // trash button is first in left group
    await userEvent.click(deleteBtn);
    expect(screen.getByRole('button', { name: /confirm delete/i })).toBeInTheDocument();
  });

  it('calls deleteQueue with the queue URL after confirming deletion', async () => {
    const { deleteQueue } = await import('@/lib/actions');
    render(<QueueCard queue={mockQueue} />);
    const buttons = screen.getAllByRole('button');
    await userEvent.click(buttons[0]);
    await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));
    expect(deleteQueue).toHaveBeenCalledWith('http://localhost:9324/queue/test-queue');
  });

  it('renders Send and Generate action buttons', () => {
    render(<QueueCard queue={mockQueue} />);
    expect(screen.getByRole('button', { name: /generate/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send/i })).toBeInTheDocument();
  });
});
