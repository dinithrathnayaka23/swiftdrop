import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import ResetPasswordPage from './ResetPasswordPage';

jest.mock('../../api/auth', () => ({
  authApi: { confirmPasswordReset: jest.fn() },
}));

const confirmPasswordReset = jest.mocked(authApi.confirmPasswordReset);

function renderAt(url: string) {
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function choosePassword(password: string, confirmation = password) {
  await userEvent.type(screen.getByLabelText('New Password'), password);
  await userEvent.type(screen.getByLabelText('Confirm New Password'), confirmation);
  await userEvent.click(screen.getByRole('button', { name: 'Update Password' }));
}

describe('ResetPasswordPage', () => {
  it('shows "link invalid" straight away when the link has no token', () => {
    renderAt('/reset-password');

    expect(screen.getByText('Link invalid or expired')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Request a New Link' })).toBeInTheDocument();
  });

  it('validates the new password and its confirmation before calling the API', async () => {
    renderAt('/reset-password?token=abc');

    await choosePassword('short', 'different');

    expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument();
    expect(confirmPasswordReset).not.toHaveBeenCalled();
  });

  it('sends the token from the link with the new password, then confirms', async () => {
    confirmPasswordReset.mockResolvedValue(undefined);
    renderAt('/reset-password?token=abc.def');

    await choosePassword('newpass2026');

    expect(await screen.findByText('Password updated')).toBeInTheDocument();
    expect(confirmPasswordReset).toHaveBeenCalledWith({
      token: 'abc.def',
      newPassword: 'newpass2026',
    });
  });

  it('switches to "link invalid" when the backend says the token is used or expired', async () => {
    confirmPasswordReset.mockRejectedValue(
      new ApiRequestError('invalid', { status: 400, code: 'INVALID_RESET_TOKEN' }),
    );
    renderAt('/reset-password?token=used');

    await choosePassword('newpass2026');

    expect(await screen.findByText('Link invalid or expired')).toBeInTheDocument();
  });

  it('shows other failures above the form', async () => {
    confirmPasswordReset.mockRejectedValue(new ApiRequestError('Server unavailable'));
    renderAt('/reset-password?token=abc');

    await choosePassword('newpass2026');

    expect(await screen.findByText('Server unavailable')).toBeInTheDocument();
  });
});
