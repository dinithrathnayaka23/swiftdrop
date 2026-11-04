import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
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

  it('inside a layout, replaces only the page and lets the user try again', async () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    let broken = true;
    function Flaky() {
      if (broken) throw new Error('render bug');
      return <p>page content</p>;
    }

    render(
      <ErrorBoundary variant="page">
        <Flaky />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Something Went Wrong')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reload Page' })).not.toBeInTheDocument();

    broken = false;
    await userEvent.click(screen.getByRole('button', { name: 'Try Again' }));

    expect(screen.getByText('page content')).toBeInTheDocument();
    consoleError.mockRestore();
  });

  it('clears the error when the reset key changes (another page was opened)', async () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    function App() {
      const [page, setPage] = useState('broken');
      return (
        <>
          <button onClick={() => setPage('fine')}>open other page</button>
          <ErrorBoundary variant="page" resetKey={page}>
            {page === 'broken' ? <Broken /> : <p>other page</p>}
          </ErrorBoundary>
        </>
      );
    }

    render(<App />);
    expect(screen.getByText('Something Went Wrong')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'open other page' }));

    expect(screen.getByText('other page')).toBeInTheDocument();
    consoleError.mockRestore();
  });
});
