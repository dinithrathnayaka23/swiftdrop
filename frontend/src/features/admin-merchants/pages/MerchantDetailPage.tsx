import {
  ArrowLeft,
  Building2,
  CircleCheck,
  CircleX,
  Clock,
  Mail,
  MapPin,
  Phone,
  Store,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import LoadingState from '../../../components/feedback/LoadingState';
import StateView from '../../../components/feedback/StateView';
import { ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { PATHS } from '../../../routes/paths';
import { useApiData } from '../../shared-parcels/useApiData';
import { adminMerchantsApi } from '../api';
import ReviewMerchantDialog, { type ReviewDecision } from '../components/ReviewMerchantDialog';
import { MERCHANT_STATUS_CLASSES, MERCHANT_STATUS_LABELS } from '../merchantStatus';
import type { Merchant } from '../types';

function Detail({
  icon: Icon,
  label,
  children,
}: {
  icon: typeof Mail;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex gap-3">
      <Icon className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
      <div>
        <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</dt>
        <dd className="mt-0.5 text-sm text-slate-900">{children}</dd>
      </div>
    </div>
  );
}

/** The registration's history: submitted, then the admin's decision (if any). */
function History({ merchant }: { merchant: Merchant }) {
  const { formatDateTime } = useDateFormat();
  const decided = merchant.reviewedAt !== null;
  const outcome =
    merchant.approvalStatus === 'REJECTED'
      ? { icon: CircleX, tone: 'bg-rose-50 text-rose-600', title: 'Registration rejected' }
      : merchant.approvalStatus === 'SUSPENDED'
        ? { icon: CircleX, tone: 'bg-slate-100 text-slate-600', title: 'Approved, now suspended' }
        : {
            icon: CircleCheck,
            tone: 'bg-emerald-50 text-emerald-600',
            title: 'Registration approved',
          };
  const OutcomeIcon = outcome.icon;

  return (
    <ol className="space-y-5">
      <li className="flex gap-3">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-sky-50 text-sky-600">
          <Store className="size-4" aria-hidden="true" />
        </span>
        <div>
          <p className="text-sm font-medium text-slate-900">Registration submitted</p>
          <p className="text-xs text-slate-500">{formatDateTime(merchant.submittedAt)}</p>
        </div>
      </li>
      {decided ? (
        <li className="flex gap-3">
          <span
            className={`flex size-8 shrink-0 items-center justify-center rounded-full ${outcome.tone}`}
          >
            <OutcomeIcon className="size-4" aria-hidden="true" />
          </span>
          <div>
            <p className="text-sm font-medium text-slate-900">{outcome.title}</p>
            <p className="text-xs text-slate-500">{formatDateTime(merchant.reviewedAt!)}</p>
            {merchant.rejectionReason && (
              <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
                Reason: {merchant.rejectionReason}
              </p>
            )}
          </div>
        </li>
      ) : (
        <li className="flex gap-3">
          <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-amber-50 text-amber-600">
            <Clock className="size-4" aria-hidden="true" />
          </span>
          <div>
            <p className="text-sm font-medium text-slate-900">Waiting for review</p>
            <p className="text-xs text-slate-500">Approve or reject this registration.</p>
          </div>
        </li>
      )}
    </ol>
  );
}

/** Admin: one merchant's business details, contact, review history and (if pending) the review. */
export default function MerchantDetailPage() {
  const { merchantId = '' } = useParams();
  const [reloads, setReloads] = useState(0);
  const detail = useApiData(`${merchantId}#${reloads}`, () => adminMerchantsApi.get(merchantId));

  const [decision, setDecision] = useState<ReviewDecision | null>(null);
  const [saving, setSaving] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const back = (
    <Link
      to={PATHS.adminMerchants}
      className="inline-flex items-center gap-1.5 rounded-md text-sm font-medium text-slate-600 hover:text-slate-900"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Back to Merchants
    </Link>
  );

  if (detail.status === 'loading') {
    return (
      <section>
        {back}
        <LoadingState title="Loading merchant..." />
      </section>
    );
  }
  if (detail.status === 'error') {
    return (
      <section>
        {back}
        <ErrorState message={detail.message} onAction={detail.retry} actionLabel="Try again" />
      </section>
    );
  }
  const merchant = detail.data;
  if (!merchant) {
    return (
      <section>
        {back}
        <StateView
          icon={Building2}
          title="Merchant Not Found"
          message="This merchant doesn't exist. It may have been removed, or the link is wrong."
          actions={<ButtonLink to={PATHS.adminMerchants}>Back to Merchants</ButtonLink>}
        />
      </section>
    );
  }

  const pending = merchant.approvalStatus === 'PENDING';

  function startReview(next: ReviewDecision) {
    setReviewError(null);
    setDecision(next);
  }

  async function confirmReview(reason?: string) {
    if (!merchant || !decision) return;
    setSaving(true);
    setReviewError(null);
    try {
      if (decision === 'approve') {
        await adminMerchantsApi.approve(merchant.merchantId);
        setNotice(`${merchant.businessName} was approved and emailed.`);
      } else {
        await adminMerchantsApi.reject(merchant.merchantId, reason ?? '');
        setNotice(`${merchant.businessName} was rejected and emailed the reason.`);
      }
      setDecision(null);
      setReloads((n) => n + 1);
    } catch (err) {
      setReviewError(
        err instanceof Error ? err.message : 'Something went wrong. Please try again.',
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      {back}

      <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
              {merchant.businessName}
            </h1>
            <span
              className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ring-1 ${MERCHANT_STATUS_CLASSES[merchant.approvalStatus]}`}
            >
              {MERCHANT_STATUS_LABELS[merchant.approvalStatus]}
            </span>
          </div>
          <p className="mt-1 text-slate-500">Registration no. {merchant.registrationNo ?? '–'}</p>
        </div>
        {pending && (
          <div className="flex gap-2">
            <Button onClick={() => startReview('approve')}>Approve</Button>
            <Button variant="danger" onClick={() => startReview('reject')}>
              Reject
            </Button>
          </div>
        )}
      </div>

      {notice && (
        <Alert tone="success" className="mt-6">
          {notice}
        </Alert>
      )}

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-6">
          <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-900">Business</h2>
            <dl className="mt-4 grid gap-5 sm:grid-cols-2">
              <Detail icon={Building2} label="Business name">
                {merchant.businessName}
              </Detail>
              <Detail icon={Store} label="Registration no.">
                {merchant.registrationNo ?? '–'}
              </Detail>
              <div className="sm:col-span-2">
                <Detail icon={MapPin} label="Business address">
                  {merchant.businessAddress}
                </Detail>
              </div>
            </dl>
          </div>

          <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-900">Contact</h2>
            <dl className="mt-4 grid gap-5 sm:grid-cols-2">
              <Detail icon={Mail} label="Email">
                <a
                  href={`mailto:${merchant.contactEmail}`}
                  className="text-sky-700 hover:underline"
                >
                  {merchant.contactEmail}
                </a>
              </Detail>
              <Detail icon={Phone} label="Phone">
                {merchant.contactPhone}
              </Detail>
            </dl>
          </div>
        </div>

        <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">History</h2>
          <div className="mt-4">
            <History merchant={merchant} />
          </div>
        </div>
      </div>

      <ReviewMerchantDialog
        key={decision ?? 'closed'}
        merchant={decision ? merchant : null}
        decision={decision ?? 'approve'}
        saving={saving}
        error={reviewError}
        onConfirm={(reason) => void confirmReview(reason)}
        onCancel={() => setDecision(null)}
      />
    </section>
  );
}
