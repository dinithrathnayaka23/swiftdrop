import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import type { Role } from '../types/auth';
import { saveSession } from '../utils/sessionStorage';
import ProtectedRoute from './ProtectedRoute';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>login page</p>} />
          <Route path="/admin/login" element={<p>admin login page</p>} />
          <Route path="/unauthorized" element={<p>unauthorized page</p>} />
          <Route element={<ProtectedRoute allowedRoles={['COURIER']} />}>
            <Route path="/courier" element={<p>courier home</p>} />
          </Route>
          <Route
            element={
              <ProtectedRoute allowedRoles={['DISPATCHER_ADMIN']} loginPath="/admin/login" />
            }
          >
            <Route path="/admin" element={<p>admin home</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function signInAs(role: Role) {
  saveSession({ token: 'test-token', user: { id: '1', displayName: 'Test', role } }, true);
}

describe('ProtectedRoute', () => {
  it('redirects anonymous users to login', () => {
    renderAt('/courier');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it("sends anonymous users to the area's own sign-in page when one is given", () => {
    renderAt('/admin');
    expect(screen.getByText('admin login page')).toBeInTheDocument();
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
