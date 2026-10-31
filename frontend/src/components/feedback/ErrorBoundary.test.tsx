import { render, screen } from '@testing-library/react';
import ErrorBoundary from './ErrorBoundary';

function Broken(): never {
  throw new Error('render bug');
}

describe('ErrorBoundary', () => {
  it('shows its children when nothing goes wrong', () => {
    render(
      <ErrorBoundary>
        <p>page content</p>
      </ErrorBoundary>,
    );

    expect(screen.getByText('page content')).toBeInTheDocument();
  });

  it('replaces a crashed page with a friendly message and a way out', () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload Page' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Go to Home' })).toBeInTheDocument();
    expect(screen.queryByText('render bug')).not.toBeInTheDocument(); // no internals shown
    consoleError.mockRestore();
  });
});
