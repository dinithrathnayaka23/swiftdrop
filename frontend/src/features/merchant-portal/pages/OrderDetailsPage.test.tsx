import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { merchantPortalApi } from '../api';
import type { MerchantOrderDetail } from '../types';
import OrderDetailsPage from './OrderDetailsPage';

jest.mock('../api', () => ({ merchantPortalApi: { getOrder: jest.fn() } }));

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

beforeEach(() => jest.clearAllMocks());

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
