import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router';
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

/** Shows the address's query string, so tests can check what the filters put there. */
function Address() {
  return <output data-testid="address">{useLocation().search}</output>;
}

function renderAt(url = '/merchant/orders') {
  render(
    <MemoryRouter initialEntries={[url]}>
      <OrdersPage />
      <Address />
    </MemoryRouter>,
  );
}

function address() {
  return new URLSearchParams(screen.getByTestId('address').textContent ?? '');
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
  expect(api.listOrders).toHaveBeenCalledWith(0, 10, {});
});

test('pages through orders', async () => {
  api.listOrders.mockResolvedValue(paged([order()], 25, 3));
  renderAt();

  await userEvent.click(await screen.findByRole('button', { name: '2' }));

  expect(api.listOrders).toHaveBeenLastCalledWith(1, 10, {});
});

test('opens the page given in the address', async () => {
  renderAt('/merchant/orders?page=3');

  await screen.findByRole('link', { name: 'SD123456789' });
  expect(api.listOrders).toHaveBeenCalledWith(2, 10, {});
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

describe('filters', () => {
  test('searches after typing stops, and keeps the search in the address', async () => {
    renderAt();
    await screen.findByRole('link', { name: 'SD123456789' });

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search orders' }), '  kandy ');

    await waitFor(() =>
      expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, { search: 'kandy' }),
    );
    expect(address().get('search')).toBe('kandy');
    // One request for the page, one for the finished search: none per key press.
    expect(api.listOrders).toHaveBeenCalledTimes(2);
  });

  test('filters by one status at a time and can show every status again', async () => {
    renderAt();
    const inTransit = await screen.findByRole('button', { name: 'In Transit' });
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(inTransit);

    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, { status: 'IN_TRANSIT' });
    expect(address().get('status')).toBe('IN_TRANSIT');
    expect(screen.getByRole('button', { name: 'In Transit' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'false');

    await userEvent.click(screen.getByRole('button', { name: 'Delivered' }));
    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, { status: 'DELIVERED' });

    await userEvent.click(screen.getByRole('button', { name: 'All' }));
    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, {});
    expect(address().has('status')).toBe(false);
  });

  test('clicking the selected status again clears it', async () => {
    renderAt('/merchant/orders?status=FAILED');

    await userEvent.click(await screen.findByRole('button', { name: 'Failed' }));

    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, {});
  });

  test('filters by creation dates', async () => {
    renderAt();
    await screen.findByRole('link', { name: 'SD123456789' });

    await userEvent.type(screen.getByLabelText('Created from'), '2026-11-01');
    await userEvent.type(screen.getByLabelText('Created to'), '2026-11-30');

    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, {
      from: '2026-11-01',
      to: '2026-11-30',
    });
    expect(address().get('from')).toBe('2026-11-01');
    expect(address().get('to')).toBe('2026-11-30');
    expect(screen.getByLabelText('Created from')).toHaveAttribute('max', '2026-11-30');
    expect(screen.getByLabelText('Created to')).toHaveAttribute('min', '2026-11-01');
  });

  test('opens with every filter and the page from the address, combined', async () => {
    renderAt(
      '/merchant/orders?search=kandy&status=IN_TRANSIT&from=2026-11-01&to=2026-11-30&page=2',
    );

    await screen.findByRole('link', { name: 'SD123456789' });
    expect(api.listOrders).toHaveBeenCalledWith(1, 10, {
      search: 'kandy',
      status: 'IN_TRANSIT',
      from: '2026-11-01',
      to: '2026-11-30',
    });
    expect(screen.getByRole('searchbox', { name: 'Search orders' })).toHaveValue('kandy');
    expect(screen.getByRole('button', { name: 'In Transit' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  test('a changed filter goes back to the first page and keeps the other filters', async () => {
    api.listOrders.mockResolvedValue(paged([order()], 25, 3));
    renderAt('/merchant/orders?search=kandy&page=3');
    await screen.findByRole('link', { name: 'SD123456789' });

    await userEvent.click(screen.getByRole('button', { name: 'Delivered' }));

    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, {
      search: 'kandy',
      status: 'DELIVERED',
    });
    expect(address().has('page')).toBe(false);
    expect(address().get('search')).toBe('kandy');
  });

  test('paging keeps the filters', async () => {
    api.listOrders.mockResolvedValue(paged([order()], 25, 3));
    renderAt('/merchant/orders?status=DELIVERED');

    await userEvent.click(await screen.findByRole('button', { name: '2' }));

    expect(api.listOrders).toHaveBeenLastCalledWith(1, 10, { status: 'DELIVERED' });
    expect(address().get('status')).toBe('DELIVERED');
  });

  test('ignores filter values in the address that cannot be valid', async () => {
    renderAt('/merchant/orders?status=LOST&from=yesterday&to=2026-11-3&search=%20%20');

    await screen.findByRole('link', { name: 'SD123456789' });
    expect(api.listOrders).toHaveBeenCalledWith(0, 10, {});
    expect(screen.queryByRole('button', { name: /Clear filters/ })).not.toBeInTheDocument();
  });

  test('says when no order matches, and clears every filter', async () => {
    api.listOrders.mockResolvedValue(paged([], 0, 0));
    renderAt('/merchant/orders?search=nothing&status=FAILED&from=2026-11-01');

    expect(await screen.findByText('No orders match your filters')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Set up the API' })).not.toBeInTheDocument();

    api.listOrders.mockResolvedValue(paged([order()], 1, 1));
    const clearButtons = screen.getAllByRole('button', { name: /Clear filters/ });
    await userEvent.click(clearButtons[clearButtons.length - 1]);

    expect(await screen.findByRole('link', { name: 'SD123456789' })).toBeInTheDocument();
    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, {});
    expect(screen.getByTestId('address')).toBeEmptyDOMElement();
    expect(screen.getByRole('searchbox', { name: 'Search orders' })).toHaveValue('');
  });

  test('clear filters also drops text that has not been searched yet', async () => {
    renderAt('/merchant/orders?status=FAILED');
    await screen.findByRole('link', { name: 'SD123456789' });

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search orders' }), 'kan');
    await userEvent.click(screen.getByRole('button', { name: /Clear filters/ }));

    expect(screen.getByRole('searchbox', { name: 'Search orders' })).toHaveValue('');
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(api.listOrders).toHaveBeenLastCalledWith(0, 10, {});
  });

  test('counts only the matching orders', async () => {
    api.listOrders.mockResolvedValue(paged([order()], 3, 1));
    renderAt('/merchant/orders?status=DELIVERED');

    expect(await screen.findByText('3 matching orders')).toBeInTheDocument();
  });

  test('shows the server message for a filter it rejects', async () => {
    api.listOrders.mockRejectedValue(
      new ApiRequestError('from must be on or before to', { status: 400 }),
    );
    renderAt('/merchant/orders?from=2026-11-30&to=2026-11-01');

    expect(await screen.findByText('Could not load your orders')).toBeInTheDocument();
    expect(screen.getByText('from must be on or before to')).toBeInTheDocument();
  });
});
