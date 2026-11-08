import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { apiClient } from './client';
import { ApiRequestError } from './errors';
import { parseRetryAfter, trackingApi, TrackingRateLimitError } from './tracking';

// Jest's env stand-in turns the mock APIs on (src/test/env.ts): tracking must still use the backend.
jest.mock('./client', () => ({ apiClient: { get: jest.fn() } }));

const get = jest.mocked(apiClient.get);

function httpError(status: number, body: object, headers: Record<string, string> = {}) {
  const config = { headers: new AxiosHeaders() };
  const response = {
    status,
    statusText: '',
    data: body,
    headers,
    config,
  } as unknown as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, response);
}

describe('trackingApi (HTTP)', () => {
  beforeEach(() => get.mockReset());

  it('gets the public tracking of an encoded tracking number', async () => {
    get.mockResolvedValue({ data: { trackingNumber: 'SD1' } });

    await expect(trackingApi.getTracking('SD 1')).resolves.toEqual({ trackingNumber: 'SD1' });
    expect(get).toHaveBeenCalledWith('/public/tracking/SD%201');
  });

  it('returns null for 404', async () => {
    get.mockRejectedValue(httpError(404, { code: 'NOT_FOUND', message: 'No parcel' }));

    await expect(trackingApi.getTracking('SD0')).resolves.toBeNull();
  });

  it('turns 429 into a TrackingRateLimitError with the Retry-After wait', async () => {
    get.mockRejectedValue(
      httpError(
        429,
        { code: 'TOO_MANY_REQUESTS', message: 'Too many tracking look-ups.' },
        { 'retry-after': '42' },
      ),
    );

    const error = await trackingApi.getTracking('SD1').catch((err: unknown) => err);

    expect(error).toBeInstanceOf(TrackingRateLimitError);
    expect(error).toMatchObject({ status: 429, retryAfterSeconds: 42 });
  });

  it('passes other errors on as ApiRequestError', async () => {
    get.mockRejectedValue(httpError(500, { code: 'INTERNAL_ERROR', message: 'Server error' }));

    const error = await trackingApi.getTracking('SD1').catch((err: unknown) => err);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).not.toBeInstanceOf(TrackingRateLimitError);
    expect(error).toMatchObject({ status: 500, message: 'Server error' });
  });
});

describe('parseRetryAfter', () => {
  it('reads whole seconds', () => {
    expect(parseRetryAfter('30')).toBe(30);
    expect(parseRetryAfter(' 5 ')).toBe(5);
  });

  it('never waits less than a second', () => {
    expect(parseRetryAfter('0')).toBe(1);
  });

  it('reads an HTTP date', () => {
    const now = Date.parse('2026-11-06T10:00:00Z');
    expect(parseRetryAfter('Fri, 06 Nov 2026 10:00:20 GMT', now)).toBe(20);
  });

  it('falls back to a minute when the header is missing or unreadable', () => {
    expect(parseRetryAfter(undefined)).toBe(60);
    expect(parseRetryAfter('')).toBe(60);
    expect(parseRetryAfter('soon')).toBe(60);
  });
});
