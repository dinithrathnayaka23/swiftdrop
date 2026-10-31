/**
 * Loads Google Identity Services (the official "Sign in with Google" script) on demand, once.
 * Only the small part of its API that the sign-in button uses is typed here.
 */

const SCRIPT_URL = 'https://accounts.google.com/gsi/client';

export interface GoogleCredentialResponse {
  /** The Google ID token (a JWT). The backend verifies it; the browser never trusts it. */
  credential: string;
}

export interface GoogleButtonOptions {
  type?: 'standard' | 'icon';
  theme?: 'outline' | 'filled_blue' | 'filled_black';
  size?: 'large' | 'medium' | 'small';
  text?: 'signin_with' | 'signup_with' | 'continue_with';
  shape?: 'rectangular' | 'pill';
  logo_alignment?: 'left' | 'center';
  /** Pixels, at most 400. */
  width?: number;
}

export interface GoogleIdentityApi {
  initialize(config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    ux_mode?: 'popup' | 'redirect';
    cancel_on_tap_outside?: boolean;
  }): void;
  renderButton(parent: HTMLElement, options: GoogleButtonOptions): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdentityApi } };
  }
}

let loading: Promise<GoogleIdentityApi> | null = null;

export function loadGoogleIdentity(): Promise<GoogleIdentityApi> {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id);
  loading ??= new Promise<GoogleIdentityApi>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SCRIPT_URL;
    script.async = true;
    script.defer = true;
    script.onload = () =>
      window.google?.accounts?.id
        ? resolve(window.google.accounts.id)
        : reject(new Error('Google sign-in did not load'));
    script.onerror = () => {
      loading = null; // allow a retry on the next page visit
      script.remove();
      reject(new Error('Google sign-in could not be loaded'));
    };
    document.head.appendChild(script);
  });
  return loading;
}
