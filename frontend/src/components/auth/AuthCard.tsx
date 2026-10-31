import { ArrowLeft } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { PATHS } from '../../routes/paths';
import Logo from '../brand/Logo';

interface AuthCardProps {
  title: string;
  subtitle?: string;
  /** Shows a "Back" link above the card. */
  backTo?: string;
  backLabel?: string;
  /** Text under the form, e.g. "Don't have an account? Create Account". */
  footer?: ReactNode;
  children: ReactNode;
}

/**
 * Form container for auth pages. A card with the logo on phones/tablets; on large screens the
 * brand panel already shows the logo, so the form sits directly on the page, left-aligned.
 */
export default function AuthCard({
  title,
  subtitle,
  backTo,
  backLabel = 'Back',
  footer,
  children,
}: AuthCardProps) {
  return (
    <div>
      {backTo && (
        <Link
          to={backTo}
          className="mb-4 inline-flex items-center gap-1.5 rounded-md text-sm font-medium text-slate-600 hover:text-slate-900 lg:mb-8"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {backLabel}
        </Link>
      )}
      <div className="rounded-2xl border border-slate-200 bg-white px-5 py-8 shadow-sm sm:px-9 sm:py-10 lg:rounded-none lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none">
        <div className="flex justify-center lg:hidden">
          <Link to={PATHS.home} aria-label="SwiftDrop home">
            <Logo />
          </Link>
        </div>
        <h1 className="mt-5 text-center text-2xl font-bold tracking-tight text-slate-900 lg:mt-0 lg:text-left lg:text-3xl">
          {title}
        </h1>
        {subtitle && (
          <p className="mt-1.5 text-center text-sm text-slate-500 lg:mt-2 lg:text-left lg:text-base">
            {subtitle}
          </p>
        )}
        <div className="mt-8">{children}</div>
        {footer && <div className="mt-7 text-center text-sm text-slate-600">{footer}</div>}
      </div>
    </div>
  );
}
