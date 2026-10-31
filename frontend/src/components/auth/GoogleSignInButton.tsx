import { useEffect, useRef, useState } from 'react';
import { authApi } from '../../api/auth';
import type { GoogleConfig } from '../../types/auth';
import { loadGoogleIdentity, type GoogleIdentityApi } from '../../utils/googleIdentity';

interface GoogleSignInButtonProps {
  /** Called with the Google ID token after the user picks their Google account. */
  onCredential: (credential: string) => void;
  text?: 'signin_with' | 'signup_with' | 'continue_with';
  /** Dims the button while the parent exchanges the credential with the backend. */
  busy?: boolean;
}

// The config can't change while the app is open: fetch it once.
let configRequest: Promise<GoogleConfig> | null = null;

function loadGoogleConfig(): Promise<GoogleConfig> {
  configRequest ??= authApi.googleConfig().catch(() => {
    configRequest = null;
    return { enabled: false, clientId: null };
  });
  return configRequest;
}

const MAX_BUTTON_WIDTH = 400; // Google's limit

// Google expects initialize() once per page load; the mounted button swaps in its own handler.
let initializedClientId: string | null = null;
let activeHandler: ((credential: string) => void) | null = null;

function initializeOnce(api: GoogleIdentityApi, clientId: string) {
  if (initializedClientId === clientId) return;
  api.initialize({
    client_id: clientId,
    callback: (response) => activeHandler?.(response.credential),
    ux_mode: 'popup',
  });
  initializedClientId = clientId;
}

/**
 * Google's official "Sign in with Google" button, with an "or" divider above it. Renders nothing
 * when the backend has no GOOGLE_CLIENT_ID or Google's script can't load (e.g. offline), so the
 * email form always works on its own.
 */
export default function GoogleSignInButton({
  onCredential,
  text = 'continue_with',
  busy = false,
}: GoogleSignInButtonProps) {
  const container = useRef<HTMLDivElement>(null);
  const [google, setGoogle] = useState<{ api: GoogleIdentityApi; clientId: string } | null>(null);
  // Google's callback is registered once per page load; route it to this button's latest handler.
  useEffect(() => {
    activeHandler = onCredential;
    return () => {
      if (activeHandler === onCredential) activeHandler = null;
    };
  }, [onCredential]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const config = await loadGoogleConfig();
      if (!config.enabled || !config.clientId) return;
      const api = await loadGoogleIdentity();
      if (!cancelled) setGoogle({ api, clientId: config.clientId });
    })().catch(() => {
      // Script blocked or offline: leave the button out.
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!google || !container.current) return;
    initializeOnce(google.api, google.clientId);
    google.api.renderButton(container.current, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      text,
      shape: 'rectangular',
      logo_alignment: 'center',
      width: Math.min(container.current.offsetWidth || MAX_BUTTON_WIDTH, MAX_BUTTON_WIDTH),
    });
  }, [google, text]);

  if (!google) return null;

  return (
    <div>
      <div className="my-6 flex items-center gap-3 text-xs text-slate-400">
        <span className="h-px flex-1 bg-slate-200" />
        or
        <span className="h-px flex-1 bg-slate-200" />
      </div>
      <div
        ref={container}
        aria-busy={busy}
        className={`mx-auto flex min-h-11 w-full max-w-100 justify-center transition-opacity ${busy ? 'pointer-events-none opacity-50' : ''}`}
      />
    </div>
  );
}
