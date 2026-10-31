import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthProvider } from '../context/AuthContext';
import { saveSession } from '../utils/sessionStorage';
import GuestRoute from './GuestRoute';

function renderLogin() {
  render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route element={<GuestRoute />}>
            <Route path="/login" element={<p>login form</p>} />
          </Route>
          <Route path="/merchant" element={<p>merchant home</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('GuestRoute', () => {
  it('shows the sign-in page to visitors', () => {
    renderLogin();
    expect(screen.getByText('login form')).toBeInTheDocument();
  });

  it("sends a signed-in user to their role's home instead", () => {
    saveSession({ token: 't', user: { id: '1', displayName: 'Shop', role: 'MERCHANT' } }, false);
    renderLogin();
    expect(screen.getByText('merchant home')).toBeInTheDocument();
  });
});
