import { act, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { PATHS } from '../../../routes/paths';
import type { LiveDeliveryDetail, LiveDeliveryRow, LiveStatusCounts } from '../types';
import { adminLiveApi } from '../api';
import { useLiveUpdates, type LiveUpdatesOptions } from '../../shared-live/useLiveUpdates';
import { DELIVERY_DETAIL_PATH } from '../paths';
import DeliveryDetailPage from './DeliveryDetailPage';
import LiveTrackingPage from './LiveTrackingPage';

jest.mock('../api', () => ({
  ADMIN_LIVE_STREAM_PATH: '/admin/live/stream',
  adminLiveApi: { listDeliveries: jest.fn(), getCounts: jest.fn(), getDelivery: jest.fn() },
}));
jest.mock('../../shared-live/useLiveUpdates', () => ({ useLiveUpdates: jest.fn() }));
jest.mock('../../../hooks/useAuth', () => ({ useAuth: () => ({ token: 'admin-token' }) }));

const api = jest.mocked(adminLiveApi);
const live = jest.mocked(useLiveUpdates);

const ROW: LiveDeliveryRow = {
  parcelId: 'p1',
  trackingNumber: 'SD123456789',
  status: 'IN_TRANSIT',
  statusVersion: 3,
  recipientName: 'Kasun Perera',
  recipientPhone: '+94 77 123 4567',
  merchantName: 'BrightMart',
  courierName: null,
  destinationCity: 'Colombo 07',
  bookedAt: '2026-11-07T04:00:00Z',
  lastUpdatedAt: '2026-11-07T06:00:00Z',
};

const COUNTS: LiveStatusCounts = {
  CREATED: 1,
  PICKED_UP: 0,
  IN_TRANSIT: 2,
  OUT_FOR_DELIVERY: 1,
  DELIVERED: 6,
  FAILED_ATTEMPT: 0,
  FAILED: 0,
};

/** The options of the latest useLiveUpdates call, to push an update like the stream would. */
function latestLiveOptions(): LiveUpdatesOptions {
  return live.mock.calls[live.mock.calls.length - 1][0];
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={PATHS.adminDeliveries} element={<LiveTrackingPage />} />
        <Route path={DELIVERY_DETAIL_PATH} element={<DeliveryDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  live.mockReturnValue('live');
  api.getCounts.mockResolvedValue(COUNTS);
  api.listDeliveries.mockResolvedValue({
    content: [ROW],
    page: { size: 10, number: 0, totalElements: 1, totalPages: 1 },
  });
});

describe('LiveTrackingPage', () => {
  it('shows the counts and deliveries, linked to their detail page', async () => {
    renderAt(PATHS.adminDeliveries);

    const row = (await screen.findByText('#SD123456789')).closest('tr')!;
    expect(within(row).getByText('Kasun Perera')).toBeInTheDocument();
    expect(within(row).getByText('In Transit')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'View delivery SD123456789' })).toHaveAttribute(
      'href',
      '/admin/deliveries/SD123456789',
    );
    // Total of every status, and the delivered share of it.
    expect(screen.getByText('Total Deliveries').nextSibling).toHaveTextContent('10');
    expect(screen.getByText('Delivered', { selector: 'p' }).nextSibling).toHaveTextContent('660%');
    // The admin stream is opened with the admin's token.
    expect(latestLiveOptions()).toMatchObject({ path: '/admin/live/stream', token: 'admin-token' });
  });

  it('applies a live status change to its row and the counts', async () => {
    renderAt(PATHS.adminDeliveries);
    await screen.findByText('#SD123456789');

    act(() =>
      latestLiveOptions().onUpdate({
        trackingNumber: 'SD123456789',
        previousStatus: 'IN_TRANSIT',
        status: 'OUT_FOR_DELIVERY',
        statusVersion: 4,
        changedAt: '2026-11-07T07:00:00Z',
      }),
    );

    const row = screen.getByText('#SD123456789').closest('tr')!;
    expect(within(row).getByText('Out for Delivery')).toBeInTheDocument();
    expect(screen.getByText('Out for Delivery', { selector: 'p' }).nextSibling).toHaveTextContent(
      '2',
    );
  });
});

describe('DeliveryDetailPage', () => {
  const DETAIL: LiveDeliveryDetail = {
    ...ROW,
    dropoffAddress: {
      addressLine1: 'No. 15, Park Road',
      addressLine2: null,
      city: 'Colombo 07',
      district: 'Western',
      postalCode: null,
    },
    fromCity: 'Kandy',
    parcelType: 'PARCEL',
    weightKg: 2.5,
    lengthCm: 30,
    widthCm: 20,
    heightCm: 15,
    contentDescription: 'Clothing',
    deliveryInstructions: 'Handle with care',
    history: [
      { status: 'CREATED', changedAt: '2026-11-07T04:00:00Z' },
      { status: 'PICKED_UP', changedAt: '2026-11-07T05:00:00Z' },
      { status: 'IN_TRANSIT', changedAt: '2026-11-07T06:00:00Z' },
    ],
  };

  it('shows one delivery on its own page', async () => {
    api.getDelivery.mockResolvedValue(DETAIL);
    renderAt('/admin/deliveries/SD123456789');

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '+94 77 123 4567' })).toHaveAttribute(
      'href',
      'tel:+94771234567',
    );
    expect(screen.getByText('No. 15, Park Road', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('Handle with care')).toBeInTheDocument();
    expect(screen.getByText('30 × 20 × 15 cm')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open in Maps/ })).toHaveAttribute(
      'href',
      expect.stringContaining('google.com/maps'),
    );
    // Follows this parcel's public stream.
    expect(latestLiveOptions().path).toBe('/public/tracking/SD123456789/stream');
  });

  it('says when no parcel has the tracking number', async () => {
    api.getDelivery.mockResolvedValue(null);
    renderAt('/admin/deliveries/SD000000000');

    expect(await screen.findByText('Delivery Not Found')).toBeInTheDocument();
  });
});
