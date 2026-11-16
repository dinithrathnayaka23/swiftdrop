import { apiClient } from '../../api/client';
import { ApiRequestError } from '../../api/errors';
import { adminUsersApi } from './api';

jest.mock('../../api/client', () => ({
  apiClient: { get: jest.fn(), patch: jest.fn() },
}));

const client = jest.mocked(apiClient);

beforeEach(() => {
  jest.clearAllMocks();
});

test('lists users with only the filters that are set', async () => {
  const paged = { content: [], page: { size: 10, number: 0, totalElements: 0, totalPages: 0 } };
  client.get.mockResolvedValue({ data: paged });

  const result = await adminUsersApi.list({
    search: 'kasun',
    role: '',
    status: undefined,
    page: 2,
    size: 10,
  });

  expect(result).toBe(paged);
  expect(client.get).toHaveBeenCalledWith('/admin/users', {
    params: { search: 'kasun', page: 2, size: 10 },
  });
});

test('loads the dashboard numbers', async () => {
  client.get.mockResolvedValue({ data: { total: 3 } });

  await expect(adminUsersApi.stats()).resolves.toEqual({ total: 3 });
  expect(client.get).toHaveBeenCalledWith('/admin/users/stats');
});

test('changes a role and a status with the right body', async () => {
  client.patch.mockResolvedValue({ data: {} });

  await adminUsersApi.changeRole('u1', 'COURIER');
  await adminUsersApi.changeStatus('u1', 'SUSPENDED');

  expect(client.patch).toHaveBeenCalledWith('/admin/users/u1/role', { role: 'COURIER' });
  expect(client.patch).toHaveBeenCalledWith('/admin/users/u1/status', { status: 'SUSPENDED' });
});

test('server errors become ApiRequestError with the server message', async () => {
  client.patch.mockRejectedValue(
    Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: {
        status: 409,
        data: { code: 'CONFLICT', message: 'The admin account always stays active' },
      },
    }),
  );

  const failure = adminUsersApi.changeStatus('admin', 'SUSPENDED');

  await expect(failure).rejects.toBeInstanceOf(ApiRequestError);
  await expect(failure).rejects.toMatchObject({
    status: 409,
    message: 'The admin account always stays active',
  });
});
