import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { AuthProvider } from '../../context/AuthContext';
import { clearSession } from '../../utils/sessionStorage';
import AdminLoginPage from './AdminLoginPage';

jest.mock('../../api/auth', () => ({
  authApi: { login: jest.fn(), logout: jest.fn() },
}));

const login = jest.mocked(authApi.login);

describe('AdminLoginPage', () => {
  beforeEach(() => {
    login.mockReset();
    clearSession();
    localStorage.clear();
  });

  it('signs an admin in through the admin portal', async () => {
    login.mockResolvedValue({
      token: 'jwt',
      expiresIn: 900,
      rememberMe: true,
      user: { id: '1', displayName: 'Admin', role: 'DISPATCHER_ADMIN' },
    });
    render(
      <MemoryRouter initialEntries={['/admin/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/admin/login" element={<AdminLoginPage />} />
            <Route path="/dispatcher/assignments" element={<p>assignments</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Admin Portal' })).toBeInTheDocument();
    // The admin password comes from .env, so there is no email reset.
    expect(screen.queryByRole('link', { name: 'Forgot Password?' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Email'), 'admin@swiftdrop.lk');
    await userEvent.type(screen.getByLabelText('Password'), 'admin-pass-2026');
    await userEvent.click(screen.getByLabelText('Remember me'));
    await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));

    expect(await screen.findByText('assignments')).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith(
      expect.objectContaining({ portal: 'ADMIN', rememberMe: true }),
    );
  });
});
