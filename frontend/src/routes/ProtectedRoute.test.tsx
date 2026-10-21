import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import type { Role } from '../types/auth';
import { saveSession } from '../utils/sessionStorage';
import ProtectedRoute from './ProtectedRoute';
import { ROUTER_FUTURE_FLAGS } from './routerConfig';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]} future={ROUTER_FUTURE_FLAGS}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>login page</p>} />
          <Route path="/unauthorized" element={<p>unauthorized page</p>} />
          <Route element={<ProtectedRoute allowedRoles={['COURIER']} />}>
            <Route path="/courier" element={<p>courier home</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function signInAs(role: Role) {
  saveSession({ token: 'test-token', user: { id: '1', displayName: 'Test', role } });
}

describe('ProtectedRoute', () => {
  it('redirects anonymous users to login', () => {
    renderAt('/courier');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('renders the page for an allowed role', () => {
    signInAs('COURIER');
    renderAt('/courier');
    expect(screen.getByText('courier home')).toBeInTheDocument();
  });

  it('sends users with the wrong role to /unauthorized', () => {
    signInAs('CUSTOMER');
    renderAt('/courier');
    expect(screen.getByText('unauthorized page')).toBeInTheDocument();
  });
});
