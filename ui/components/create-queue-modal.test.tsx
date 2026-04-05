import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateQueueModal } from './create-queue-modal';

vi.mock('@/lib/actions', () => ({
  createQueue: vi.fn().mockResolvedValue('http://localhost:9324/queue/test-queue'),
}));

describe('CreateQueueModal', () => {
  const onClose = vi.fn();
  const onCreated = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the queue name input', () => {
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    expect(screen.getByPlaceholderText('my-queue')).toBeInTheDocument();
  });

  it('disables the Create Queue button when name is empty', () => {
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    expect(screen.getByRole('button', { name: /create queue/i })).toBeDisabled();
  });

  it('enables the Create Queue button after typing a name', async () => {
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    await userEvent.type(screen.getByPlaceholderText('my-queue'), 'my-queue');
    expect(screen.getByRole('button', { name: /create queue/i })).not.toBeDisabled();
  });

  it('hides the content-based deduplication toggle by default', () => {
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    expect(screen.queryByLabelText(/content-based deduplication/i)).not.toBeInTheDocument();
  });

  it('shows the content-based deduplication toggle when FIFO is enabled', async () => {
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    await userEvent.click(screen.getByLabelText(/fifo queue/i));
    expect(screen.getByLabelText(/content-based deduplication/i)).toBeInTheDocument();
  });

  it('calls createQueue with correct params on submit', async () => {
    const { createQueue } = await import('@/lib/actions');
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    await userEvent.type(screen.getByPlaceholderText('my-queue'), 'test-queue');
    await userEvent.click(screen.getByRole('button', { name: /create queue/i }));
    expect(createQueue).toHaveBeenCalledWith(expect.objectContaining({
      name: 'test-queue',
      fifo: false,
    }));
  });

  it('passes fifo: true when creating a FIFO queue', async () => {
    const { createQueue } = await import('@/lib/actions');
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    await userEvent.type(screen.getByPlaceholderText('my-queue'), 'my-queue');
    await userEvent.click(screen.getByLabelText(/fifo queue/i));
    await userEvent.click(screen.getByRole('button', { name: /create queue/i }));
    expect(createQueue).toHaveBeenCalledWith(expect.objectContaining({ fifo: true }));
  });

  it('calls onClose when Cancel is clicked', async () => {
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls onClose when the X button is clicked', async () => {
    render(<CreateQueueModal onClose={onClose} onCreated={onCreated} />);
    await userEvent.click(screen.getByRole('button', { name: /close/i }));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
