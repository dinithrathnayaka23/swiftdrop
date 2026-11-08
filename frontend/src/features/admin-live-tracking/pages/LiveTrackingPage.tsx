import { useState } from 'react';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { useAuth } from '../../../hooks/useAuth';
import type { ParcelStatusCode } from '../../../types/tracking';
import Pagination from '../../shared-parcels/components/Pagination';
import { useApiData } from '../../shared-parcels/useApiData';
import type { LiveStatusUpdate } from '../../shared-live/types';
import { useLiveUpdates } from '../../shared-live/useLiveUpdates';
import { ADMIN_LIVE_STREAM_PATH, adminLiveApi } from '../api';
import DeliveriesTable from '../components/DeliveriesTable';
import DeliveryFilters from '../components/DeliveryFilters';
import LiveIndicator from '../../shared-live/LiveIndicator';
import StatusCountCards from '../components/StatusCountCards';
import { NO_FILTERS, PAGE_SIZES, toQuery, type AppliedFilters } from '../filters';
import type { LiveDeliveryRow, LiveStatusCounts } from '../types';

/** How long a row stays tinted after a live status change. */
const HIGHLIGHT_MS = 3_000;

/** The row with a live update applied, if the update is newer than what was loaded. */
function withUpdate(row: LiveDeliveryRow, update: LiveStatusUpdate | undefined): LiveDeliveryRow {
  if (!update || update.statusVersion <= row.statusVersion) return row;
  return {
    ...row,
    status: update.status,
    statusVersion: update.statusVersion,
    lastUpdatedAt: update.changedAt,
  };
}

/** Loaded counts plus the status changes that arrived live since. */
function withChanges(
  counts: LiveStatusCounts,
  changes: Partial<Record<ParcelStatusCode, number>>,
): LiveStatusCounts {
  const result = { ...counts };
  for (const [status, change] of Object.entries(changes) as [ParcelStatusCode, number][]) {
    result[status] = Math.max(0, (result[status] ?? 0) + change);
  }
  return result;
}

/** Admin: Deliveries Tracking. Every delivery with its status, updated live. */
export default function LiveTrackingPage() {
  const { token } = useAuth();
  const [filters, setFilters] = useState<AppliedFilters>(NO_FILTERS);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState<number>(PAGE_SIZES[0]);
  // Bumped after a reconnect: updates may have been missed, so reload from the API.
  const [reloads, setReloads] = useState(0);
  const [updates, setUpdates] = useState<Record<string, LiveStatusUpdate>>({});
  const [countChanges, setCountChanges] = useState<Partial<Record<ParcelStatusCode, number>>>({});
  const [highlighted, setHighlighted] = useState<ReadonlySet<string>>(new Set());

  const query = toQuery(filters, page, size);
  const list = useApiData(`${JSON.stringify(query)}#${reloads}`, () =>
    adminLiveApi.listDeliveries(query),
  );
  const counts = useApiData(`counts#${reloads}`, () => adminLiveApi.getCounts());

  const rows =
    list.status === 'success'
      ? list.data.content.map((row) => withUpdate(row, updates[row.trackingNumber]))
      : [];

  const connection = useLiveUpdates({
    path: ADMIN_LIVE_STREAM_PATH,
    token,
    knownVersions: Object.fromEntries(rows.map((row) => [row.trackingNumber, row.statusVersion])),
    onUpdate: (update) => {
      setUpdates((current) => ({ ...current, [update.trackingNumber]: update }));
      setCountChanges((current) => {
        const next = { ...current, [update.status]: (current[update.status] ?? 0) + 1 };
        if (update.previousStatus) {
          next[update.previousStatus] = (next[update.previousStatus] ?? 0) - 1;
        }
        return next;
      });
      setHighlighted((current) => new Set(current).add(update.trackingNumber));
      setTimeout(() => {
        setHighlighted((current) => {
          const next = new Set(current);
          next.delete(update.trackingNumber);
          return next;
        });
      }, HIGHLIGHT_MS);
    },
    onReconnect: () => {
      setCountChanges({});
      setReloads((n) => n + 1);
    },
  });

  const applyFilters = (next: AppliedFilters) => {
    setFilters(next);
    setPage(0);
  };

  const total = list.status === 'success' ? list.data.page.totalElements : 0;
  const totalPages = list.status === 'success' ? list.data.page.totalPages : 0;
  const first = total === 0 ? 0 : page * size + 1;
  const last = Math.min(total, (page + 1) * size);

  return (
    <div className="mx-auto max-w-7xl space-y-5 px-4 py-6 sm:px-6 lg:py-8">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Deliveries Tracking
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Monitor all deliveries in real time and view their status.
          </p>
        </div>
        <LiveIndicator state={connection} />
      </header>

      <StatusCountCards
        counts={counts.status === 'success' ? withChanges(counts.data, countChanges) : null}
      />

      <DeliveryFilters applied={filters} onApply={applyFilters} />

      <section
        aria-label="Deliveries"
        className="rounded-2xl border border-slate-200 bg-white shadow-xs"
      >
        {list.status === 'loading' && <LoadingState title="Loading deliveries..." />}
        {list.status === 'error' && <ErrorState message={list.message} onAction={list.retry} />}
        {list.status === 'success' && rows.length === 0 && (
          <EmptyState
            title="No deliveries found"
            message={
              filters === NO_FILTERS
                ? 'Deliveries appear here as soon as they are booked.'
                : 'No delivery matches these filters. Try another search or status.'
            }
          />
        )}
        {list.status === 'success' && rows.length > 0 && (
          <>
            <DeliveriesTable rows={rows} highlighted={highlighted} />
            <div className="flex flex-wrap items-center justify-end gap-4 border-t border-slate-100 px-4 py-3 text-sm text-slate-600">
              <label className="flex items-center gap-2">
                Rows per page
                <select
                  value={size}
                  onChange={(e) => {
                    setSize(Number(e.target.value));
                    setPage(0);
                  }}
                  className={inputClasses(false, 'h-9 w-20')}
                >
                  {PAGE_SIZES.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
              <span aria-live="polite">
                {first} – {last} of {total}
              </span>
              {totalPages > 1 && (
                <Pagination page={page} totalPages={totalPages} onChange={setPage} />
              )}
            </div>
          </>
        )}
      </section>
    </div>
  );
}
