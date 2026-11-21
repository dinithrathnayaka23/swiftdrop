import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { saveBlob } from '../../shared-parcels/download';
import { useLiveUpdates, type LiveUpdatesOptions } from '../../shared-live/useLiveUpdates';
import { merchantPortalApi } from '../api';
import type { MerchantOrderDetail } from '../types';
import OrderDetailsPage from './OrderDetailsPage';

jest.mock('../api', () => ({
  merchantPortalApi: { getOrder: jest.fn(), downloadReceipt: jest.fn() },
}));
jest.mock('../../shared-live/useLiveUpdates', () => ({ useLiveUpdates: jest.fn() }));
jest.mock('../../shared-parcels/download', () => ({ saveBlob: jest.fn() }));

const live = jest.mocked(useLiveUpdates);
const save = jest.mocked(saveBlob);

const api = jest.mocked(merchantPortalApi);

const ORDER: MerchantOrderDetail = {
  parcelId: 'p1',
  trackingNumber: 'SD123456789',
  externalOrderReference: 'ORD-1001',
  pickupAddress: {
    contactName: 'BrightMart Warehouse',
    contactPhone: '+94711111111',
    addressLine1: '1 Depot Road',
    addressLine2: null,
    city: 'Colombo',
    district: 'Colombo',
    postalCode: '00300',
  },
  dropoffAddress: {
    contactName: 'Nimal Perera',
    contactPhone: '+94772222222',
    addressLine1: '25 Temple Street',
    addressLine2: 'Apt 4',
    city: 'Kandy',
    district: 'Kandy',
    postalCode: '20000',
  },
  parcelType: 'PARCEL',
  weightKg: 1.5,
  lengthCm: null,
  widthCm: null,
  heightCm: null,
  contentDescription: 'Running shoes',
  deliveryInstructions: 'Call first',
  status: 'DELIVERED',
  quotedFee: 450,
  feeCurrency: 'LKR',
  createdAt: '2026-11-10T08:00:00Z',
  updatedAt: '2026-11-11T15:00:00Z',
  timeline: [
    {
      status: 'CREATED',
      changedAt: '2026-11-10T08:00:00Z',
      note: null,
      failureReason: null,
      recipientName: null,
    },
    {
      status: 'DELIVERED',
      changedAt: '2026-11-11T15:00:00Z',
      note: null,
      failureReason: null,
      recipientName: 'Kamala Perera',
    },
  ],
};

