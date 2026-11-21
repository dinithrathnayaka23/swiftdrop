import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useAuth } from '../../../hooks/useAuth';
import AccountNotActivePage from './AccountNotActivePage';

jest.mock('../../../hooks/useAuth', () => ({ useAuth: jest.fn() }));

const logout = jest.fn(() => new Promise<void>(() => undefined)); // stays pending, like a slow sign-out

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(useAuth).mockReturnValue({ logout } as unknown as ReturnType<typeof useAuth>);
});

test('explains a suspended account and offers sign-out and support', async () => {
  const checkAgain = jest.fn();
  render(
    <AccountNotActivePage
      block={{
        code: 'ACCOUNT_DISABLED',
        message: 'This merchant account is not active. Please contact support.',
      }}
      onCheckAgain={checkAgain}
    />,
  );

  expect(
    screen.getByRole('heading', { name: 'Your merchant account is not active' }),
  ).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /Contact support/ })).toHaveAttribute(
    'href',
    expect.stringMatching(/^mailto:/),
  );

  await userEvent.click(screen.getByRole('button', { name: 'Check Again' }));
  expect(checkAgain).toHaveBeenCalled();

  await userEvent.click(screen.getByRole('button', { name: 'Sign Out' }));
  expect(logout).toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Check Again' })).toBeDisabled();
});

test('explains an account that is waiting for approval', () => {
  render(
    <AccountNotActivePage
      block={{ code: 'ACCOUNT_PENDING_APPROVAL', message: '' }}
      onCheckAgain={jest.fn()}
    />,
  );

  expect(
    screen.getByRole('heading', { name: 'Your merchant account is awaiting approval' }),
  ).toBeInTheDocument();
  // No server message: a sensible default is shown.
  expect(screen.getByText(/We'll email you as soon as it's approved/)).toBeInTheDocument();
});
