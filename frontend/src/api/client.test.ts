import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from 'axios';
import { getToken, loadSession, saveSession } from '../utils/sessionStorage';
import {
  apiClient,
  refreshSession,
  setAccountBlockedHandler,
  setSessionUpdatedHandler,
  setUnauthorizedHandler,
} from './client';

const user = { id: '1', displayName: 'Nimal', role: 'CUSTOMER' as const };

function ok(config: InternalAxiosRequestConfig, data: unknown) {
  return Promise.resolve({ data, status: 200, statusText: 'OK', headers: {}, config });
}

function failWith(status: number) {
  return (config: InternalAxiosRequestConfig) => {
    const response = { data: {}, status, statusText: '', headers: {}, config };
    return Promise.reject(
      new AxiosError(String(status), 'ERR_BAD_RESPONSE', config, null, response),
    );
  };
}

const unauthorized = failWith(401);

function networkDown(config: InternalAxiosRequestConfig) {
  return Promise.reject(new AxiosError('Network Error', 'ERR_NETWORK', config, null, undefined));
}

function auth(config: InternalAxiosRequestConfig) {
  return AxiosHeaders.from(config.headers).get('Authorization');
}

/** Fake backend: /users/me accepts only "Bearer new"; /auth/refresh behaves as given. */
function fakeBackend(refresh: (config: InternalAxiosRequestConfig) => ReturnType<AxiosAdapter>) {
  const calls: string[] = [];
  const adapter: AxiosAdapter = (config) => {
    calls.push(`${config.url} ${auth(config) ?? '-'}`);
    if (config.url === '/auth/refresh') return refresh(config);
    return auth(config) === 'Bearer new' ? ok(config, { name: 'Nimal' }) : unauthorized(config);
  };
  apiClient.defaults.adapter = adapter;
  return calls;
}

// The refresh token travels in the httpOnly cookie, so the body carries only the access token.
const refreshSucceeds = (config: InternalAxiosRequestConfig) =>
  ok(config, { token: 'new', expiresIn: 900, rememberMe: true, user });

describe('apiClient', () => {
  const onUnauthorized = jest.fn();
  const onSessionUpdated = jest.fn();

  beforeEach(() => {
    onUnauthorized.mockReset();
    onSessionUpdated.mockReset();
    setUnauthorizedHandler(onUnauthorized);
    setSessionUpdatedHandler(onSessionUpdated);
    saveSession({ token: 'expired', user }, true);
  });

  afterEach(() => {
    setUnauthorizedHandler(null);
    setSessionUpdatedHandler(null);
  });

  it('sends cookies (for the session cookie) with its requests', () => {
    expect(apiClient.defaults.withCredentials).toBe(true);
  });

  it('renews an expired access token and retries the request transparently', async () => {
    const calls = fakeBackend(refreshSucceeds);

    const response = await apiClient.get('/users/me');

    expect(response.data).toEqual({ name: 'Nimal' });
    expect(calls).toEqual([
      '/users/me Bearer expired',
      '/auth/refresh Bearer expired',
      '/users/me Bearer new',
    ]);
    // The new access token is kept in memory; only the user is stored ("Remember me": localStorage).
    expect(getToken()).toBe('new');
    expect(loadSession()).toEqual({ token: 'new', user });
    expect(window.localStorage.getItem('swiftdrop.user')).not.toContain('new');
    expect(onSessionUpdated).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('uses a single refresh call for several requests failing at once', async () => {
    const calls = fakeBackend(refreshSucceeds);

    await Promise.all([
      apiClient.get('/users/me'),
      apiClient.get('/users/me'),
      apiClient.get('/users/me'),
    ]);

    expect(calls.filter((c) => c.startsWith('/auth/refresh'))).toHaveLength(1);
  });

  it('signs the user out when the session is over (401 or 403 on refresh)', async () => {
    fakeBackend(unauthorized);
    await expect(apiClient.get('/users/me')).rejects.toThrow();
    expect(onUnauthorized).toHaveBeenCalledTimes(1);

    saveSession({ token: 'expired', user }, true);
    fakeBackend(failWith(403));
    await expect(apiClient.get('/users/me')).rejects.toThrow();
    expect(onUnauthorized).toHaveBeenCalledTimes(2);
  });

  it('keeps the user signed in when the server is unreachable or failing', async () => {
    fakeBackend(networkDown);
    await expect(apiClient.get('/users/me')).rejects.toThrow();

    fakeBackend(failWith(503));
    await expect(apiClient.get('/users/me')).rejects.toThrow();

    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(getToken()).toBe('expired');
  });

  it('does not try to renew after a failed sign-in', async () => {
    const calls: string[] = [];
    apiClient.defaults.adapter = (config) => {
      calls.push(`${config.url}`);
      return unauthorized(config);
    };

    await expect(apiClient.post('/auth/login', {})).rejects.toThrow();
    expect(calls).toEqual(['/auth/login']);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});

describe('refreshSession', () => {
  it.each([
    [refreshSucceeds, 'renewed'],
    [unauthorized, 'expired'],
    [failWith(403), 'expired'],
    [failWith(503), 'unavailable'],
    [networkDown, 'unavailable'],
  ] as const)('reports the outcome (%#)', async (refresh, expected) => {
    fakeBackend(refresh);

    await expect(refreshSession()).resolves.toMatchObject({ status: expected });
  });
});

describe('blocked account', () => {
  const onBlocked = jest.fn();

  /** Every request answers with this status and error body. */
  function answerWith(status: number, data: unknown) {
    apiClient.defaults.adapter = (config) =>
      Promise.reject(
        new AxiosError(String(status), 'ERR_BAD_RESPONSE', config, null, {
          data,
          status,
          statusText: '',
          headers: {},
          config,
        }),
      );
  }

  beforeEach(() => {
    onBlocked.mockReset();
    setAccountBlockedHandler(onBlocked);
    saveSession({ token: 'valid', user }, true);
  });

  afterEach(() => setAccountBlockedHandler(null));

  it.each(['ACCOUNT_DISABLED', 'ACCOUNT_PENDING_APPROVAL'])(
    'reports a signed-in request refused with %s, and still fails the request',
    async (code) => {
      answerWith(403, { code, message: 'This merchant account is not active.' });

      await expect(apiClient.get('/merchant/profile')).rejects.toBeInstanceOf(AxiosError);

      expect(onBlocked).toHaveBeenCalledWith({
        code,
        message: 'This merchant account is not active.',
      });
    },
  );

  it('ignores other 403s', async () => {
    answerWith(403, { code: 'FORBIDDEN', message: 'Access denied' });

    await expect(apiClient.get('/merchant/profile')).rejects.toBeInstanceOf(AxiosError);
    expect(onBlocked).not.toHaveBeenCalled();
  });

  it('leaves a blocked sign-in to the sign-in page', async () => {
    answerWith(403, { code: 'ACCOUNT_DISABLED', message: 'Disabled' });

    await expect(apiClient.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError);
    expect(onBlocked).not.toHaveBeenCalled();
  });

  it('ignores requests sent without a token', async () => {
    saveSession({ token: '', user }, true);
    answerWith(403, { code: 'ACCOUNT_DISABLED', message: 'Disabled' });

    await expect(apiClient.get('/public/settings')).rejects.toBeInstanceOf(AxiosError);
    expect(onBlocked).not.toHaveBeenCalled();
  });
});
