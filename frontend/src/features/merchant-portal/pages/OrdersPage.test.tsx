import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { merchantPortalApi } from '../api';
import type { MerchantOrderSummary } from '../types';
import OrdersPage from './OrdersPage';

jest.mock('../api', () => ({ merchantPortalApi: { listOrders: jest.fn() } }));

const api = jest.mocked(merchantPortalApi);

function order(overrides: Partial<MerchantOrderSummary> = {}): MerchantOrderSummary {
  return {
    parcelId: 'p1',
    trackingNumber: 'SD123456789',
    externalOrderReference: 'ORD-1001',
    recipientName: 'Nimal Perera',
    deliveryCity: 'Kandy',
    parcelType: 'FRAGILE',
    status: 'DELIVERED',
    quotedFee: 450,
    feeCurrency: 'LKR',
    createdAt: '2026-11-15T08:00:00Z',
    ...overrides,
  };
}

function paged(content: MerchantOrderSummary[], totalElements: number, totalPages: number) {
  return { content, page: { size: 10, number: 0, totalElements, totalPages } };
}

function renderAt(url = '/merchant/orders') {
  render(
    <MemoryRouter initialEntries={[url]}>
      <OrdersPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  api.listOrders.mockResolvedValue(paged([order()], 1, 1));
});

test('lists each order with a link to its details', async () => {
  renderAt();

  const link = await screen.findByRole('link', { name: 'SD123456789' });
  expect(link).toHaveAttribute('href', '/merchant/orders/SD123456789');
  const row = link.closest('tr')!;
  expect(within(row).getByText('ORD-1001')).toBeInTheDocument();
  expect(within(row).getByText('Nimal Perera')).toBeInTheDocument();
  expect(within(row).getByText('Fragile')).toBeInTheDocument();
  expect(within(row).getByText('Rs. 450.00')).toBeInTheDocument();
  expect(screen.getByText('1 order')).toBeInTheDocument();
  expect(api.listOrders).toHaveBeenCalledWith(0, 10);
});

test('pages through orders', async () => {
  api.listOrders.mockResolvedValue(paged([order()], 25, 3));
  renderAt();

  await userEvent.click(await screen.findByRole('button', { name: '2' }));

  expect(api.listOrders).toHaveBeenLastCalledWith(1, 10);
});

test('opens the page given in the address', async () => {
  renderAt('/merchant/orders?page=3');

  await screen.findByRole('link', { name: 'SD123456789' });
  expect(api.listOrders).toHaveBeenCalledWith(2, 10);
});

test('points a merchant without orders to the API setup', async () => {
  api.listOrders.mockResolvedValue(paged([], 0, 0));
  renderAt();

  expect(await screen.findByText('No orders yet')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Set up the API' })).toHaveAttribute(
    'href',
    '/merchant/integration',
  );
});

test('lets the merchant retry when the orders fail to load', async () => {
  api.listOrders.mockRejectedValueOnce(new ApiRequestError('Server error', { status: 500 }));
  renderAt();

  await userEvent.click(await screen.findByRole('button', { name: 'Try again' }));

  expect(await screen.findByRole('link', { name: 'SD123456789' })).toBeInTheDocument();
});
