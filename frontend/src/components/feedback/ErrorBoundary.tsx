import { TriangleAlert } from 'lucide-react';
import { Component, type ErrorInfo, type ReactNode } from 'react';
import Button from '../ui/Button';
import StateView from './StateView';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  failed: boolean;
}

/**
 * Last line of defence for bugs while rendering: instead of a blank page, show a friendly message
 * with a way out. (API errors are handled by each page; this is only for unexpected crashes.)
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unexpected error while rendering', error, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
        <StateView
          icon={TriangleAlert}
          tone="danger"
          title="Something went wrong"
          message="An unexpected error occurred. Reloading the page usually fixes it."
          actions={
            <>
              <Button onClick={() => window.location.reload()}>Reload Page</Button>
              <Button variant="outline" onClick={() => window.location.assign('/')}>
                Go to Home
              </Button>
            </>
          }
        />
      </div>
    );
  }
}
