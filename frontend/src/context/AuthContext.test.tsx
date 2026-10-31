import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../api/auth';
import { refreshSession } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import ProtectedRoute from '../routes/ProtectedRoute';
import { getToken, saveSession, SESSION_HINT_KEY } from '../utils/sessionStorage';
import { AuthProvider } from './AuthContext';

jest.mock('../api/auth', () => ({ authApi: { logout: jest.fn() } }));
jest.mock('../api/client', () => ({
  refreshSession: jest.fn(),
  setUnauthorizedHandler: jest.fn(),
  setSessionUpdatedHandler: jest.fn(),
}));

const logout = jest.mocked(authApi.logout);
const refresh = jest.mocked(refreshSession);
const user = { id: '7', displayName: 'Kamal Silva', role: 'CUSTOMER' as const };

function Dashboard() {
  const { user: current, logout: signOut } = useAuth();
  return (
    <>
      <p>dashboard of {current?.displayName}</p>
      <button onClick={() => void signOut()}>Sign out</button>
    </>
  );
}

function renderApp() {
  render(
    <MemoryRouter initialEntries={['/customer']}>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/customer" element={<Dashboard />} />
          </Route>
          <Route path="/login" element={<p>login page</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

/** A previous visit: the user is stored, but this freshly loaded page has no access token. */
function previousVisit() {
  window.localStorage.setItem(SESSION_HINT_KEY, JSON.stringify(user));
}

describe('AuthProvider', () => {
  beforeEach(() => {
    refresh.mockReset();
    logout.mockReset();
    logout.mockResolvedValue(undefined);
  });

  it('restores the session with the cookie after a reload, showing a loader meanwhile', async () => {
    previousVisit();
    refresh.mockResolvedValue({ status: 'renewed', session: { token: 'fresh', user } });

    renderApp();

    expect(screen.getByText('Signing you in...')).toBeInTheDocument();
    expect(await screen.findByText('dashboard of Kamal Silva')).toBeInTheDocument();
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('sends the visitor to sign in when the session has ended', async () => {
    previousVisit();
    refresh.mockResolvedValue({ status: 'expired' });

    renderApp();

    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(window.localStorage.getItem(SESSION_HINT_KEY)).toBeNull();
  });

  it('offers to try again when the server cannot be reached, without signing out', async () => {
    previousVisit();
    refresh.mockResolvedValueOnce({ status: 'unavailable' });
    refresh.mockResolvedValueOnce({ status: 'renewed', session: { token: 'fresh', user } });

    renderApp();

    expect(await screen.findByText("Can't reach SwiftDrop")).toBeInTheDocument();
    expect(window.localStorage.getItem(SESSION_HINT_KEY)).not.toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    expect(await screen.findByText('dashboard of Kamal Silva')).toBeInTheDocument();
  });

  it('does not call the server when there was no previous session', async () => {
    renderApp();

    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(refresh).not.toHaveBeenCalled();
  });

  it('signing out ends the session on the server and forgets it here', async () => {
    saveSession({ token: 'access', user }, true);
    renderApp();

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(logout).toHaveBeenCalledTimes(1);
    expect(getToken()).toBeNull();
    expect(window.localStorage.getItem(SESSION_HINT_KEY)).toBeNull();
  });

  it('signs out locally even when the server cannot be told', async () => {
    logout.mockRejectedValue(new Error('offline'));
    saveSession({ token: 'access', user }, true);
    renderApp();

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('signing out in another tab signs this tab out too', async () => {
    saveSession({ token: 'access', user }, true);
    renderApp();
    expect(screen.getByText('dashboard of Kamal Silva')).toBeInTheDocument();

    act(() => {
      window.localStorage.removeItem(SESSION_HINT_KEY);
      window.dispatchEvent(new StorageEvent('storage', { key: SESSION_HINT_KEY, newValue: null }));
    });

    expect(await screen.findByText('login page')).toBeInTheDocument();
  });
});
