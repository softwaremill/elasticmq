import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorDisplay } from './error-display';

describe('ErrorDisplay', () => {
  it('renders the error message and heading', () => {
    render(<ErrorDisplay message="Connection refused" />);
    expect(screen.getByText('Connection Error')).toBeInTheDocument();
    expect(screen.getByText('Connection refused')).toBeInTheDocument();
  });

  it('does not render a Retry button when onRetry is not provided', () => {
    render(<ErrorDisplay message="some error" />);
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
  });

  it('renders a Retry button when onRetry is provided', () => {
    render(<ErrorDisplay message="some error" onRetry={vi.fn()} />);
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('calls onRetry when the Retry button is clicked', async () => {
    const onRetry = vi.fn();
    render(<ErrorDisplay message="some error" onRetry={onRetry} />);
    await userEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
