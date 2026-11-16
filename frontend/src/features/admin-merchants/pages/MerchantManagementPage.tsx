import { useState } from 'react';
import { generatePath, Link } from 'react-router';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { PATHS } from '../../../routes/paths';
import { useApiData } from '../../shared-parcels/useApiData';
import { adminMerchantsApi } from '../api';
import ReviewMerchantDialog, { type ReviewDecision } from '../components/ReviewMerchantDialog';
import { MERCHANT_STATUS_CLASSES, MERCHANT_STATUS_LABELS } from '../merchantStatus';
import { MERCHANT_STATUSES, type Merchant, type MerchantStatus } from '../types';

const STATUS_LABELS = MERCHANT_STATUS_LABELS;
const STATUS_CLASSES = MERCHANT_STATUS_CLASSES;

const TAB_LABELS: Record<MerchantStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

const EMPTY_MESSAGES: Record<MerchantStatus, string> = {
  PENDING: 'No registrations are waiting for review.',
  APPROVED: 'No merchants have been approved yet.',
  REJECTED: 'No registrations have been rejected.',
  SUSPENDED: 'No merchants are suspended.',
};

type MerchantsByStatus = Record<MerchantStatus, Merchant[]>;

/** Every status at once: the tabs and cards need all the counts, and merchant lists are short. */
async function loadAll(): Promise<MerchantsByStatus> {
  const lists = await Promise.all(
    MERCHANT_STATUSES.map((status) => adminMerchantsApi.list(status)),
  );
  return Object.fromEntries(
    MERCHANT_STATUSES.map((status, i) => [status, lists[i]]),
  ) as MerchantsByStatus;
}

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

export default function MerchantManagementPage() {
  const [tab, setTab] = useState<MerchantStatus>('PENDING');
  // Bumped after a review so the lists and counts load again.
  const [reloads, setReloads] = useState(0);
  const data = useApiData(`merchants#${reloads}`, loadAll);

  const [review, setReview] = useState<{ merchant: Merchant; decision: ReviewDecision } | null>(
    null,
  );
  const [saving, setSaving] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const merchants = data.status === 'success' ? data.data : null;
  const rows = merchants?.[tab] ?? [];

  function startReview(merchant: Merchant, decision: ReviewDecision) {
    setReviewError(null);
    setReview({ merchant, decision });
  }

  async function confirmReview(reason?: string) {
    if (!review) return;
    const { merchant, decision } = review;
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
      setReview(null);
      setReloads((n) => n + 1);
    } catch (err) {
      // Keep the dialog open so the admin can read the problem (e.g. already reviewed) and retry.
      setReviewError(
        err instanceof Error ? err.message : 'Something went wrong. Please try again.',
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          Merchant Management
        </h1>
        <p className="mt-1 text-slate-500">
          Review business registrations and approve the merchants who can start shipping.
        </p>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {MERCHANT_STATUSES.map((status) => (
          <div key={status} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-sm text-slate-500">{STATUS_LABELS[status]}</p>
            <p className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
              {merchants ? merchants[status].length.toLocaleString() : '–'}
            </p>
          </div>
        ))}
      </div>

      {notice && (
        <Alert tone="success" className="mt-6">
          {notice}
        </Alert>
      )}

      <div className="mt-8 rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-200 p-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Merchants</h2>
            <p className="text-sm text-slate-500">
              Oldest registrations first, so the queue is reviewed in order.
            </p>
          </div>
          <div role="tablist" aria-label="Approval status" className="flex flex-wrap gap-2">
            {MERCHANT_STATUSES.map((status) => (
              <button
                key={status}
                type="button"
                role="tab"
                aria-selected={tab === status}
                onClick={() => setTab(status)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                  tab === status
                    ? 'bg-slate-900 text-white'
                    : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                }`}
              >
                {TAB_LABELS[status]}
                {merchants && <span className="ml-1.5 opacity-70">{merchants[status].length}</span>}
              </button>
            ))}
          </div>
        </div>

        {data.status === 'error' ? (
          <ErrorState message={data.message} onAction={data.retry} actionLabel="Try again" />
        ) : !merchants ? (
          <LoadingState title="Loading merchants..." />
        ) : rows.length === 0 ? (
          <EmptyState
            title={`No ${TAB_LABELS[tab].toLowerCase()} merchants`}
            message={EMPTY_MESSAGES[tab]}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-5 py-3 font-medium">Business</th>
                  <th className="px-5 py-3 font-medium">Contact</th>
                  <th className="px-5 py-3 font-medium">Address</th>
                  <th className="px-5 py-3 font-medium">Submitted</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                  <th className="px-5 py-3 text-right font-medium">
                    {tab === 'PENDING' ? 'Review' : 'Reviewed'}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {rows.map((merchant) => (
                  <tr key={merchant.merchantId} className="align-top hover:bg-slate-50">
                    <td className="px-5 py-4">
                      <Link
                        to={generatePath(PATHS.adminMerchantDetail, {
                          merchantId: merchant.merchantId,
                        })}
                        className="font-medium text-slate-900 hover:text-sky-700 hover:underline"
                      >
                        {merchant.businessName}
                      </Link>
                      <div className="text-xs text-slate-500">
                        Reg. no. {merchant.registrationNo ?? '–'}
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <div>{merchant.contactEmail}</div>
                      <div className="text-xs text-slate-500">{merchant.contactPhone}</div>
                    </td>
                    <td className="max-w-xs px-5 py-4">{merchant.businessAddress}</td>
                    <td className="px-5 py-4 whitespace-nowrap">
                      {formatDate(merchant.submittedAt)}
                    </td>
                    <td className="px-5 py-4">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium whitespace-nowrap ring-1 ${STATUS_CLASSES[merchant.approvalStatus]}`}
                      >
                        {STATUS_LABELS[merchant.approvalStatus]}
                      </span>
                      {merchant.rejectionReason && (
                        <p className="mt-2 max-w-xs text-xs text-slate-500">
                          Reason: {merchant.rejectionReason}
                        </p>
                      )}
                    </td>
                    <td className="px-5 py-4">
                      {merchant.approvalStatus === 'PENDING' ? (
                        <div className="flex justify-end gap-2">
                          <Button size="sm" onClick={() => startReview(merchant, 'approve')}>
                            Approve
                          </Button>
                          <Button
                            size="sm"
                            variant="danger"
                            onClick={() => startReview(merchant, 'reject')}
                          >
                            Reject
                          </Button>
                        </div>
                      ) : (
                        <div className="text-right whitespace-nowrap text-slate-500">
                          {merchant.reviewedAt ? formatDate(merchant.reviewedAt) : '–'}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <ReviewMerchantDialog
        // A new key per review starts the reason box empty.
        key={review ? `${review.merchant.merchantId}-${review.decision}` : 'closed'}
        merchant={review?.merchant ?? null}
        decision={review?.decision ?? 'approve'}
        saving={saving}
        error={reviewError}
        onConfirm={(reason) => void confirmReview(reason)}
        onCancel={() => setReview(null)}
      />
    </section>
  );
}
