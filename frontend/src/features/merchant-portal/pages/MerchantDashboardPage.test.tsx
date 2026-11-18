import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import type { StatusCounts } from '../../../types/parcel';
import { merchantWebhooksApi } from '../../merchant-webhooks/api';
import { merchantPortalApi } from '../api';
import type { MerchantOrderSummary, MerchantProfile } from '../types';
import MerchantDashboardPage from './MerchantDashboardPage';

jest.mock('../api', () => ({
  merchantPortalApi: {
    getProfile: jest.fn(),
    getOrderCounts: jest.fn(),
    listOrders: jest.fn(),
    listApiKeys: jest.fn(),
  },
}));
jest.mock('../../merchant-webhooks/api', () => ({
  merchantWebhooksApi: { get: jest.fn(), listDeliveries: jest.fn() },
}));

const api = jest.mocked(merchantPortalApi);
const webhooks = jest.mocked(merchantWebhooksApi);

const PROFILE: MerchantProfile = {
  merchantId: 'm1',
  businessName: 'BrightMart',
  registrationNo: 'PV00234567',
  contactEmail: 'orders@brightmart.lk',
  contactPhone: '+94771234567',
  businessAddress: '12 Galle Road, Colombo 03',
  approvalStatus: 'APPROVED',
  approvedAt: '2026-11-01T08:00:00Z',
  updatedAt: '2026-11-01T08:00:00Z',
};

const COUNTS: StatusCounts = {
  CREATED: 2,
  PICKED_UP: 1,
  IN_TRANSIT: 1,
  OUT_FOR_DELIVERY: 0,
  DELIVERED: 7,
  FAILED_ATTEMPT: 1,
  FAILED: 1,
};

const ORDER: MerchantOrderSummary = {
  parcelId: 'p1',
  trackingNumber: 'SD123456789',
  externalOrderReference: 'ORD-1001',
  recipientName: 'Nimal Perera',
  deliveryCity: 'Kandy',
  parcelType: 'PARCEL',
  status: 'IN_TRANSIT',
  quotedFee: 450,
  feeCurrency: 'LKR',
  createdAt: '2026-11-15T08:00:00Z',
};

function paged<T>(content: T[]) {
  return { content, page: { size: 10, number: 0, totalElements: content.length, totalPages: 1 } };
}

function renderPage() {
  render(<MerchantDashboardPage />, { wrapper: MemoryRouter });
}

beforeEach(() => {
  jest.clearAllMocks();
  api.getProfile.mockResolvedValue(PROFILE);
  api.getOrderCounts.mockResolvedValue(COUNTS);
  api.listOrders.mockResolvedValue(paged([ORDER]));
  api.listApiKeys.mockResolvedValue([
    {
      apiCredentialId: 'k1',
      keyId: 'aaaaaaaaaaaaaaaa',
      status: 'ACTIVE',
      createdAt: '2026-11-01T08:00:00Z',
      expiresAt: null,
      revokedAt: null,
      lastUsedAt: null,
    },
  ]);
  webhooks.get.mockResolvedValue({
    webhookId: 'w1',
    endpointUrl: 'https://brightmart.lk/hooks',
    eventTypes: [],
    active: true,
    signingSecret: null,
    createdAt: '2026-11-01T08:00:00Z',
  });
  webhooks.listDeliveries.mockResolvedValue(paged([]));
});

test('greets the merchant by business name and shows order counts', async () => {
  renderPage();

  expect(await screen.findByRole('heading', { name: 'BrightMart' })).toBeInTheDocument();
  expect(await screen.findByText('13')).toBeInTheDocument(); // total
  expect(screen.getByText('5')).toBeInTheDocument(); // active: created, picked up, in transit, failed attempt
  expect(screen.getByText('7')).toBeInTheDocument(); // delivered
});

test('lists recent orders with a link to each order', async () => {
  renderPage();

  const link = await screen.findByRole('link', { name: 'SD123456789' });
  expect(link).toHaveAttribute('href', '/merchant/orders/SD123456789');
  const row = link.closest('tr')!;
  expect(within(row).getByText('ORD-1001')).toBeInTheDocument();
  expect(within(row).getByText('Nimal Perera')).toBeInTheDocument();
  expect(api.listOrders).toHaveBeenCalledWith(0, 5);
});

test('points a merchant without orders to the API setup', async () => {
  api.listOrders.mockResolvedValue(paged([]));
  renderPage();

  expect(await screen.findByText('No orders yet')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Set up the API' })).toHaveAttribute(
    'href',
    '/merchant/integration',
  );
});

test('shows the integration status from the API keys and webhook', async () => {
  api.listApiKeys.mockResolvedValue([]);
  renderPage();

  const apiKeyRow = (await screen.findByText('API key')).parentElement!;
  const webhookRow = screen.getByText('Webhook').parentElement!;
  expect(within(apiKeyRow).getByText('Not configured')).toBeInTheDocument();
  expect(within(webhookRow).getByText('Active')).toBeInTheDocument();
});

test('lets the merchant retry when the orders fail to load', async () => {
  api.listOrders.mockRejectedValueOnce(new ApiRequestError('Server error', { status: 500 }));
  renderPage();

  await userEvent.click(await screen.findByRole('button', { name: 'Try again' }));

  expect(await screen.findByRole('link', { name: 'SD123456789' })).toBeInTheDocument();
  expect(api.listOrders).toHaveBeenCalledTimes(2);
});
