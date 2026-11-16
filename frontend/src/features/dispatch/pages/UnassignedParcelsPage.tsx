import { CalendarDays, CircleX, PackageCheck, Search, Truck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { generatePath, Link, useSearchParams } from 'react-router';
import { parcelsApi } from '../../../api/parcels';
import StateView from '../../../components/feedback/StateView';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { useSettings } from '../../../hooks/useSettings';
import { PATHS } from '../../../routes/paths';
import { MAX_BULK_PARCELS } from '../../../types/dispatch';
import type { AdminParcelFilters } from '../../../types/parcel';
import { useDebouncedValue } from '../../shared-dispatch/useDebouncedValue';
import Pagination from '../../shared-parcels/components/Pagination';
import { isUnassigned } from '../../shared-parcels/components/statusVisuals';
import { useApiData } from '../../shared-parcels/useApiData';
import AssignToCourierDialog, { type ParcelRef } from '../components/AssignToCourierDialog';

const MAX_PAGE_SIZE = 100;

type DateRange = 'ALL' | 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS';
const DATE_RANGES: { id: DateRange; label: string; days?: number }[] = [
  { id: 'ALL', label: 'All time' },
  { id: 'TODAY', label: 'Today', days: 0 },
  { id: 'LAST_7_DAYS', label: 'Last 7 days', days: 7 },
  { id: 'LAST_30_DAYS', label: 'Last 30 days', days: 30 },
];

/** yyyy-mm-dd, {@code daysAgo} days back, in Sri Lanka time. */
function sriLankaDay(daysAgo: number): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(
    Date.now() - daysAgo * 86_400_000,
  );
}

type Notice = { tone: 'success' | 'info' | 'error'; text: string };

