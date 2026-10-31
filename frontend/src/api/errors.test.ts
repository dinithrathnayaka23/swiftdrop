import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { ApiRequestError, getErrorMessage, request, toApiRequestError } from './errors';

function axiosError(status: number | null, data?: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() };
  const response = status
    ? ({ status, data, statusText: '', headers: {}, config } as AxiosResponse)
    : undefined;
  return new AxiosError('Request failed', 'ERR', config, null, response);
}

describe('toApiRequestError', () => {
  it('uses the backend ErrorResponse body (message, code, field errors)', () => {
    const err = toApiRequestError(
      axiosError(409, {
        code: 'CONFLICT',
        message: 'An account with this email already exists',
        fieldErrors: [{ field: 'email', message: 'An account with this email already exists' }],
      }),
    );

    expect(err).toBeInstanceOf(ApiRequestError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('CONFLICT');
    expect(err.fieldErrors).toEqual([
      { field: 'email', message: 'An account with this email already exists' },
    ]);
  });

  it('explains a network failure (no response at all)', () => {
    expect(toApiRequestError(axiosError(null)).message).toMatch(/Unable to reach the server/);
  });

  it('falls back to a generic message for an unexpected body or error', () => {
    expect(toApiRequestError(axiosError(500, '<html>')).message).toBe(
      'Something went wrong. Please try again.',
    );
    expect(toApiRequestError(new TypeError('boom')).message).toBe(
      'Something went wrong. Please try again.',
    );
  });

  it('passes an ApiRequestError through unchanged', () => {
    const original = new ApiRequestError('Already normalised', { status: 400 });
    expect(toApiRequestError(original)).toBe(original);
    expect(getErrorMessage(original)).toBe('Already normalised');
  });
});

describe('request', () => {
  it('returns the response data', async () => {
    await expect(request(async () => ({ data: { ok: true } }))).resolves.toEqual({ ok: true });
  });

  it('rethrows failures as ApiRequestError', async () => {
    await expect(
      request(async () => {
        throw axiosError(401, { code: 'UNAUTHORIZED', message: 'Invalid email or password' });
      }),
    ).rejects.toMatchObject({ status: 401, message: 'Invalid email or password' });
  });
});
