import { authApi } from './auth';
import { apiClient } from './client';

jest.mock('./client', () => ({
  apiClient: { get: jest.fn(), post: jest.fn() },
}));

const get = jest.mocked(apiClient.get);
const post = jest.mocked(apiClient.post);

/** Each authApi function must call the matching backend endpoint and return the response body. */
describe('authApi', () => {
  beforeEach(() => {
    post.mockResolvedValue({ data: 'response-body' });
    get.mockResolvedValue({ data: { enabled: true, clientId: 'abc' } });
  });

  it.each([
    ['login', '/auth/login', { email: 'a@b.lk', password: 'secret123', rememberMe: true }],
    ['loginWithGoogle', '/auth/google', { credential: 'google-id-token', rememberMe: false }],
    ['registerCustomer', '/auth/register/customer', { fullName: 'A', email: 'a@b.lk' }],
    ['registerMerchant', '/auth/register/merchant', { businessName: 'Shop' }],
  ] as const)('%s posts to %s', async (method, url, body) => {
    const call = authApi[method] as (b: unknown) => Promise<unknown>;

    await expect(call(body)).resolves.toBe('response-body');
    expect(post).toHaveBeenCalledWith(url, body);
  });

  it('refresh and logout send no body: the browser adds the session cookie', async () => {
    await expect(authApi.refresh()).resolves.toBe('response-body');
    expect(post).toHaveBeenCalledWith('/auth/refresh');

    await expect(authApi.logout()).resolves.toBeUndefined();
    expect(post).toHaveBeenCalledWith('/auth/logout');
  });

  it('email confirmation endpoints', async () => {
    await expect(authApi.confirmEmailVerification({ token: 't' })).resolves.toBe('response-body');
    expect(post).toHaveBeenCalledWith('/auth/verify-email/confirm', { token: 't' });

    await expect(authApi.resendVerification({ email: 'a@b.lk' })).resolves.toBeUndefined();
    expect(post).toHaveBeenCalledWith('/auth/verify-email/resend', { email: 'a@b.lk' });
  });

  it('googleConfig reads /auth/google/config', async () => {
    await expect(authApi.googleConfig()).resolves.toEqual({ enabled: true, clientId: 'abc' });
    expect(get).toHaveBeenCalledWith('/auth/google/config');
  });

  it('password reset calls resolve without a body', async () => {
    await expect(authApi.requestPasswordReset({ email: 'a@b.lk' })).resolves.toBeUndefined();
    expect(post).toHaveBeenCalledWith('/auth/password-reset', { email: 'a@b.lk' });

    await expect(
      authApi.confirmPasswordReset({ token: 't', newPassword: 'newpass2026' }),
    ).resolves.toBeUndefined();
    expect(post).toHaveBeenCalledWith('/auth/password-reset/confirm', {
      token: 't',
      newPassword: 'newpass2026',
    });
  });
});
