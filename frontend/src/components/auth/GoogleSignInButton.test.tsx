import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import { AuthProvider } from '../../context/AuthContext';
import LoginPage from '../../pages/auth/LoginPage';
import { loadSession } from '../../utils/sessionStorage';
import type { GoogleIdentityApi } from '../../utils/googleIdentity';

jest.mock('../../api/auth', () => ({
  authApi: {
    login: jest.fn(),
    googleConfig: jest.fn(async () => ({
      enabled: true,
      clientId: 'test.apps.googleusercontent.com',
    })),
    loginWithGoogle: jest.fn(),
  },
}));

const loginWithGoogle = jest.mocked(authApi.loginWithGoogle);

// Stands in for Google's script: renders a plain button that "returns" a credential.
let pickGoogleAccount: () => void = () => {};
const fakeGoogle: GoogleIdentityApi = {
  initialize: jest.fn((config) => {
    pickGoogleAccount = () => config.callback({ credential: 'google-id-token' });
  }),
  renderButton: jest.fn((parent: HTMLElement) => {
    const button = document.createElement('button');
    button.textContent = 'Sign in with Google';
    button.type = 'button';
    button.onclick = () => pickGoogleAccount();
    parent.replaceChildren(button);
  }),
};

function renderLogin() {
  render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/customer" element={<p>customer home</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('GoogleSignInButton', () => {
  beforeEach(() => {
    window.google = { accounts: { id: fakeGoogle } };
    loginWithGoogle.mockReset();
  });

  afterEach(() => {
    delete window.google;
  });

  it("renders Google's button with our client ID and signs in with the returned credential", async () => {
    loginWithGoogle.mockResolvedValue({
      token: 'jwt',
      expiresIn: 3600,
      rememberMe: true,
      user: { id: '7', displayName: 'Kamal Silva', role: 'CUSTOMER' },
    });
    renderLogin();
    await userEvent.click(screen.getByLabelText('Remember me'));

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in with Google' }));

    expect(fakeGoogle.initialize).toHaveBeenCalledWith(
      expect.objectContaining({ client_id: 'test.apps.googleusercontent.com' }),
    );
    await screen.findByText('customer home');
    expect(loginWithGoogle).toHaveBeenCalledWith({
      credential: 'google-id-token',
      rememberMe: true,
    });
    expect(loadSession()?.user.displayName).toBe('Kamal Silva');
  });

  it('shows the backend message when Google sign-in is refused', async () => {
    loginWithGoogle.mockImplementation(async () => {
      throw new ApiRequestError('Your merchant account is awaiting approval.', { status: 403 });
    });
    renderLogin();

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in with Google' }));

    expect(
      await screen.findByText('Your merchant account is awaiting approval.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign In' })).toBeEnabled());
  });
});
