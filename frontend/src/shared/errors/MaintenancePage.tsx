import { Mail, Wrench } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import Logo from '../../components/brand/Logo';
import StateView from '../../components/feedback/StateView';
import Button from '../../components/ui/Button';
import { useSettings } from '../../hooks/useSettings';
import { PATHS } from '../../routes/paths';

/** Shown to everyone but the admin while maintenance mode is on. */
export default function MaintenancePage() {
  const { settings, refresh } = useSettings();
  const [checking, setChecking] = useState(false);

  const checkAgain = async () => {
    setChecking(true);
    await refresh();
    setChecking(false);
  };

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-slate-50 px-4 py-12">
      <Logo />
      <div className="mt-8 w-full max-w-md rounded-2xl border border-slate-200 bg-white shadow-sm">
        <StateView
          icon={Wrench}
          tone="warning"
          title="We'll be back soon"
          message={`${settings.companyName} is down for scheduled maintenance. Please try again a little later.`}
          actions={
            <Button variant="outline" onClick={checkAgain} loading={checking}>
              Try Again
            </Button>
          }
        />
      </div>
      <a
        href={`mailto:${settings.contactEmail}`}
        className="mt-6 inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900"
      >
        <Mail className="size-4" aria-hidden="true" />
        {settings.contactEmail}
      </a>
      <Link to={PATHS.adminLogin} className="mt-3 text-xs text-slate-400 hover:text-slate-600">
        Admin sign-in
      </Link>
    </div>
  );
}
