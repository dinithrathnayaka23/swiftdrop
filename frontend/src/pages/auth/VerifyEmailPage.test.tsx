import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import VerifyEmailPage from './VerifyEmailPage';

jest.mock('../../api/auth', () => ({
  authApi: { confirmEmailVerification: jest.fn() },
}));

const confirm = jest.mocked(authApi.confirmEmailVerification);

function renderAt(url: string) {
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('VerifyEmailPage', () => {
  beforeEach(() => {
    confirm.mockReset();
  });

  it('confirms the token from the link once and invites the customer to sign in', async () => {
    confirm.mockResolvedValue({ awaitingApproval: false });
    renderAt('/verify-email?token=customer-token');

    expect(screen.getByText('Confirming your email...')).toBeInTheDocument();
    expect(
      await screen.findByText('Your account is ready. You can now sign in.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign In' })).toHaveAttribute('href', '/login');
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(confirm).toHaveBeenCalledWith({ token: 'customer-token' });
  });

  it('tells a merchant that approval is still pending', async () => {
    confirm.mockResolvedValue({ awaitingApproval: true });
    renderAt('/verify-email?token=merchant-token');

    expect(await screen.findByText(/waiting for approval/)).toBeInTheDocument();
  });

  it('explains an invalid or expired link', async () => {
    confirm.mockRejectedValue(
      new ApiRequestError('invalid', { status: 400, code: 'INVALID_VERIFICATION_TOKEN' }),
    );
    renderAt('/verify-email?token=expired-token');

    expect(await screen.findByText('Link invalid or expired')).toBeInTheDocument();
  });

  it('shows a link without a token as invalid, without calling the server', () => {
    renderAt('/verify-email');

    expect(screen.getByText('Link invalid or expired')).toBeInTheDocument();
    expect(confirm).not.toHaveBeenCalled();
  });

  it('asks to try again when the server is unreachable', async () => {
    confirm.mockRejectedValue(new ApiRequestError('Unable to reach the server.'));
    renderAt('/verify-email?token=offline-token');

    expect(await screen.findByText("We couldn't confirm your email")).toBeInTheDocument();
  });
});
