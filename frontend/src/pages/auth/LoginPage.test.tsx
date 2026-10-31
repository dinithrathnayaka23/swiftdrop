import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import { AuthProvider } from '../../context/AuthContext';
import { updateLoginPreferences } from '../../utils/loginPreferences';
import { clearSession } from '../../utils/sessionStorage';
import LoginPage from './LoginPage';

jest.mock('../../api/auth', () => ({
  authApi: {
    login: jest.fn(),
    resendVerification: jest.fn(),
    googleConfig: jest.fn(async () => ({ enabled: false, clientId: null })),
    loginWithGoogle: jest.fn(),
    registerCustomer: jest.fn(),
    registerMerchant: jest.fn(),
    requestPasswordReset: jest.fn(),
    confirmPasswordReset: jest.fn(),
  },
}));

const login = jest.mocked(authApi.login);

function authResponse(rememberMe: boolean) {
  return {
    token: 'jwt',
    expiresIn: 3600,
    rememberMe,
    user: { id: '1', displayName: 'BrightMart', role: 'MERCHANT' as const },
  };
}

function renderLogin() {
  render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/merchant" element={<p>merchant home</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function signIn(email: string, password: string) {
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Password'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));
}

describe('LoginPage', () => {
  beforeEach(() => {
    login.mockReset();
  });

  it('shows validation errors for an empty form without calling the API', async () => {
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));

    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('signs in and sends the user to their role home', async () => {
    login.mockResolvedValue(authResponse(false));
    renderLogin();
    await signIn('merchant@example.com', 'secret123');

    expect(await screen.findByText('merchant home')).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith({
      email: 'merchant@example.com',
      password: 'secret123',
      rememberMe: false,
    });
  });

  it('without "Remember me" keeps the session only for this browser session', async () => {
    login.mockResolvedValue(authResponse(false));
    renderLogin();
    expect(screen.getByLabelText('Remember me')).not.toBeChecked();
    await signIn('merchant@example.com', 'secret123');
    await screen.findByText('merchant home');

    // Only the user (no tokens) is stored, per browser session; the access token stays in memory.
    expect(window.sessionStorage.getItem('swiftdrop.user')).toContain('BrightMart');
    expect(window.sessionStorage.getItem('swiftdrop.user')).not.toContain('jwt');
    expect(window.localStorage.getItem('swiftdrop.user')).toBeNull();
  });

  it('with "Remember me" asks for a long session and keeps it after the browser closes', async () => {
    login.mockResolvedValue(authResponse(true));
    renderLogin();
    await userEvent.click(screen.getByLabelText('Remember me'));
    await signIn('merchant@example.com', 'secret123');
    await screen.findByText('merchant home');

    expect(login).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: true }));
    expect(window.localStorage.getItem('swiftdrop.user')).toContain('BrightMart');
    expect(window.sessionStorage.getItem('swiftdrop.user')).toBeNull();
  });

  it('after signing out, the form remembers "Remember me" and the email (never the password)', async () => {
    login.mockResolvedValue(authResponse(true));
    renderLogin();
    await userEvent.click(screen.getByLabelText('Remember me'));
    await signIn('merchant@example.com', 'secret123');
    await screen.findByText('merchant home');

    // Sign out and come back to the login page.
    cleanup();
    clearSession();
    renderLogin();

    expect(screen.getByLabelText('Remember me')).toBeChecked();
    expect(screen.getByLabelText('Email')).toHaveValue('merchant@example.com');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.getByLabelText('Password')).toHaveFocus();
    expect(window.localStorage.getItem('swiftdrop.login')).not.toContain('secret123');
  });

  it('signing in with "Remember me" unticked forgets the saved email', async () => {
    updateLoginPreferences(true, 'old@example.com');
    login.mockResolvedValue(authResponse(false));
    renderLogin();
    expect(screen.getByLabelText('Remember me')).toBeChecked();

    await userEvent.click(screen.getByLabelText('Remember me'));
    await userEvent.type(screen.getByLabelText('Password'), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign In' }));
    await screen.findByText('merchant home');

    expect(login).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: false }));
    expect(window.localStorage.getItem('swiftdrop.login')).toBeNull();
  });

  it('shows a generic error for wrong credentials (401)', async () => {
    login.mockImplementation(async () => {
      throw new ApiRequestError('Invalid email or password', { status: 401 });
    });
    renderLogin();
    await signIn('customer@example.com', 'wrongpassword1');

    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument();
  });

  it('explains an unconfirmed email and can send a new confirmation link', async () => {
    login.mockImplementation(async () => {
      throw new ApiRequestError('Please confirm your email address first.', {
        status: 403,
        code: 'EMAIL_NOT_VERIFIED',
      });
    });
    jest.mocked(authApi.resendVerification).mockResolvedValue(undefined);
    renderLogin();
    await signIn('new@example.com', 'secret123');

    expect(await screen.findByText('Confirm your email first')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Resend confirmation email' }));

    expect(authApi.resendVerification).toHaveBeenCalledWith({ email: 'new@example.com' });
    expect(await screen.findByText('We sent a new confirmation link.')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Resend confirmation email in \d+s/ }),
    ).toBeDisabled();
  });

  it('shows the backend message for a merchant awaiting approval (403)', async () => {
    login.mockImplementation(async () => {
      throw new ApiRequestError('Your merchant account is awaiting approval.', {
        status: 403,
        code: 'ACCOUNT_PENDING_APPROVAL',
      });
    });
    renderLogin();
    await signIn('merchant@example.com', 'secret123');

    expect(
      await screen.findByText('Your merchant account is awaiting approval.'),
    ).toBeInTheDocument();
  });
});