/** Admin: parcels waiting for a courier, assigned one at a time or in bulk. */
export default function UnassignedParcelsPage() {
  const { settings } = useSettings();
  const { formatShortDateTime } = useDateFormat();
  const size = Math.min(Math.max(settings.itemsPerPage, 1), MAX_PAGE_SIZE);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput.trim(), 300);
  const [dateRange, setDateRange] = useState<DateRange>('ALL');
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  // id -> tracking number, kept across pages and filters.
  const [selected, setSelected] = useState<Map<string, string>>(new Map());
  const [assigning, setAssigning] = useState<ParcelRef[] | null>(null);
  const [notice, setNotice] = useState<Notice>();
  const [params, setParams] = useSearchParams();
  const linkedId = params.get('parcelId');

  const [appliedSearch, setAppliedSearch] = useState(search);
  if (appliedSearch !== search) {
    setAppliedSearch(search);
    setPage(0);
  }

  const range = DATE_RANGES.find((item) => item.id === dateRange);
  const filters: AdminParcelFilters = {
    tab: 'UNASSIGNED',
    search: search || undefined,
    createdFrom: range?.days !== undefined ? sriLankaDay(range.days) : undefined,
    page,
    size,
  };
  const list = useApiData(`${JSON.stringify(filters)}#${version}`, () =>
    parcelsApi.listAdminParcels(filters),
  );
  const rows = list.status === 'success' ? list.data.content : [];

  // ?parcelId=... (the Assign button of the parcel details): open the dialog for that parcel, but
  // only if it is still waiting for a courier.
  useEffect(() => {
    if (!linkedId) return;
    let active = true;
    const notWaiting = () =>
      setNotice({
        tone: 'info',
        text: 'That parcel is not waiting for a courier, so there is nothing to assign.',
      });
    parcelsApi
      .getAdminParcel(linkedId)
      .then(
        (parcel) => {
          if (!active) return;
          if (parcel && isUnassigned(parcel.status, parcel.dispatchState)) {
            setAssigning([{ id: parcel.id, trackingNumber: parcel.trackingNumber }]);
          } else {
            notWaiting();
          }
        },
        () => active && notWaiting(),
      )
      .finally(() => {
        if (!active) return;
        setParams(
          (current) => {
            current.delete('parcelId');
            return current;
          },
          { replace: true },
        );
      });
    return () => {
      active = false;
    };
  }, [linkedId, setParams]);

  const tooMany = selected.size > MAX_BULK_PARCELS;
  const allOnPage = rows.length > 0 && rows.every((row) => selected.has(row.id));
  const someOnPage = rows.some((row) => selected.has(row.id));

  function toggle(id: string, trackingNumber: string) {
    setSelected((current) => {
      const next = new Map(current);
      if (next.has(id)) next.delete(id);
      else next.set(id, trackingNumber);
      return next;
    });
  }

  function togglePage() {
    setSelected((current) => {
      const next = new Map(current);
      for (const row of rows) {
        if (allOnPage) next.delete(row.id);
        else next.set(row.id, row.trackingNumber);
      }
      return next;
    });
  }

  return (
    <div className="mx-auto max-w-[1600px] space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Unassigned Parcels</h1>
          <p className="mt-1 text-slate-500">
            {list.status === 'success'
              ? `${list.data.page.totalElements} ${list.data.page.totalElements === 1 ? 'parcel is' : 'parcels are'} waiting for a courier.`
              : 'Parcels waiting for a courier.'}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <Button
            className="bg-brand-800 hover:bg-brand-900"
            disabled={selected.size === 0 || tooMany}
            aria-describedby={tooMany ? 'unassigned-limit' : undefined}
            onClick={() =>
              setAssigning([...selected].map(([id, trackingNumber]) => ({ id, trackingNumber })))
            }
          >
            <Truck className="size-4" aria-hidden="true" />
            Assign selected ({selected.size})
          </Button>
          {tooMany && (
            <p id="unassigned-limit" className="max-w-xs text-right text-sm text-red-600">
              At most {MAX_BULK_PARCELS} parcels can be assigned at once. Deselect{' '}
              {selected.size - MAX_BULK_PARCELS} to continue.
            </p>
          )}
        </div>
      </header>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-xs">
        <div className="grid gap-3 p-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] sm:items-end">
          <div className="relative">
            <label
              htmlFor="unassigned-search"
              className="mb-1 block text-xs font-medium text-slate-600"
            >
              Search
            </label>
            <Search
              className="pointer-events-none absolute bottom-3 left-3.5 size-4 text-slate-400"
              aria-hidden="true"
            />
            <input
              id="unassigned-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Tracking number, recipient or merchant..."
              className={inputClasses(false, 'h-10 pl-10')}
            />
          </div>
          <div>
            <label
              htmlFor="unassigned-date"
              className="mb-1 flex items-center gap-1 text-xs font-medium text-slate-600"
            >
              <CalendarDays className="size-3.5" aria-hidden="true" />
              Date Range
            </label>
            <select
              id="unassigned-date"
              value={dateRange}
              onChange={(event) => {
                setDateRange(event.target.value as DateRange);
                setPage(0);
              }}
              className={inputClasses(false, 'h-10')}
            >
              {DATE_RANGES.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {selected.size > 0 && (
          <div className="flex flex-wrap items-center gap-3 border-t border-slate-100 px-4 py-2 text-sm">
            <span className="text-slate-700" aria-live="polite">
              {selected.size} selected
            </span>
            <Button variant="ghost" size="sm" onClick={() => setSelected(new Map())}>
              Clear selection
            </Button>
          </div>
        )}

        {list.status === 'loading' && (
          <div className="space-y-2 px-4 pb-4" aria-busy="true" aria-label="Loading parcels">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}

        {list.status === 'error' && (
          <StateView
            icon={CircleX}
            tone="danger"
            title="Could not load parcels"
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
            title="No Parcels Waiting"
            message={
              search || dateRange !== 'ALL'
                ? 'No waiting parcels match these filters.'
                : 'Every parcel has a courier.'
            }
          />
        )}

        {list.status === 'success' && rows.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-xs text-slate-600">
                  <tr>
                    <th scope="col" className="w-10 py-3 pr-2 pl-4">
                      <input
                        type="checkbox"
                        checked={allOnPage}
                        ref={(box) => {
                          if (box) box.indeterminate = someOnPage && !allOnPage;
                        }}
                        onChange={togglePage}
                        aria-label="Select all parcels on this page"
                        className="accent-brand-700"
                      />
                    </th>
                    {[
                      'Tracking No.',
                      'Merchant',
                      'Recipient',
                      'Destination',
                      'Weight',
                      'Created At',
                      'Action',
                    ].map((label) => (
                      <th key={label} scope="col" className="px-3 py-3 font-medium">
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((row) => (
                    <tr key={row.id} className="hover:bg-slate-50/60">
                      <td className="py-3 pr-2 pl-4">
                        <input
                          type="checkbox"
                          checked={selected.has(row.id)}
                          onChange={() => toggle(row.id, row.trackingNumber)}
                          aria-label={`Select #${row.trackingNumber}`}
                          className="accent-brand-700"
                        />
                      </td>
                      <td className="px-3 py-3">
                        <Link
                          to={generatePath(PATHS.adminParcelDetail, { parcelId: row.id })}
                          className="font-semibold text-slate-900 hover:text-brand-700 hover:underline"
                        >
                          #{row.trackingNumber}
                        </Link>
                      </td>
                      <td className="px-3 py-3 text-slate-700">{row.merchantName ?? '—'}</td>
                      <td className="px-3 py-3 text-slate-700">{row.recipientName}</td>
                      <td className="px-3 py-3 text-slate-700">{row.destination}</td>
                      <td className="px-3 py-3 text-slate-700">{row.weightKg} kg</td>
                      <td className="px-3 py-3 text-slate-700">
                        {formatShortDateTime(row.createdAt)}
                      </td>
                      <td className="px-3 py-3">
                        <Button
                          size="sm"
                          className="bg-brand-800 hover:bg-brand-900"
                          onClick={() =>
                            setAssigning([{ id: row.id, trackingNumber: row.trackingNumber }])
                          }
                          aria-label={`Assign #${row.trackingNumber}`}
                        >
                          Assign
                        </Button>
                      </td>
                    </tr>
                  ))}
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

      {assigning && (
        <AssignToCourierDialog
          parcels={assigning}
          onClose={() => setAssigning(null)}
          onAssigned={(courier, result) => {
            const done = new Set(result.assigned.map((a) => a.parcelId));
            // Assigned parcels leave the selection; the failed ones stay selected for another try.
            setSelected((current) => new Map([...current].filter(([id]) => !done.has(id))));
            setVersion((n) => n + 1);
            if (assigning.length === 1) {
              setAssigning(null);
              setNotice({
                tone: 'success',
                text: `#${assigning[0].trackingNumber} was assigned to ${courier.fullName}.`,
              });
            } else {
              setNotice(undefined);
            }
          }}
        />
      )}
    </div>
  );
}
