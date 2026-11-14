import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { apiClient } from './client';
import { httpDispatchApi as api } from './dispatch';
import { ApiRequestError } from './errors';

// Jest's env stand-in turns the mock APIs on (src/test/env.ts): test the HTTP implementation directly.
jest.mock('./client', () => ({
  apiClient: { get: jest.fn(), post: jest.fn() },
}));

const get = jest.mocked(apiClient.get);
const post = jest.mocked(apiClient.post);

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

describe('dispatchApi (HTTP)', () => {
  beforeEach(() => jest.resetAllMocks());

  it('assigns one parcel with the AssignParcelRequest body', async () => {
    post.mockResolvedValue({ data: { id: 'a1' } });

    await expect(api.assign('p1', 'c1')).resolves.toEqual({ id: 'a1' });

    expect(post).toHaveBeenCalledWith('/admin/dispatch/assignments', {
      parcelId: 'p1',
      courierId: 'c1',
    });
  });

  it('bulk assigns with the BulkAssignRequest body and returns both lists', async () => {
    const result = { assigned: [{ id: 'a1' }], failed: [{ parcelId: 'p2', reason: 'No' }] };
    post.mockResolvedValue({ data: result });

    await expect(api.assignAll('c1', ['p1', 'p2'])).resolves.toEqual(result);

    expect(post).toHaveBeenCalledWith('/admin/dispatch/assignments/bulk', {
      courierId: 'c1',
      parcelIds: ['p1', 'p2'],
    });
  });

  it('reassigns and unassigns under the parcel path, with the reason only when given', async () => {
    post.mockResolvedValue({ data: {} });

    await api.reassign('p 1', 'c2', 'Vehicle broke down');
    await api.unassign('p1', 'Customer asked to wait');
    await api.unassign('p1');

    expect(post).toHaveBeenNthCalledWith(1, '/admin/dispatch/parcels/p%201/reassign', {
      courierId: 'c2',
      reason: 'Vehicle broke down',
    });
    expect(post).toHaveBeenNthCalledWith(2, '/admin/dispatch/parcels/p1/unassign', {
      reason: 'Customer asked to wait',
    });
    expect(post).toHaveBeenNthCalledWith(3, '/admin/dispatch/parcels/p1/unassign', {});
  });

  it('reads one assignment', async () => {
    get.mockResolvedValue({ data: { id: 'a1' } });

    await api.getAssignment('a1');

    expect(get).toHaveBeenCalledWith('/admin/dispatch/assignments/a1');
  });

  it('turns a 409 into an ApiRequestError with the backend message', async () => {
    post.mockRejectedValue(
      httpError(409, {
        code: 'CONFLICT',
        message: 'This courier is offline and cannot be given parcels',
      }),
    );

    const error = await api.assign('p1', 'c1').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({
      status: 409,
      message: 'This courier is offline and cannot be given parcels',
    });
  });

  it('keeps the field errors of a 400', async () => {
    post.mockRejectedValue(
      httpError(400, {
        code: 'VALIDATION_FAILED',
        message: 'Request validation failed',
        fieldErrors: [{ field: 'reason', message: 'Reason is required' }],
      }),
    );

    const error = await api.reassign('p1', 'c1', ' ').catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 400, fieldErrors: [{ field: 'reason' }] });
  });
});
