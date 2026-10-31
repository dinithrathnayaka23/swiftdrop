import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { authApi } from '../../api/auth';
import ForgotPasswordPage, { RESEND_COOLDOWN_SECONDS } from './ForgotPasswordPage';

jest.mock('../../api/auth', () => ({
  authApi: {
    login: jest.fn(),
    refresh: jest.fn(),
    registerCustomer: jest.fn(),
    registerMerchant: jest.fn(),
    requestPasswordReset: jest.fn(),
    confirmPasswordReset: jest.fn(),
  },
}));

const requestPasswordReset = jest.mocked(authApi.requestPasswordReset);

function renderPage() {
  render(
    <MemoryRouter>
      <ForgotPasswordPage />
    </MemoryRouter>,
  );
}

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    requestPasswordReset.mockReset();
    requestPasswordReset.mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('validates the email before calling the API', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.click(screen.getByRole('button', { name: 'Send Reset Link' }));

    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(requestPasswordReset).not.toHaveBeenCalled();
  });

  it('confirms without revealing whether the account exists, then allows a resend after the cooldown', async () => {
    jest.useFakeTimers({ advanceTimers: true });
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    renderPage();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.click(screen.getByRole('button', { name: 'Send Reset Link' }));

    expect(await screen.findByText('Check your email')).toBeInTheDocument();
    expect(screen.getByText('jane@example.com')).toBeInTheDocument();
    expect(requestPasswordReset).toHaveBeenCalledWith({ email: 'jane@example.com' });

    const resend = screen.getByRole('button', { name: /Resend email in \d+s/ });
    expect(resend).toBeDisabled();

    await act(async () => {
      jest.advanceTimersByTime((RESEND_COOLDOWN_SECONDS + 1) * 1000);
    });
    const ready = screen.getByRole('button', { name: 'Resend email' });
    expect(ready).toBeEnabled();

    await user.click(ready);
    expect(await screen.findByText('We sent the link again.')).toBeInTheDocument();
    expect(requestPasswordReset).toHaveBeenCalledTimes(2);
  });
});
