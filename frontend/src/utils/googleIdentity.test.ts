import type { GoogleIdentityApi } from './googleIdentity';

// The loader keeps module-level state, so each test gets a fresh copy of the module.
function freshLoader(): Promise<typeof import('./googleIdentity')> {
  jest.resetModules();
  return import('./googleIdentity');
}

const fakeApi = { initialize: jest.fn(), renderButton: jest.fn() } as GoogleIdentityApi;

describe('loadGoogleIdentity', () => {
  afterEach(() => {
    delete window.google;
    document.head.querySelectorAll('script').forEach((s) => s.remove());
  });

  it('uses Google already on the page without adding a script', async () => {
    window.google = { accounts: { id: fakeApi } };
    const { loadGoogleIdentity } = await freshLoader();

    await expect(loadGoogleIdentity()).resolves.toBe(fakeApi);
    expect(document.head.querySelector('script')).toBeNull();
  });

  it("adds Google's script once and resolves when it has loaded", async () => {
    const { loadGoogleIdentity } = await freshLoader();

    const first = loadGoogleIdentity();
    const second = loadGoogleIdentity();
    const scripts = document.head.querySelectorAll('script');
    expect(scripts).toHaveLength(1);
    expect(scripts[0]).toHaveAttribute('src', 'https://accounts.google.com/gsi/client');

    window.google = { accounts: { id: fakeApi } };
    scripts[0]!.dispatchEvent(new Event('load'));

    await expect(first).resolves.toBe(fakeApi);
    await expect(second).resolves.toBe(fakeApi);
  });

  it('rejects when the script fails, and allows a retry later', async () => {
    const { loadGoogleIdentity } = await freshLoader();

    const failed = loadGoogleIdentity();
    document.head.querySelector('script')!.dispatchEvent(new Event('error'));
    await expect(failed).rejects.toThrow('could not be loaded');
    expect(document.head.querySelector('script')).toBeNull();

    void loadGoogleIdentity().catch(() => {});
    expect(document.head.querySelectorAll('script')).toHaveLength(1);
  });
});
