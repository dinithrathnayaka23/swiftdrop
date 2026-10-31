import { Hourglass } from 'lucide-react';
import { useLocation } from 'react-router';
import ButtonLink from '../../components/ui/ButtonLink';
import { PATHS } from '../../routes/paths';
import { formatDateTime } from '../../utils/date';
import type { MerchantSubmittedState } from './MerchantSignupPage';

export default function MerchantSubmittedPage() {
  const submission = useLocation().state as MerchantSubmittedState | null;

  const rows: [string, string][] = submission
    ? [
        ['Business Name', submission.businessName],
        ['Registration No.', submission.registrationNo],
        ['Submitted On', formatDateTime(submission.submittedAt)],
      ]
    : [];

  return (
    <div className="rounded-2xl border border-slate-200 bg-white px-5 py-10 text-center shadow-sm sm:px-9">
      <div className="mx-auto flex size-20 items-center justify-center rounded-full bg-amber-50 ring-8 ring-amber-50/50">
        <Hourglass className="size-9 text-amber-500" strokeWidth={1.8} aria-hidden="true" />
      </div>
      <h1 className="mt-6 text-2xl font-bold tracking-tight text-slate-900">
        Registration Submitted
      </h1>
      <p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-slate-500">
        Your merchant account has been submitted and is pending approval.
      </p>

      {rows.length > 0 && (
        <dl className="mt-7 space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-5 text-left text-sm">
          {rows.map(([label, value]) => (
            <div key={label} className="flex justify-between gap-4">
              <dt className="text-slate-500">{label}</dt>
              <dd className="text-right font-medium text-slate-900">{value}</dd>
            </div>
          ))}
        </dl>
      )}

      <p className="mx-auto mt-7 max-w-sm text-sm leading-6 text-slate-500">
        We&apos;ve emailed you a link to confirm your email address; please open it. We will review
        your details and notify you by email once your account is approved.
      </p>

      <ButtonLink to={PATHS.home} variant="outline" fullWidth className="mt-7">
        Back to Home
      </ButtonLink>
    </div>
  );
}
