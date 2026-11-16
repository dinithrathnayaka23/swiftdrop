import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { adminMerchantsApi } from '../api';
import type { Merchant } from '../types';
import MerchantDetailPage from './MerchantDetailPage';

jest.mock('../api', () => ({
  adminMerchantsApi: { get: jest.fn(), approve: jest.fn(), reject: jest.fn() },
}));

const api = jest.mocked(adminMerchantsApi);

const PENDING: Merchant = {
  merchantId: 'm1',
  businessName: 'BrightMart',
  registrationNo: 'PV00234567',
  contactEmail: 'sunil@brightmart.lk',
  contactPhone: '+94771234567',
  businessAddress: '12 Galle Road, Colombo 03',
  approvalStatus: 'PENDING',
  submittedAt: '2026-11-10T08:00:00Z',
  reviewedAt: null,
  rejectionReason: null,
};

function renderDetail(merchantId = 'm1') {
  render(
    <MemoryRouter initialEntries={[`/admin/merchants/${merchantId}`]}>
      <Routes>
        <Route path="/admin/merchants/:merchantId" element={<MerchantDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
});

test('shows the business, contact and history of the merchant in the URL', async () => {
  api.get.mockResolvedValue(PENDING);
  renderDetail();

  expect(await screen.findByRole('heading', { name: 'BrightMart' })).toBeInTheDocument();
  expect(api.get).toHaveBeenCalledWith('m1');
  expect(screen.getByText('12 Galle Road, Colombo 03')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'sunil@brightmart.lk' })).toBeInTheDocument();
  expect(screen.getByText('+94771234567')).toBeInTheDocument();
  expect(screen.getByText('Registration submitted')).toBeInTheDocument();
  expect(screen.getByText('Waiting for review')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /Back to Merchants/ })).toBeInTheDocument();
});

test('a rejected merchant shows the reason and no review buttons', async () => {
  api.get.mockResolvedValue({
    ...PENDING,
    approvalStatus: 'REJECTED',
    reviewedAt: '2026-11-11T09:00:00Z',
    rejectionReason: 'Documents missing',
  });
  renderDetail();

  expect(await screen.findByText('Registration rejected')).toBeInTheDocument();
  expect(screen.getByText('Reason: Documents missing')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
});

test('approving a pending merchant reloads it as approved', async () => {
  api.get.mockResolvedValueOnce(PENDING).mockResolvedValue({
    ...PENDING,
    approvalStatus: 'APPROVED',
    reviewedAt: '2026-11-11T09:00:00Z',
  });
  api.approve.mockResolvedValue({ ...PENDING, approvalStatus: 'APPROVED' });
  renderDetail();
  await screen.findByRole('heading', { name: 'BrightMart' });

  await userEvent.click(screen.getByRole('button', { name: 'Approve' }));
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Approve' }),
  );

  expect(api.approve).toHaveBeenCalledWith('m1');
  expect(await screen.findByText('Registration approved')).toBeInTheDocument();
  expect(screen.getByText('BrightMart was approved and emailed.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
});

test('rejecting sends the reason', async () => {
  api.get.mockResolvedValue(PENDING);
  api.reject.mockResolvedValue({ ...PENDING, approvalStatus: 'REJECTED' });
  renderDetail();
  await screen.findByRole('heading', { name: 'BrightMart' });

  await userEvent.click(screen.getByRole('button', { name: 'Reject' }));
  await userEvent.type(screen.getByLabelText('Reason for rejection'), 'Documents missing');
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Reject' }));

  expect(api.reject).toHaveBeenCalledWith('m1', 'Documents missing');
});

test('an unknown merchant shows a not-found page', async () => {
  api.get.mockResolvedValue(null);
  renderDetail('nope');

  expect(await screen.findByText('Merchant Not Found')).toBeInTheDocument();
});

test('a failed load offers to try again', async () => {
  api.get
    .mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'))
    .mockResolvedValue(PENDING);
  renderDetail();

  expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findByRole('heading', { name: 'BrightMart' })).toBeInTheDocument();
});
