import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import App from './App';
import ErrorBoundary from './components/feedback/ErrorBoundary';
import { AuthProvider } from './context/AuthContext';
import { SettingsProvider } from './context/SettingsContext';
import './index.css';

// A data router (rather than <BrowserRouter>) so pages can ask before navigating away with unsaved
// changes (useBlocker). Every URL goes to <App />, whose <Routes> still define the screens.
const router = createBrowserRouter([
  {
    path: '*',
    element: (
      <ErrorBoundary>
        <SettingsProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </SettingsProvider>
      </ErrorBoundary>
    ),
  },
]);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  </StrictMode>,
);
