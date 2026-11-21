import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useEffect, useState } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { apiClient } from '../../../api/client';
import { getErrorMessage } from '../../../api/errors';
import { useAuth } from '../../../hooks/useAuth';
import { saveSession } from '../../../utils/sessionStorage';
import MerchantAccessGate from './MerchantAccessGate';

jest.mock('../../../hooks/useAuth', () => ({ useAuth: jest.fn() }));

const logout = jest.fn();
let suspended = true;

/** Fake backend: the profile is refused with ACCOUNT_DISABLED while the merchant is suspended. */
function backend(config: InternalAxiosRequestConfig) {
  if (!suspended) {
    return Promise.resolve({
      data: { businessName: 'BrightMart' },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    });
  }
  const data = {
    code: 'ACCOUNT_DISABLED',
    message: 'This merchant account is not active. Please contact support.',
  };
  return Promise.reject(
    new AxiosError('403', 'ERR_BAD_RESPONSE', config, null, {
      data,
      status: 403,
      statusText: '',
      headers: {},
      config,
    }),
  );
}

/** A portal screen that loads data and would show its own error card. */
function ProfileScreen() {
  const [text, setText] = useState('Loading...');
  useEffect(() => {
    apiClient.get<{ businessName: string }>('/merchant/profile').then(
      (response) => setText(response.data.businessName),
      (err: unknown) => setText(`Error card: ${getErrorMessage(err)}`),
    );
  }, []);
  return <p>{text}</p>;
}

function renderPortal() {
  render(
    <MemoryRouter initialEntries={['/merchant']}>
      <Routes>
        <Route element={<MerchantAccessGate />}>
          <Route path="/merchant" element={<ProfileScreen />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  suspended = true;
  jest.mocked(useAuth).mockReturnValue({ logout } as unknown as ReturnType<typeof useAuth>);
  saveSession({ token: 'valid', user: { id: 'u1', displayName: 'Sunil', role: 'MERCHANT' } }, true);
  apiClient.defaults.adapter = backend;
});

test('a suspended merchant sees one clear page instead of error cards', async () => {
  renderPortal();

  expect(
    await screen.findByRole('heading', { name: 'Your merchant account is not active' }),
  ).toBeInTheDocument();
  expect(
    screen.getByText('This merchant account is not active. Please contact support.'),
  ).toBeInTheDocument();
  expect(screen.queryByText(/Error card/)).not.toBeInTheDocument();
});

test('the merchant can sign out from the page', async () => {
  renderPortal();

  await userEvent.click(await screen.findByRole('button', { name: 'Sign Out' }));

  expect(logout).toHaveBeenCalled();
});

test('checking again loads the portal once the account is active again', async () => {
  renderPortal();
  await screen.findByRole('heading', { name: 'Your merchant account is not active' });
  suspended = false;

  await userEvent.click(screen.getByRole('button', { name: 'Check Again' }));

  expect(await screen.findByText('BrightMart')).toBeInTheDocument();
});

test('an active merchant sees the portal', async () => {
  suspended = false;
  renderPortal();

  expect(await screen.findByText('BrightMart')).toBeInTheDocument();
});
