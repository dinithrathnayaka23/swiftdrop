import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { apiClient } from './client';
import { httpCouriersApi as api } from './couriers';
import { ApiRequestError } from './errors';

// Jest's env stand-in turns the mock APIs on (src/test/env.ts): test the HTTP implementation directly.
jest.mock('./client', () => ({
  apiClient: { get: jest.fn(), post: jest.fn(), patch: jest.fn() },
}));

const get = jest.mocked(apiClient.get);
const post = jest.mocked(apiClient.post);
const patch = jest.mocked(apiClient.patch);

function httpError(status: number, body: object) {
  const config = { headers: new AxiosHeaders() };
  const response = {
    status,
    statusText: '',
    data: body,
    headers: {},
    config,
  } as unknown as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, response);
}

describe('couriersApi (HTTP)', () => {
  beforeEach(() => jest.resetAllMocks());

  it('lists with the filters as query parameters', async () => {
    get.mockResolvedValue({ data: { content: [] } });
    const filters = {
      search: 'ramesh',
      availability: 'ONLINE' as const,
      serviceAreaId: 'z1',
      vehicleType: 'Van',
      page: 1,
      size: 20,
    };

    await api.list(filters);

    expect(get).toHaveBeenCalledWith('/admin/couriers', { params: filters });
  });

  it('reads the summary and one courier', async () => {
    get.mockResolvedValue({ data: {} });

    await api.summary();
    await api.get('c 1');

    expect(get).toHaveBeenNthCalledWith(1, '/admin/couriers/summary');
    expect(get).toHaveBeenNthCalledWith(2, '/admin/couriers/c%201');
  });

  it('posts a new courier with the CreateCourierRequest fields', async () => {
    post.mockResolvedValue({ data: { id: 'c1' } });
    const courier = {
      fullName: 'Ramesh Perera',
      email: 'ramesh@example.com',
      phone: '+94771234567',
      password: 'courier2026',
      vehicleType: 'Motorbike',
      vehicleRegistration: 'ABC-1234',
      serviceAreaIds: ['z1'],
    };

    await api.create(courier);

    expect(post).toHaveBeenCalledWith('/admin/couriers', courier);
  });

  it('patches the profile and the availability', async () => {
    patch.mockResolvedValue({ data: {} });

    await api.update('c1', { vehicleType: 'Van', serviceAreaIds: [] });
    await api.changeAvailability('c1', 'ON_BREAK');

    expect(patch).toHaveBeenNthCalledWith(1, '/admin/couriers/c1', {
      vehicleType: 'Van',
      serviceAreaIds: [],
    });
    expect(patch).toHaveBeenNthCalledWith(2, '/admin/couriers/c1/availability', {
      availability: 'ON_BREAK',
    });
  });

  it('lists a courier’s assignments with open, page and size', async () => {
    get.mockResolvedValue({ data: { content: [] } });

    await api.listAssignments('c1', { open: false, page: 2, size: 10 });

    expect(get).toHaveBeenCalledWith('/admin/couriers/c1/assignments', {
      params: { open: false, page: 2, size: 10 },
    });
  });

  it('turns a 409 into an ApiRequestError with the field errors', async () => {
    post.mockRejectedValue(
      httpError(409, {
        code: 'CONFLICT',
        message: 'An account with this email already exists',
        fieldErrors: [{ field: 'email', message: 'An account with this email already exists' }],
      }),
    );

    const error = await api.create({} as never).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({ status: 409, fieldErrors: [{ field: 'email' }] });
  });
});
