import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { adminMerchantsApi } from '../api';
import type { Merchant, MerchantStatus } from '../types';
import MerchantManagementPage from './MerchantManagementPage';

jest.mock('../api', () => ({
  adminMerchantsApi: { list: jest.fn(), approve: jest.fn(), reject: jest.fn() },
}));

const api = jest.mocked(adminMerchantsApi);

function merchant(overrides: Partial<Merchant>): Merchant {
  return {
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
    ...overrides,
  };
}

const BRIGHTMART = merchant({});
const GREEN_BASKET = merchant({
  merchantId: 'm2',
  businessName: 'Green Basket',
  approvalStatus: 'APPROVED',
  reviewedAt: '2026-11-11T09:00:00Z',
});
const QUICKCART = merchant({
  merchantId: 'm3',
  businessName: 'QuickCart',
  approvalStatus: 'REJECTED',
  reviewedAt: '2026-11-12T09:00:00Z',
  rejectionReason: 'Registration number could not be verified',
});

function givenMerchants(byStatus: Partial<Record<MerchantStatus, Merchant[]>>) {
  api.list.mockImplementation((status) => Promise.resolve(byStatus[status] ?? []));
}

beforeEach(() => {
  jest.clearAllMocks();
  givenMerchants({ PENDING: [BRIGHTMART], APPROVED: [GREEN_BASKET], REJECTED: [QUICKCART] });
});

const dialog = () => screen.getByRole('dialog');

test('shows the pending queue first, with counts for every status', async () => {
  render(<MerchantManagementPage />, { wrapper: MemoryRouter });

  expect(await screen.findByText('BrightMart')).toBeInTheDocument();
  expect(screen.queryByText('Green Basket')).not.toBeInTheDocument();
  expect(screen.getByRole('tab', { name: /Pending/ })).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByRole('tab', { name: 'Approved 1' })).toBeInTheDocument();
  expect(api.list).toHaveBeenCalledTimes(4);
});

test('the other tabs list reviewed merchants without review buttons', async () => {
  render(<MerchantManagementPage />, { wrapper: MemoryRouter });
  await screen.findByText('BrightMart');

  await userEvent.click(screen.getByRole('tab', { name: /Rejected/ }));

  expect(screen.getByText('QuickCart')).toBeInTheDocument();
  expect(screen.getByText('Reason: Registration number could not be verified')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
});

test('approving asks first, then saves and reloads', async () => {
  api.approve.mockResolvedValue({ ...BRIGHTMART, approvalStatus: 'APPROVED' });
  render(<MerchantManagementPage />, { wrapper: MemoryRouter });
  await screen.findByText('BrightMart');

  await userEvent.click(screen.getByRole('button', { name: 'Approve' }));
  expect(within(dialog()).getByText('Approve BrightMart?')).toBeInTheDocument();
  expect(api.approve).not.toHaveBeenCalled();

  givenMerchants({ APPROVED: [GREEN_BASKET, { ...BRIGHTMART, approvalStatus: 'APPROVED' }] });
  await userEvent.click(within(dialog()).getByRole('button', { name: 'Approve' }));

  expect(api.approve).toHaveBeenCalledWith('m1');
  expect(await screen.findByText('BrightMart was approved and emailed.')).toBeInTheDocument();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(await screen.findByText('No pending merchants')).toBeInTheDocument();
});

test('rejecting needs a reason, which is sent trimmed', async () => {
  api.reject.mockResolvedValue({ ...BRIGHTMART, approvalStatus: 'REJECTED' });
  render(<MerchantManagementPage />, { wrapper: MemoryRouter });
  await screen.findByText('BrightMart');

  await userEvent.click(screen.getByRole('button', { name: 'Reject' }));
  await userEvent.click(within(dialog()).getByRole('button', { name: 'Reject' }));
  expect(within(dialog()).getByText('Please give a reason for the rejection')).toBeInTheDocument();
  expect(api.reject).not.toHaveBeenCalled();

  await userEvent.type(
    within(dialog()).getByLabelText('Reason for rejection'),
    '  Documents missing  ',
  );
  await userEvent.click(within(dialog()).getByRole('button', { name: 'Reject' }));

  expect(api.reject).toHaveBeenCalledWith('m1', 'Documents missing');
  expect(
    await screen.findByText('BrightMart was rejected and emailed the reason.'),
  ).toBeInTheDocument();
});

test('cancelling closes the dialog without saving', async () => {
  render(<MerchantManagementPage />, { wrapper: MemoryRouter });
  await screen.findByText('BrightMart');

  await userEvent.click(screen.getByRole('button', { name: 'Reject' }));
  await userEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));

  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(api.reject).not.toHaveBeenCalled();
});

test('a refused review keeps the dialog open with the server message', async () => {
  api.approve.mockRejectedValue(
    new ApiRequestError('Only pending merchants can be reviewed', { status: 409 }),
  );
  render(<MerchantManagementPage />, { wrapper: MemoryRouter });
  await screen.findByText('BrightMart');

  await userEvent.click(screen.getByRole('button', { name: 'Approve' }));
  await userEvent.click(within(dialog()).getByRole('button', { name: 'Approve' }));

  expect(
    await within(dialog()).findByText('Only pending merchants can be reviewed'),
  ).toBeInTheDocument();
});

test('a failed load offers to try again', async () => {
  api.list.mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
  render(<MerchantManagementPage />, { wrapper: MemoryRouter });

  expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  await waitFor(() => expect(screen.getByText('BrightMart')).toBeInTheDocument());
});
