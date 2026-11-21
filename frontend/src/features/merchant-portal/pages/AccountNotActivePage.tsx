import { Clock, LogOut, Mail, ShieldOff } from 'lucide-react';
import { useState } from 'react';
import type { AccountBlock } from '../../../api/client';
import Logo from '../../../components/brand/Logo';
import StateView from '../../../components/feedback/StateView';
import Button from '../../../components/ui/Button';
import { useAuth } from '../../../hooks/useAuth';
import { useSettings } from '../../../hooks/useSettings';

const COPY: Record<AccountBlock['code'], { title: string; fallback: string }> = {
  ACCOUNT_DISABLED: {
    title: 'Your merchant account is not active',
    fallback: 'This merchant account is not active. Please contact support.',
  },
  ACCOUNT_PENDING_APPROVAL: {
    title: 'Your merchant account is awaiting approval',
    fallback:
      "Your merchant account is awaiting approval. We'll email you as soon as it's approved.",
  },
};

interface AccountNotActivePageProps {
  block: AccountBlock;
  /** Hides this page and loads the portal again (e.g. after support reactivated the account). */
  onCheckAgain: () => void;
}

/**
 * Shown instead of the merchant portal when the API refuses the signed-in merchant, for example
 * after an admin suspended it. Replaces the error cards each screen would otherwise show.
 */
export default function AccountNotActivePage({ block, onCheckAgain }: AccountNotActivePageProps) {
  const { logout } = useAuth();
  const { settings } = useSettings();
  const [signingOut, setSigningOut] = useState(false);
  const copy = COPY[block.code];

  async function signOut() {
    setSigningOut(true);
    await logout();
  }

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-slate-50 px-4 py-12">
      <Logo />
      <div className="mt-8 w-full max-w-md rounded-2xl border border-slate-200 bg-white shadow-sm">
        <StateView
          icon={block.code === 'ACCOUNT_PENDING_APPROVAL' ? Clock : ShieldOff}
          tone={block.code === 'ACCOUNT_PENDING_APPROVAL' ? 'warning' : 'danger'}
          title={copy.title}
          message={block.message || copy.fallback}
          actions={
            <>
              <Button loading={signingOut} onClick={() => void signOut()}>
                {!signingOut && <LogOut className="size-4" aria-hidden="true" />}
                Sign Out
              </Button>
              <Button variant="outline" disabled={signingOut} onClick={onCheckAgain}>
                Check Again
              </Button>
            </>
          }
        />
      </div>
      <a
        href={`mailto:${settings.contactEmail}`}
        className="mt-6 inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900"
      >
        <Mail className="size-4" aria-hidden="true" />
        Contact support: {settings.contactEmail}
      </a>
    </div>
  );
}
