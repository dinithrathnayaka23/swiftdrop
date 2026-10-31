import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import MerchantSignupPage from './MerchantSignupPage';

jest.mock('../../api/auth', () => ({
  authApi: { registerMerchant: jest.fn() },
}));

const registerMerchant = jest.mocked(authApi.registerMerchant);

function SubmittedProbe() {
  const { state } = useLocation();
  return <p>submitted: {JSON.stringify(state)}</p>;
}

function renderSignup() {
  render(
    <MemoryRouter initialEntries={['/signup/merchant']}>
      <Routes>
        <Route path="/signup/merchant" element={<MerchantSignupPage />} />
        <Route path="/signup/merchant/submitted" element={<SubmittedProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

const submit = () => userEvent.click(screen.getByRole('button', { name: 'Submit for Approval' }));

async function fillValidForm(registrationNo = 'pv 0023-4567') {
  await userEvent.type(screen.getByLabelText('Business Name'), '  BrightMart ');
  await userEvent.type(screen.getByLabelText('Business Registration No.'), registrationNo);
  await userEvent.type(screen.getByLabelText('Contact Person Name'), 'Sunil Fernando');
  await userEvent.type(screen.getByLabelText('Email'), 'sunil@brightmart.lk');
  await userEvent.type(screen.getByLabelText('Phone Number'), '077 123 4567');
  await userEvent.type(screen.getByLabelText('Business Address'), '12 Galle Road, Colombo 03');
  await userEvent.type(screen.getByLabelText('Password'), 'merchant2026');
}

describe('MerchantSignupPage', () => {
  it('rejects a registration number that is not PV/PB/GA + 8 digits', async () => {
    renderSignup();
    await fillValidForm('AB1234');

    await submit();

    expect(screen.getByLabelText('Business Registration No.')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(registerMerchant).not.toHaveBeenCalled();
  });

  it('sends cleaned-up values and opens the "submitted" page', async () => {
    registerMerchant.mockResolvedValue({
      merchantId: 'm1',
      businessName: 'BrightMart',
      registrationNo: 'PV00234567',
      approvalStatus: 'PENDING',
      submittedAt: '2026-10-31T10:00:00Z',
    });
    renderSignup();
    await fillValidForm();

    await submit();

    expect(await screen.findByText(/submitted:/)).toHaveTextContent('PV00234567');
    expect(registerMerchant).toHaveBeenCalledWith({
      businessName: 'BrightMart',
      registrationNo: 'PV00234567',
      contactPersonName: 'Sunil Fernando',
      email: 'sunil@brightmart.lk',
      phone: '+94771234567',
      businessAddress: '12 Galle Road, Colombo 03',
      password: 'merchant2026',
    });
  });

  it('puts a "registration number already used" error on that field', async () => {
    registerMerchant.mockRejectedValue(
      new ApiRequestError('Conflict', {
        status: 409,
        fieldErrors: [
          {
            field: 'registrationNo',
            message: 'A business with this registration number is already registered',
          },
        ],
      }),
    );
    renderSignup();
    await fillValidForm();

    await submit();

    expect(
      await screen.findByText('A business with this registration number is already registered'),
    ).toBeInTheDocument();
  });
});
