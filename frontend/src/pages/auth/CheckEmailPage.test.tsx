import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import CheckEmailPage from './CheckEmailPage';

jest.mock('../../api/auth', () => ({
  authApi: { resendVerification: jest.fn() },
}));

const resend = jest.mocked(authApi.resendVerification);

function renderPage(state: unknown) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/signup/check-email', state }]}>
      <Routes>
        <Route path="/signup/check-email" element={<CheckEmailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('CheckEmailPage', () => {
  beforeEach(() => {
    resend.mockReset();
  });

  it('names the address and waits a minute before allowing a resend', () => {
    renderPage({ email: 'jane@example.com' });

    expect(screen.getByText('jane@example.com')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Resend confirmation email in \d+s/ }),
    ).toBeDisabled();
    expect(screen.getByRole('link', { name: 'Go to Sign In' })).toBeInTheDocument();
  });

  it('can resend after the countdown and reports errors', async () => {
    jest.useFakeTimers({ advanceTimers: true });
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    resend.mockRejectedValue(
      new ApiRequestError('Too many requests. Please try again in 5 minutes.'),
    );
    renderPage({ email: 'jane@example.com' });

    jest.advanceTimersByTime(61_000);
    await user.click(await screen.findByRole('button', { name: 'Resend confirmation email' }));

    expect(resend).toHaveBeenCalledWith({ email: 'jane@example.com' });
    expect(
      await screen.findByText('Too many requests. Please try again in 5 minutes.'),
    ).toBeInTheDocument();
    jest.useRealTimers();
  });

  it('still explains what to do when opened without the sign-up details', () => {
    renderPage(null);

    expect(screen.getByText(/sent you a confirmation link/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Resend/ })).not.toBeInTheDocument();
  });
});
