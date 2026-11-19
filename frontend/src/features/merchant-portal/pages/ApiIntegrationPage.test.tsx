import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiRequestError } from '../../../api/errors';
import { merchantPortalApi } from '../api';
import type { ApiKey, GeneratedApiKey } from '../types';
import ApiIntegrationPage from './ApiIntegrationPage';

jest.mock('../api', () => ({
  merchantPortalApi: {
    listApiKeys: jest.fn(),
    createApiKey: jest.fn(),
    revokeApiKey: jest.fn(),
    rotateApiKey: jest.fn(),
  },
}));

const api = jest.mocked(merchantPortalApi);

function key(overrides: Partial<ApiKey> = {}): ApiKey {
  return {
    apiCredentialId: 'k1',
    keyId: '3f9a1c0b7d2e4f68',
    status: 'ACTIVE',
    createdAt: '2026-11-01T08:00:00Z',
    expiresAt: null,
    revokedAt: null,
    lastUsedAt: null,
    ...overrides,
  };
}

const GENERATED: GeneratedApiKey = {
  apiCredentialId: 'k2',
  keyId: 'aaaaaaaaaaaaaaaa',
  apiKey: 'sd_aaaaaaaaaaaaaaaa_secretsecretsecretsecretsecretsecretsecr',
  createdAt: '2026-11-19T08:00:00Z',
};

beforeEach(() => {
  jest.clearAllMocks();
  api.listApiKeys.mockResolvedValue([key()]);
});

test('lists keys by their public ID only, with actions for active keys', async () => {
  api.listApiKeys.mockResolvedValue([
    key(),
    key({ apiCredentialId: 'k0', keyId: '0000000000000000', status: 'REVOKED' }),
  ]);
  render(<ApiIntegrationPage />);

  const active = (await screen.findByText('sd_3f9a1c0b7d2e4f68_••••')).closest('tr')!;
  expect(within(active).getByText('Active')).toBeInTheDocument();
  expect(within(active).getByText('Never')).toBeInTheDocument();
  expect(within(active).getByRole('button', { name: /Revoke/ })).toBeInTheDocument();

  const revoked = screen.getByText('sd_0000000000000000_••••').closest('tr')!;
  expect(within(revoked).getByText('Revoked')).toBeInTheDocument();
  expect(within(revoked).queryByRole('button')).not.toBeInTheDocument();
});

test('shows a new key once, until the merchant confirms they saved it', async () => {
  api.createApiKey.mockResolvedValue(GENERATED);
  render(<ApiIntegrationPage />);

  await userEvent.click(await screen.findByRole('button', { name: 'Create API key' }));

  expect(await screen.findByText(GENERATED.apiKey)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Copy API key' })).toBeInTheDocument();
  expect(api.listApiKeys).toHaveBeenCalledTimes(2); // reloaded after creating

  await userEvent.click(screen.getByRole('button', { name: "I've saved it" }));
  expect(screen.queryByText(GENERATED.apiKey)).not.toBeInTheDocument();
});

test('explains why a key could not be created', async () => {
  api.createApiKey.mockRejectedValue(
    new ApiRequestError('You already have 2 active API keys. Revoke one before creating another.', {
      status: 409,
    }),
  );
  render(<ApiIntegrationPage />);

  await userEvent.click(await screen.findByRole('button', { name: 'Create API key' }));

  expect(await screen.findByText(/You already have 2 active API keys/)).toBeInTheDocument();
});

test('revokes a key only after confirmation', async () => {
  api.revokeApiKey.mockResolvedValue(key({ status: 'REVOKED' }));
  render(<ApiIntegrationPage />);

  await userEvent.click(await screen.findByRole('button', { name: /Revoke key 3f9a1c0b7d2e4f68/ }));
  const dialog = screen.getByRole('alertdialog');
  expect(dialog).toHaveTextContent('stops working immediately');
  expect(api.revokeApiKey).not.toHaveBeenCalled();

  await userEvent.click(within(dialog).getByRole('button', { name: 'Revoke key' }));

  expect(api.revokeApiKey).toHaveBeenCalledWith('k1');
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
});

test('rotating a key shows its replacement once', async () => {
  api.rotateApiKey.mockResolvedValue(GENERATED);
  render(<ApiIntegrationPage />);

  await userEvent.click(await screen.findByRole('button', { name: /Rotate key 3f9a1c0b7d2e4f68/ }));
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Rotate key' }),
  );

  expect(api.rotateApiKey).toHaveBeenCalledWith('k1');
  expect(await screen.findByText(GENERATED.apiKey)).toBeInTheDocument();
});

test('cancelling the confirmation changes nothing', async () => {
  render(<ApiIntegrationPage />);

  await userEvent.click(await screen.findByRole('button', { name: /Revoke key/ }));
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Cancel' }),
  );

  expect(api.revokeApiKey).not.toHaveBeenCalled();
});

test('invites a merchant without keys to create one', async () => {
  api.listApiKeys.mockResolvedValue([]);
  render(<ApiIntegrationPage />);

  expect(await screen.findByText('No API keys yet')).toBeInTheDocument();
  expect(screen.getByText('Set up your integration')).toBeInTheDocument();
});
