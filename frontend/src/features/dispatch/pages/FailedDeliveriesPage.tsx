import { CircleX, PackageCheck } from 'lucide-react';
import { useState } from 'react';
import { generatePath, Link } from 'react-router';
import { dispatchApi } from '../../../api/dispatch';
import StateView from '../../../components/feedback/StateView';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { useSettings } from '../../../hooks/useSettings';
import { PATHS } from '../../../routes/paths';
import { MAX_FAILED_PAGE_SIZE, type FailedScope } from '../../../types/dispatch';
import { cn } from '../../../utils/cn';
import Pagination from '../../shared-parcels/components/Pagination';
import { useApiData } from '../../shared-parcels/useApiData';
import CloseDeliveryDialog from '../components/CloseDeliveryDialog';
import RetriedNotice from '../components/RetriedNotice';
import RetryDialog from '../components/RetryDialog';
import type { FailedParcelRef } from '../failedActions';

const TABS: { id: FailedScope; label: string }[] = [
  { id: 'ATTENTION', label: 'Needs decision' },
  { id: 'CLOSED', label: 'Closed' },
];

type Notice = { kind: 'retried'; parcel: FailedParcelRef } | { kind: 'closed'; text: string };

/** Admin: failed delivery attempts waiting for a decision (retry or close), and the closed ones. */
export default function FailedDeliveriesPage() {
  const { settings } = useSettings();
  const { formatShortDateTime } = useDateFormat();
  const size = Math.min(Math.max(settings.itemsPerPage, 1), MAX_FAILED_PAGE_SIZE);
  const [scope, setScope] = useState<FailedScope>('ATTENTION');
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const [action, setAction] = useState<{
    kind: 'retry' | 'close';
    parcel: FailedParcelRef;
  } | null>(null);
  const [notice, setNotice] = useState<Notice>();

  const list = useApiData(`${scope}|${page}|${size}#${version}`, () =>
    dispatchApi.listFailed({ scope, page, size }),
  );
  // Without it Retry and Close still work: nothing is emphasised or pre-filled.
  const defaults = useApiData('failed-default-action', () => dispatchApi.defaultFailedAction());
  const defaultAction = defaults.status === 'success' ? defaults.data : undefined;
  const retryFirst = defaultAction === 'RETRY_DELIVERY';
  const rows = list.status === 'success' ? list.data.content : [];
  const attention = scope === 'ATTENTION';

  function decided(next: Notice) {
    setAction(null);
    setNotice(next);
    setVersion((n) => n + 1);
  }

  return (
    <div className="mx-auto max-w-[1600px] space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Failed Deliveries</h1>
        <p className="mt-1 text-slate-500">
          Retry a failed delivery attempt with a new courier, or close it for good.
        </p>
      </header>

      {notice?.kind === 'retried' && <RetriedNotice parcel={notice.parcel} />}
      {notice?.kind === 'closed' && <Alert tone="success">{notice.text}</Alert>}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-xs">
        <div
          role="tablist"
          aria-label="Failed deliveries"
          className="flex border-b border-slate-200 px-2"
        >
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={scope === tab.id}
              onClick={() => {
                setScope(tab.id);
                setPage(0);
              }}
              className={cn(
                'border-b-2 px-3 py-4 text-sm font-medium whitespace-nowrap',
                scope === tab.id
                  ? 'border-brand-700 text-brand-700'
                  : 'border-transparent text-slate-600 hover:text-slate-900',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {list.status === 'loading' && (
          <div className="space-y-2 p-4" aria-busy="true" aria-label="Loading failed deliveries">
            {Array.from({ length: 5 }, (_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}

        {list.status === 'error' && (
          <StateView
            icon={CircleX}
            tone="danger"
            title="Could not load failed deliveries"
            message={list.message}
            actions={
              <Button variant="outline" size="sm" onClick={list.retry}>
                Try again
              </Button>
            }
          />
        )}

        {list.status === 'success' && rows.length === 0 && (
          <StateView
            icon={PackageCheck}
            title={attention ? 'Nothing Needs a Decision' : 'No Closed Deliveries'}
            message={
              attention
                ? 'No failed delivery attempt is waiting for a retry or close.'
                : 'No failed delivery has been closed yet.'
            }
          />
        )}

        {list.status === 'success' && rows.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] text-left text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs text-slate-600">
                  <tr>
                    {[
                      'Tracking No.',
                      'Courier',
                      attention ? 'Failure Reason' : 'Reason',
                      attention ? 'Failed At' : 'Closed At',
                      'Attempts',
                      'Destination',
                      ...(attention ? ['Actions'] : []),
                    ].map((label) => (
                      <th key={label} scope="col" className="px-4 py-3 font-medium">
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((row) => {
                    const parcel = { parcelId: row.parcelId, trackingNumber: row.trackingNumber };
                    return (
                      <tr key={row.parcelId} className="hover:bg-slate-50/60">
                        <td className="px-4 py-3">
                          <Link
                            to={generatePath(PATHS.adminParcelDetail, { parcelId: row.parcelId })}
                            className="font-semibold text-slate-900 hover:text-brand-700 hover:underline"
                          >
                            #{row.trackingNumber}
                          </Link>
                        </td>
                        <td className="px-4 py-3 text-slate-700">{row.courierName ?? '—'}</td>
                        <td className="max-w-[260px] px-4 py-3 text-red-700">
                          {row.failureReason ?? '—'}
                        </td>
                        <td className="px-4 py-3 text-slate-700">
                          {row.failedAt ? formatShortDateTime(row.failedAt) : '—'}
                        </td>
                        <td className="px-4 py-3 text-slate-700">{row.attemptCount}</td>
                        <td className="px-4 py-3 text-slate-700">{row.destinationCity ?? '—'}</td>
                        {attention && (
                          <td className="px-4 py-3">
                            <div className="flex gap-2">
                              <Button
                                size="sm"
                                variant={retryFirst ? 'primary' : 'outline'}
                                disabled={action !== null}
                                onClick={() => setAction({ kind: 'retry', parcel })}
                                aria-label={`Retry #${row.trackingNumber}`}
                              >
                                Retry
                              </Button>
                              <Button
                                size="sm"
                                variant={defaultAction && !retryFirst ? 'danger' : 'outline'}
                                disabled={action !== null}
                                onClick={() => setAction({ kind: 'close', parcel })}
                                aria-label={`Close #${row.trackingNumber}`}
                              >
                                Close
                              </Button>
                            </div>
                          </td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 p-4">
              <p className="text-sm text-slate-500">
                Showing {list.data.page.number * list.data.page.size + 1}–
                {list.data.page.number * list.data.page.size + rows.length} of{' '}
                {list.data.page.totalElements} parcels
              </p>
              <Pagination
                page={list.data.page.number}
                totalPages={list.data.page.totalPages}
                onChange={setPage}
              />
            </div>
          </>
        )}
      </section>

      {action?.kind === 'retry' && (
        <RetryDialog
          parcel={action.parcel}
          onClose={() => setAction(null)}
          onRetried={(parcel) => decided({ kind: 'retried', parcel })}
        />
      )}
      {action?.kind === 'close' && (
        <CloseDeliveryDialog
          parcel={action.parcel}
          defaultAction={defaultAction}
          onClose={() => setAction(null)}
          onClosed={(parcel) =>
            decided({ kind: 'closed', text: `#${parcel.trackingNumber} was closed as failed.` })
          }
        />
      )}
    </div>
  );
}
