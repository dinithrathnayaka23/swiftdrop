import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError } from '../../api/errors';
import { AuthProvider } from '../../context/AuthContext';
import CustomerSignupPage from './CustomerSignupPage';
import { getToken } from '../../utils/sessionStorage';
import { useLocation } from 'react-router';

function CheckEmailProbe() {
  const { state } = useLocation();
  return <p>check email: {(state as { email: string }).email}</p>;
}

jest.mock('../../api/auth', () => ({
  authApi: {
    login: jest.fn(),
    googleConfig: jest.fn(async () => ({ enabled: false, clientId: null })),
    loginWithGoogle: jest.fn(),
    registerCustomer: jest.fn(),
    registerMerchant: jest.fn(),
    requestPasswordReset: jest.fn(),
    confirmPasswordReset: jest.fn(),
  },
}));

const registerCustomer = jest.mocked(authApi.registerCustomer);

function renderSignup() {
  render(
    <MemoryRouter initialEntries={['/signup']}>
      <AuthProvider>
        <Routes>
          <Route path="/signup" element={<CustomerSignupPage />} />
          <Route path="/customer" element={<p>customer home</p>} />
          <Route path="/signup/check-email" element={<CheckEmailProbe />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

const submit = () => userEvent.click(screen.getByRole('button', { name: 'Create Account' }));

async function fillValidForm(email = 'jane@example.com') {
  await userEvent.type(screen.getByLabelText('Full Name'), 'Jane Doe');
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Phone Number'), '071 234 5678');
  await userEvent.type(screen.getByLabelText('Password'), 'parcel2026');
}

describe('CustomerSignupPage', () => {
  beforeEach(() => {
    registerCustomer.mockReset();
  });

  it('shows every error on submit and focuses the first invalid field', async () => {
    renderSignup();
    await submit();

    expect(screen.getByText('Full name is required')).toBeInTheDocument();
    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Phone number is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(screen.getByLabelText('Full Name')).toHaveFocus();
    expect(registerCustomer).not.toHaveBeenCalled();
  });

  it('shows a field error after leaving the field and clears it once fixed', async () => {
    renderSignup();
    const email = screen.getByLabelText('Email');

    await userEvent.type(email, 'not-an-email');
    expect(screen.queryByText('Enter a valid email address')).not.toBeInTheDocument();

    await userEvent.tab();
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(email).toHaveAttribute('aria-invalid', 'true');

    await userEvent.clear(email);
    await userEvent.type(email, 'jane@example.com');
    expect(screen.queryByText('Enter a valid email address')).not.toBeInTheDocument();
  });

  it('checks the phone length for the selected country', async () => {
    renderSignup();
    await userEvent.type(screen.getByLabelText('Phone Number'), '71234');
    await userEvent.tab();
    expect(screen.getByText(/valid Sri Lanka number \(9 digits/)).toBeInTheDocument();
  });

  it('puts the server error on the email field when it is already registered', async () => {
    registerCustomer.mockImplementation(async () => {
      throw new ApiRequestError('An account with this email already exists', {
        status: 409,
        code: 'CONFLICT',
        fieldErrors: [{ field: 'email', message: 'An account with this email already exists' }],
      });
    });
    renderSignup();
    await fillValidForm('taken@example.com');
    await submit();

    expect(
      await screen.findByText('An account with this email already exists'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });

  it('sends the phone in E.164 format, then asks to confirm the email (no sign-in yet)', async () => {
    registerCustomer.mockResolvedValue({ email: 'jane@example.com' });
    renderSignup();
    await fillValidForm();
    await submit();

    expect(await screen.findByText('check email: jane@example.com')).toBeInTheDocument();
    expect(getToken()).toBeNull();
    expect(registerCustomer).toHaveBeenCalledWith({
      fullName: 'Jane Doe',
      email: 'jane@example.com',
      phone: '+94712345678',
      password: 'parcel2026',
    });
  });
});