function renderAt(trackingNumber: string) {
  render(
    <MemoryRouter initialEntries={[`/merchant/orders/${trackingNumber}`]}>
      <Routes>
        <Route path="/merchant/orders/:trackingNumber" element={<OrderDetailsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  live.mockReturnValue('live');
});

/** The options the page last passed to useLiveUpdates. */
function liveOptions(): LiveUpdatesOptions {
  return live.mock.calls[live.mock.calls.length - 1][0];
}

test('shows the order, its addresses and who received it', async () => {
  api.getOrder.mockResolvedValue(ORDER);
  renderAt('SD123456789');

  expect(await screen.findByRole('heading', { name: /SD123456789/ })).toBeInTheDocument();
  expect(api.getOrder).toHaveBeenCalledWith('SD123456789');
  expect(screen.getByText(/Your order ORD-1001/)).toBeInTheDocument();
  expect(screen.getByText('Apt 4', { exact: false })).toBeInTheDocument();
  expect(screen.getByText('Running shoes')).toBeInTheDocument();
  expect(screen.getByText('Kamala Perera')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Back to Orders' })).toHaveAttribute(
    'href',
    '/merchant/orders',
  );
});

test('shows why a failed order could not be delivered', async () => {
  api.getOrder.mockResolvedValue({
    ...ORDER,
    status: 'FAILED',
    timeline: [
      ORDER.timeline[0],
      {
        status: 'FAILED',
        changedAt: '2026-11-11T15:00:00Z',
        note: null,
        failureReason: 'Recipient not available',
        recipientName: null,
      },
    ],
  });
  renderAt('SD123456789');

  const reasons = await screen.findAllByText('Reason: Recipient not available');
  expect(reasons[0]).toHaveClass('text-red-700'); // the banner above the details
});

test("says when the order isn't the merchant's", async () => {
  api.getOrder.mockResolvedValue(null);
  renderAt('SD000000000');

  expect(await screen.findByText('Order Not Found')).toBeInTheDocument();
});

describe('live status', () => {
  const IN_TRANSIT: MerchantOrderDetail = {
    ...ORDER,
    status: 'IN_TRANSIT',
    timeline: [
      ORDER.timeline[0],
      { ...ORDER.timeline[0], status: 'PICKED_UP', changedAt: '2026-11-10T09:00:00Z' },
      { ...ORDER.timeline[0], status: 'IN_TRANSIT', changedAt: '2026-11-10T10:00:00Z' },
    ],
  };
  const statusPill = () => screen.getByRole('heading', { name: /SD123456789/ }).closest('header')!;

  test("follows the order's public stream and shows the connection", async () => {
    api.getOrder.mockResolvedValue(IN_TRANSIT);
    renderAt('SD123456789');

    await screen.findByRole('heading', { name: /SD123456789/ });
    expect(liveOptions().path).toBe('/public/tracking/SD123456789/stream');
    expect(liveOptions().knownVersions).toEqual({ SD123456789: 3 });
    expect(screen.getByText('Live')).toBeInTheDocument();
  });

  test('shows a newer status at once, then reloads the order quietly', async () => {
    api.getOrder.mockResolvedValueOnce(IN_TRANSIT);
    renderAt('SD123456789');
    await screen.findByRole('heading', { name: /SD123456789/ });
    const failed = {
      ...IN_TRANSIT,
      status: 'FAILED_ATTEMPT' as const,
      timeline: [
        ...IN_TRANSIT.timeline,
        {
          ...ORDER.timeline[0],
          status: 'FAILED_ATTEMPT' as const,
          changedAt: '2026-11-10T11:00:00Z',
          failureReason: 'Recipient not available',
        },
      ],
    };
    api.getOrder.mockResolvedValueOnce(failed);

    act(() =>
      liveOptions().onUpdate({
        trackingNumber: 'SD123456789',
        previousStatus: 'IN_TRANSIT',
        status: 'FAILED_ATTEMPT',
        statusVersion: 4,
        changedAt: '2026-11-10T11:00:00Z',
      }),
    );

    expect(statusPill()).toHaveTextContent('Delivery attempt failed');
    expect(screen.queryByText('Loading order...')).not.toBeInTheDocument();
    expect((await screen.findAllByText('Reason: Recipient not available')).length).toBeGreaterThan(
      0,
    );
    expect(api.getOrder).toHaveBeenCalledTimes(2);
    expect(liveOptions().knownVersions).toEqual({ SD123456789: 4 });
  });

  test('ignores an update that is not newer than what is shown', async () => {
    api.getOrder.mockResolvedValue(IN_TRANSIT);
    renderAt('SD123456789');
    await screen.findByRole('heading', { name: /SD123456789/ });

    act(() =>
      liveOptions().onUpdate({
        trackingNumber: 'SD123456789',
        previousStatus: 'CREATED',
        status: 'PICKED_UP',
        statusVersion: 2,
        changedAt: '2026-11-10T09:00:00Z',
      }),
    );

    expect(statusPill()).toHaveTextContent('In Transit');
  });

  test('keeps the pushed status when the quiet reload fails', async () => {
    api.getOrder.mockResolvedValueOnce(IN_TRANSIT);
    renderAt('SD123456789');
    await screen.findByRole('heading', { name: /SD123456789/ });
    api.getOrder.mockRejectedValueOnce(new ApiRequestError('Server error', { status: 500 }));

    act(() =>
      liveOptions().onUpdate({
        trackingNumber: 'SD123456789',
        previousStatus: 'IN_TRANSIT',
        status: 'OUT_FOR_DELIVERY',
        statusVersion: 4,
        changedAt: '2026-11-10T11:00:00Z',
      }),
    );

    await waitFor(() => expect(api.getOrder).toHaveBeenCalledTimes(2));
    expect(statusPill()).toHaveTextContent('Out for Delivery');
    expect(screen.queryByText('Something Went Wrong')).not.toBeInTheDocument();
  });

  test('reloads after the stream reconnects, in case changes were missed', async () => {
    const delivered: MerchantOrderDetail = {
      ...IN_TRANSIT,
      status: 'DELIVERED',
      timeline: [...IN_TRANSIT.timeline, ORDER.timeline[1]],
    };
    api.getOrder.mockResolvedValueOnce(IN_TRANSIT).mockResolvedValueOnce(delivered);
    renderAt('SD123456789');
    await screen.findByRole('heading', { name: /SD123456789/ });

    act(() => liveOptions().onReconnect?.());

    await waitFor(() => expect(statusPill()).toHaveTextContent('Delivered'));
    expect(screen.getByText('Kamala Perera')).toBeInTheDocument();
  });

  test('never goes back to an older state when a reload is behind the stream', async () => {
    api.getOrder.mockResolvedValueOnce(IN_TRANSIT).mockResolvedValueOnce(IN_TRANSIT);
    renderAt('SD123456789');
    await screen.findByRole('heading', { name: /SD123456789/ });

    act(() =>
      liveOptions().onUpdate({
        trackingNumber: 'SD123456789',
        previousStatus: 'IN_TRANSIT',
        status: 'OUT_FOR_DELIVERY',
        statusVersion: 4,
        changedAt: '2026-11-10T11:00:00Z',
      }),
    );

    await waitFor(() => expect(api.getOrder).toHaveBeenCalledTimes(2));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(statusPill()).toHaveTextContent('Out for Delivery');
  });

  test('says when live updates are paused', async () => {
    live.mockReturnValue('offline');
    api.getOrder.mockResolvedValue(IN_TRANSIT);
    renderAt('SD123456789');

    expect(await screen.findByText('Live updates paused')).toBeInTheDocument();
  });
});

describe('receipt', () => {
  test('downloads the receipt PDF', async () => {
    api.getOrder.mockResolvedValue(ORDER);
    const pdf = new Blob(['%PDF-'], { type: 'application/pdf' });
    api.downloadReceipt.mockResolvedValue(pdf);
    renderAt('SD123456789');

    await userEvent.click(await screen.findByRole('button', { name: 'Download Receipt' }));

    expect(api.downloadReceipt).toHaveBeenCalledWith('SD123456789');
    expect(save).toHaveBeenCalledWith(pdf, 'receipt-SD123456789.pdf');
  });

  test('says when the receipt could not be downloaded, and clears it on retry', async () => {
    api.getOrder.mockResolvedValue(ORDER);
    api.downloadReceipt.mockRejectedValueOnce(
      new ApiRequestError('Order not found', { status: 404 }),
    );
    renderAt('SD123456789');

    await userEvent.click(await screen.findByRole('button', { name: 'Download Receipt' }));

    expect(
      await screen.findByText('Could not download the receipt: Order not found'),
    ).toBeInTheDocument();
    expect(save).not.toHaveBeenCalled();

    api.downloadReceipt.mockResolvedValueOnce(new Blob(['%PDF-']));
    await userEvent.click(screen.getByRole('button', { name: 'Download Receipt' }));

    await waitFor(() => expect(save).toHaveBeenCalled());
    expect(screen.queryByText(/Could not download the receipt/)).not.toBeInTheDocument();
  });
});
