import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiRequestError } from '../../../api/errors';
import { adminUsersApi } from '../api';
import type { UserStats, UserSummary } from '../types';
import UserManagementPage from './UserManagementPage';

jest.mock('../api', () => ({
  adminUsersApi: {
    list: jest.fn(),
    stats: jest.fn(),
    changeRole: jest.fn(),
    changeStatus: jest.fn(),
  },
}));

const api = jest.mocked(adminUsersApi);

function user(overrides: Partial<UserSummary>): UserSummary {
  return {
    id: 'u1',
    fullName: 'Kasun Perera',
    email: 'kasun@example.com',
    phone: '+94771234567',
    role: 'CUSTOMER',
    accountStatus: 'ACTIVE',
    emailVerified: true,
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

function page(content: UserSummary[]) {
  return { content, page: { size: 10, number: 0, totalElements: content.length, totalPages: 1 } };
}

const STATS: UserStats = {
  total: 12,
  byRole: { CUSTOMER: 7, MERCHANT: 2, COURIER: 2, DISPATCHER_ADMIN: 1 },
  byStatus: { ACTIVE: 10, PENDING_APPROVAL: 1, SUSPENDED: 0, DEACTIVATED: 1 },
};

const KASUN = user({});
const NIMAL = user({
  id: 'u2',
  fullName: 'Nimal Silva',
  email: 'nimal@example.com',
  role: 'COURIER',
});
const ADMIN = user({ id: 'u3', fullName: 'SwiftDrop Admin', role: 'DISPATCHER_ADMIN' });

beforeEach(() => {
  jest.clearAllMocks();
  api.list.mockResolvedValue(page([KASUN, NIMAL, ADMIN]));
  api.stats.mockResolvedValue(STATS);
});

const rowOf = (name: string) => screen.getByText(name).closest('tr')!;

test('shows the accounts and the dashboard numbers from the API', async () => {
  render(<UserManagementPage />);

  expect(await screen.findByText('Kasun Perera')).toBeInTheDocument();
  expect(screen.getByText('nimal@example.com')).toBeInTheDocument();
  expect(await screen.findByText('12')).toBeInTheDocument(); // total users card
  expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 10 }));
});

test('only customers and couriers can be switched, and the admin account has no actions', async () => {
  render(<UserManagementPage />);
  await screen.findByText('Kasun Perera');

  expect(
    within(rowOf('Kasun Perera')).getByRole('button', { name: 'Make courier' }),
  ).toBeInTheDocument();
  expect(
    within(rowOf('Nimal Silva')).getByRole('button', { name: 'Make customer' }),
  ).toBeInTheDocument();
  expect(within(rowOf('SwiftDrop Admin')).queryAllByRole('button')).toHaveLength(0);
});

test('searching and filtering ask the API again', async () => {
  render(<UserManagementPage />);
  await screen.findByText('Kasun Perera');

  await userEvent.type(screen.getByLabelText('Search users'), 'nimal');
  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'nimal' })),
  );

  await userEvent.selectOptions(screen.getByLabelText('Filter by role'), 'COURIER');
  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'nimal', role: 'COURIER' }),
    ),
  );
});

test('a role change is confirmed first, then saved and the list reloads', async () => {
  api.changeRole.mockResolvedValue({ ...KASUN, role: 'COURIER' });
  render(<UserManagementPage />);
  await screen.findByText('Kasun Perera');
  const callsBefore = api.list.mock.calls.length;

  await userEvent.click(
    within(rowOf('Kasun Perera')).getByRole('button', { name: 'Make courier' }),
  );
  expect(api.changeRole).not.toHaveBeenCalled();
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Make courier' }),
  );

  expect(api.changeRole).toHaveBeenCalledWith('u1', 'COURIER');
  expect(await screen.findByText('Kasun Perera is now a courier.')).toBeInTheDocument();
  await waitFor(() => expect(api.list.mock.calls.length).toBeGreaterThan(callsBefore));
});

