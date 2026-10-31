import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import { AuthProvider } from '../../context/AuthContext';
import { clearSession } from '../../utils/sessionStorage';
import CourierLoginPage from './CourierLoginPage';

jest.mock('../../api/auth', () => ({
  authApi: { login: jest.fn(), logout: jest.fn() },
}));

const login = jest.mocked(authApi.login);

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/courier/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/courier/login" element={<CourierLoginPage />} />
          <Route path="/courier" element={<p>courier home</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function signIn() {
  await userEvent.type(screen.getByLabelText('Email'), 'rider@swiftdrop.lk');
  await userEvent.type(screen.getByLabelText('Password'), 'parcel2026');
  await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));
}

describe('CourierLoginPage', () => {
  beforeEach(() => {
    login.mockReset();
    clearSession();
    localStorage.clear();
  });

  it('signs a courier in through the courier portal and opens their jobs', async () => {
    login.mockResolvedValue({
      token: 'jwt',
      expiresIn: 900,
      rememberMe: false,
      user: { id: '7', displayName: 'Kasun', role: 'COURIER' },
    });
    renderPage();

    await signIn();

    expect(await screen.findByText('courier home')).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith({
      email: 'rider@swiftdrop.lk',
      password: 'parcel2026',
      rememberMe: false,
      portal: 'COURIER',
    });
  });

  it('has no sign-up or Google option: couriers are added by an admin', () => {
    renderPage();

    expect(screen.getByText(/created by your SwiftDrop admin/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('link', { name: /Create Account/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Google/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Forgot Password?' })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
  });

  it('points other accounts to their own sign-in page', async () => {
    login.mockRejectedValue(
      new ApiRequestError(
        'This sign-in page is for SwiftDrop staff. Please use the main sign-in page.',
        {
          status: 403,
          code: 'WRONG_SIGN_IN_PAGE',
        },
      ),
    );
    renderPage();

    await signIn();

    expect(await screen.findByText(/for SwiftDrop staff/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Main sign-in' })).toHaveAttribute('href', '/login');
    expect(screen.getByRole('link', { name: 'Admin portal' })).toHaveAttribute(
      'href',
      '/admin/login',
    );
  });

  it('shows the generic message for a wrong password', async () => {
    login.mockRejectedValue(new ApiRequestError('Invalid email or password', { status: 401 }));
    renderPage();

    await signIn();

    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Main sign-in' })).not.toBeInTheDocument();
  });
});
