import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { refreshSession } from '../../../api/client';
import { ApiRequestError } from '../../../api/errors';
import { profileApi } from '../api';
import type { UserProfile } from '../types';
import ProfilePage from './ProfilePage';

jest.mock('../api', () => ({
  profileApi: { get: jest.fn(), update: jest.fn(), sendPasswordResetLink: jest.fn() },
}));
jest.mock('../../../api/client', () => ({
  ...jest.requireActual('../../../api/client'),
  refreshSession: jest.fn(),
}));

const api = jest.mocked(profileApi);

const PROFILE: UserProfile = {
  id: 'u1',
  fullName: 'Kasun Perera',
  email: 'kasun@example.com',
  phone: '+94771234567',
  role: 'CUSTOMER',
  accountStatus: 'ACTIVE',
  createdAt: '2026-10-01T08:00:00Z',
};

beforeEach(() => {
  jest.clearAllMocks();
  api.get.mockResolvedValue(PROFILE);
});

const edit = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
};

test('shows the signed-in user’s details', async () => {
  render(<ProfilePage />);

  expect(await screen.findByText('Kasun Perera')).toBeInTheDocument();
  expect(screen.getByText('+94 77 123 4567')).toBeInTheDocument();
  expect(screen.getByText('kasun@example.com')).toBeInTheDocument();
  expect(screen.getByText('Active')).toBeInTheDocument();
});

test('editing starts from the current details and saves the changes', async () => {
  api.update.mockResolvedValue({ ...PROFILE, fullName: 'Kasun Silva', phone: '+94712345678' });
  render(<ProfilePage />);
  await edit();

  const name = screen.getByLabelText('Full Name');
  expect(name).toHaveValue('Kasun Perera');
  await userEvent.clear(name);
  await userEvent.type(name, ' Kasun Silva ');
  const phone = screen.getByRole('textbox', { name: /Phone Number/ });
  await userEvent.clear(phone);
  await userEvent.type(phone, '071 234 5678');
  await userEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(api.update).toHaveBeenCalledWith({ fullName: 'Kasun Silva', phone: '+94712345678' });
  expect(await screen.findByText('Your details were saved.')).toBeInTheDocument();
  expect(screen.getByText('Kasun Silva')).toBeInTheDocument();
  expect(screen.getByText('+94 71 234 5678')).toBeInTheDocument();
  expect(refreshSession).toHaveBeenCalled(); // the top bar picks up the new name
});

test('invalid details are explained without saving', async () => {
  render(<ProfilePage />);
  await edit();

  const name = screen.getByLabelText('Full Name');
  await userEvent.clear(name);
  await userEvent.type(name, 'K4sun');
  await userEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(screen.getByText(/can only contain letters/)).toBeInTheDocument();
  expect(api.update).not.toHaveBeenCalled();
});

test('field errors from the server are shown on the input', async () => {
  api.update.mockRejectedValue(
    new ApiRequestError('Validation failed', {
      status: 400,
      fieldErrors: [{ field: 'phone', message: 'Enter a valid phone number' }],
    }),
  );
  render(<ProfilePage />);
  await edit();

  await userEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(await screen.findByText('Enter a valid phone number')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Save Changes' })).toBeInTheDocument(); // still editing
});

test('cancel leaves the details unchanged', async () => {
  render(<ProfilePage />);
  await edit();

  await userEvent.type(screen.getByLabelText('Full Name'), 'xyz');
  await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

  expect(api.update).not.toHaveBeenCalled();
  expect(screen.getByText('Kasun Perera')).toBeInTheDocument();
});

test('change password emails a reset link to the user’s own address', async () => {
  api.sendPasswordResetLink.mockResolvedValue(undefined);
  render(<ProfilePage />);

  await userEvent.click(await screen.findByRole('button', { name: 'Change Password' }));

  expect(api.sendPasswordResetLink).toHaveBeenCalledWith('kasun@example.com');
  expect(await screen.findByText(/We've sent a link to kasun@example.com/)).toBeInTheDocument();
});

test('a failed load offers to try again', async () => {
  api.get.mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
  render(<ProfilePage />);

  expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findByText('Kasun Perera')).toBeInTheDocument();
});