test('suspending a user sends the new status', async () => {
  api.changeStatus.mockResolvedValue({ ...KASUN, accountStatus: 'SUSPENDED' });
  render(<UserManagementPage />);
  await screen.findByText('Kasun Perera');

  await userEvent.click(within(rowOf('Kasun Perera')).getByRole('button', { name: 'Suspend' }));
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Suspend' }),
  );

  expect(api.changeStatus).toHaveBeenCalledWith('u1', 'SUSPENDED');
});

test('a refused change shows the server message', async () => {
  api.changeStatus.mockRejectedValue(
    new ApiRequestError('The admin account always stays active', { status: 409 }),
  );
  render(<UserManagementPage />);
  await screen.findByText('Kasun Perera');

  await userEvent.click(within(rowOf('Kasun Perera')).getByRole('button', { name: 'Deactivate' }));
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Deactivate' }),
  );

  expect(await screen.findByText('The admin account always stays active')).toBeInTheDocument();
});

test('a failed load offers to try again', async () => {
  api.list.mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
  render(<UserManagementPage />);

  expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findByText('Kasun Perera')).toBeInTheDocument();
});

test('no matches shows an empty state', async () => {
  api.list.mockResolvedValue(page([]));
  render(<UserManagementPage />);

  expect(await screen.findByText('No users found')).toBeInTheDocument();
});

test('filtering by status asks the API again from the first page', async () => {
  render(<UserManagementPage />);
  await screen.findByText('Kasun Perera');

  await userEvent.selectOptions(screen.getByLabelText('Filter by status'), 'SUSPENDED');

  await waitFor(() =>
    expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'SUSPENDED', page: 0 }),
    ),
  );
});

test('a suspended user can be reactivated', async () => {
  const suspended = user({ id: 'u9', fullName: 'Ruwan Silva', accountStatus: 'SUSPENDED' });
  api.list.mockResolvedValue(page([suspended]));
  api.changeStatus.mockResolvedValue({ ...suspended, accountStatus: 'ACTIVE' });
  render(<UserManagementPage />);
  await screen.findByText('Ruwan Silva');

  expect(
    within(rowOf('Ruwan Silva')).queryByRole('button', { name: 'Suspend' }),
  ).not.toBeInTheDocument();
  await userEvent.click(within(rowOf('Ruwan Silva')).getByRole('button', { name: 'Reactivate' }));
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Reactivate' }),
  );

  expect(api.changeStatus).toHaveBeenCalledWith('u9', 'ACTIVE');
});

test('a merchant waiting for approval has no actions here', async () => {
  api.list.mockResolvedValue(
    page([
      user({
        id: 'u7',
        fullName: 'Sunil Fernando',
        role: 'MERCHANT',
        accountStatus: 'PENDING_APPROVAL',
      }),
    ]),
  );
  render(<UserManagementPage />);
  await screen.findByText('Sunil Fernando');

  expect(within(rowOf('Sunil Fernando')).queryAllByRole('button')).toHaveLength(0);
});

test('cancelling the confirmation changes nothing', async () => {
  render(<UserManagementPage />);
  await screen.findByText('Kasun Perera');

  await userEvent.click(within(rowOf('Kasun Perera')).getByRole('button', { name: 'Suspend' }));
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Cancel' }),
  );

  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(api.changeStatus).not.toHaveBeenCalled();
});

test('pages through long lists', async () => {
  api.list.mockImplementation((filters) =>
    Promise.resolve({
      content: [filters.page === 1 ? NIMAL : KASUN],
      page: { size: 10, number: filters.page ?? 0, totalElements: 15, totalPages: 2 },
    }),
  );
  render(<UserManagementPage />);
  await screen.findByText('Page 1 of 2');
  expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();

  await userEvent.click(screen.getByRole('button', { name: 'Next' }));

  expect(await screen.findByText('Nimal Silva')).toBeInTheDocument();
  expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  await userEvent.click(screen.getByRole('button', { name: 'Previous' }));
  expect(await screen.findByText('Kasun Perera')).toBeInTheDocument();
});

test('the cards show "–" when the numbers cannot be loaded, and the list still works', async () => {
  api.stats.mockRejectedValue(new Error('down'));
  render(<UserManagementPage />);

  expect(await screen.findByText('Kasun Perera')).toBeInTheDocument();
  expect(screen.getAllByText('–').length).toBeGreaterThanOrEqual(4);
});
