import { ArrowLeft, Bike } from 'lucide-react';
import { Link } from 'react-router';
import StaffSignInForm from '../../components/auth/StaffSignInForm';
import Logo from '../../components/brand/Logo';
import { PATHS } from '../../routes/paths';

/**
 * Courier app sign-in. Built for phones first: one column, full-height, large touch targets.
 * Couriers are added by an admin, so there is no sign-up link.
 */
export default function CourierLoginPage() {
  return (
    <div className="flex min-h-dvh flex-col bg-slate-50">
      <header className="relative bg-gradient-to-br from-brand-700 via-brand-600 to-brand-800 px-4 pt-[calc(env(safe-area-inset-top)+3.75rem)] pb-16 text-white">
        <Link
          to={PATHS.home}
          className="absolute top-[calc(env(safe-area-inset-top)+0.5rem)] left-2 inline-flex h-11 items-center gap-1.5 rounded-full px-3 text-sm font-medium text-white/90 hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:outline-white"
        >
          <ArrowLeft className="size-5" aria-hidden="true" />
          Back
        </Link>
        <div className="mx-auto flex max-w-md flex-col items-center text-center">
          <Link
            to={PATHS.home}
            aria-label="SwiftDrop home"
            className="inline-flex rounded-xl bg-white px-3.5 py-2 shadow-sm"
          >
            <Logo />
          </Link>
          <span className="mt-6 inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-semibold tracking-wide uppercase">
            <Bike className="size-4" aria-hidden="true" />
            Courier app
          </span>
          <h1 className="mt-3 text-2xl font-bold tracking-tight">Ready for today&apos;s run?</h1>
          <p className="mt-1.5 text-sm text-brand-50">
            Sign in to see your pickups and deliveries.
          </p>
        </div>
      </header>

      <main className="relative z-10 -mt-10 flex-1 px-4 pb-[max(env(safe-area-inset-bottom),1.5rem)]">
        <div className="mx-auto w-full max-w-md rounded-2xl border border-slate-200 bg-white px-5 py-7 shadow-sm sm:px-8">
          <StaffSignInForm portal="COURIER" emailPlaceholder="rider@swiftdrop.lk" />
        </div>
        <p className="mx-auto mt-6 max-w-md text-center text-sm text-slate-500">
          Courier accounts are created by your SwiftDrop admin. Can&apos;t sign in? Ask your
          dispatcher.
        </p>
      </main>
    </div>
  );
}
