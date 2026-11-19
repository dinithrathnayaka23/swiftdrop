import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiRequestError } from '../../../api/errors';
import { merchantPortalApi } from '../api';
import type { MerchantProfile } from '../types';
import BusinessProfilePage from './BusinessProfilePage';

jest.mock('../api', () => ({
  merchantPortalApi: { getProfile: jest.fn(), updateProfile: jest.fn() },
}));

const api = jest.mocked(merchantPortalApi);

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

beforeEach(() => {
  jest.clearAllMocks();
  api.getProfile.mockResolvedValue(PROFILE);
});

test('shows the verified business details and the contact details', async () => {
  render(<BusinessProfilePage />);

  expect(await screen.findByText('BrightMart')).toBeInTheDocument();
  expect(screen.getByText('PV00234567')).toBeInTheDocument();
  expect(screen.getByText('Approved')).toBeInTheDocument();
  expect(screen.getByText('orders@brightmart.lk')).toBeInTheDocument();
  expect(screen.getByText('+94 77 123 4567')).toBeInTheDocument();
  expect(screen.getByText('12 Galle Road, Colombo 03')).toBeInTheDocument();
});

test('saves new contact details in the stored form', async () => {
  api.updateProfile.mockResolvedValue({
    ...PROFILE,
    contactEmail: 'hello@brightmart.lk',
    contactPhone: '+94712345678',
  });
  render(<BusinessProfilePage />);
  await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));

  const email = screen.getByLabelText('Contact Email');
  await userEvent.clear(email);
  await userEvent.type(email, ' hello@brightmart.lk ');
  const phone = screen.getByRole('textbox', { name: /Contact Phone/ });
  await userEvent.clear(phone);
  await userEvent.type(phone, '071 234 5678');
  await userEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(api.updateProfile).toHaveBeenCalledWith({
    contactEmail: 'hello@brightmart.lk',
    contactPhone: '+94712345678',
    businessAddress: '12 Galle Road, Colombo 03',
  });
  expect(await screen.findByText('Your contact details were saved.')).toBeInTheDocument();
  expect(screen.getByText('hello@brightmart.lk')).toBeInTheDocument();
});

test('does not send an invalid address', async () => {
  render(<BusinessProfilePage />);
  await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));

  const address = screen.getByLabelText('Business Address');
  await userEvent.clear(address);
  await userEvent.type(address, 'Colombo');
  await userEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(await screen.findByText('Please enter the full business address')).toBeInTheDocument();
  expect(api.updateProfile).not.toHaveBeenCalled();
});

test('shows a server field error on its input', async () => {
  api.updateProfile.mockRejectedValue(
    new ApiRequestError('Validation failed', {
      status: 400,
      fieldErrors: [{ field: 'contactEmail', message: 'Enter a valid email address' }],
    }),
  );
  render(<BusinessProfilePage />);
  await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
  await userEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
});

test('can cancel editing without saving', async () => {
  render(<BusinessProfilePage />);
  await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
  await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

  expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
  expect(api.updateProfile).not.toHaveBeenCalled();
});
