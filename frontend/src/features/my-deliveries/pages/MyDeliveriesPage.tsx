import {
  ArrowDown,
  CalendarDays,
  CircleCheck,
  CircleX,
  Download,
  Package,
  PackageOpen,
  Plus,
  Search,
  Truck,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { generatePath, useSearchParams } from 'react-router';
import { parcelsApi } from '../../../api/parcels';
import StateView from '../../../components/feedback/StateView';
import Pagination from '../../shared-parcels/components/Pagination';
import RowMenu from '../../shared-parcels/components/RowMenu';
import StatCard from '../../shared-parcels/components/StatCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { useApiData } from '../../shared-parcels/useApiData';
import { PATHS } from '../../../routes/paths';
import type { CustomerParcelFilters, CustomerParcelSummary } from '../../../types/parcel';
import type { ParcelStatusCode } from '../../../types/tracking';
import { downloadReceipt, trackingLink } from '../../shared-parcels/download';
import {
  countActive,
  countAll,
  formatDate,
  formatFee,
  formatTime,
} from '../../shared-parcels/parcelDisplay';
import { STATUS_META } from '../../../utils/parcelStatus';

const PAGE_SIZE = 10;
const STATUSES = Object.keys(STATUS_META) as ParcelStatusCode[];

function isStatus(value: string | null): value is ParcelStatusCode {
  return value !== null && (STATUSES as string[]).includes(value);
}

function DeliveriesTable({ rows }: { rows: CustomerParcelSummary[] }) {
  const [downloading, setDownloading] = useState<string | null>(null);

  async function receipt(row: CustomerParcelSummary) {
    setDownloading(row.id);
    try {
      await downloadReceipt(row.id, row.trackingNumber);
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200">
      <table className="w-full min-w-[860px] text-left text-sm">
        <thead className="bg-slate-50 text-xs text-slate-600">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">
              Tracking Number
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Destination
            </th>
            <th scope="col" className="px-4 py-3 font-medium" aria-sort="descending">
              <span className="inline-flex items-center gap-1">
                Booking Date
                <ArrowDown className="size-3.5" aria-hidden="true" />
              </span>
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Delivery Fee
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Actions
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row) => (
            <tr key={row.id}>
              <td className="px-4 py-3">
                <div className="flex items-center gap-3">
                  <Package
                    className="size-6 shrink-0 text-slate-400"
                    strokeWidth={1.6}
                    aria-hidden="true"
                  />
                  <div>
                    <p className="font-semibold text-slate-900">{row.trackingNumber}</p>
                    {row.orderReference && (
                      <p className="text-xs text-slate-500">{row.orderReference}</p>
                    )}
                  </div>
                </div>
              </td>
              <td className="px-4 py-3">
                <p className="text-slate-800">{row.destinationCity}</p>
                <p className="text-xs text-slate-500">{row.destinationDistrict}</p>
              </td>
              <td className="px-4 py-3">
                <p className="text-slate-800">{formatDate(row.bookedAt)}</p>
                <p className="text-xs text-slate-500">{formatTime(row.bookedAt)}</p>
              </td>
              <td className="px-4 py-3 font-semibold text-slate-900">
                {formatFee(row.quotedFee, row.feeCurrency)}
              </td>
              <td className="px-4 py-3">
                <StatusPill visual={statusVisual(row.status)} />
              </td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <ButtonLink
                    to={generatePath(PATHS.customerDeliveryDetail, {
                      trackingNumber: row.trackingNumber,
                    })}
                    variant="outline"
                    size="sm"
                  >
                    View
                    <span className="sr-only"> {row.trackingNumber}</span>
                  </ButtonLink>
                  <Button
                    variant="outline"
                    size="sm"
                    className="w-9 px-0"
                    aria-label={`Download receipt for ${row.trackingNumber}`}
                    title="Download receipt"
                    loading={downloading === row.id}
                    onClick={() => void receipt(row)}
                  >
                    {downloading !== row.id && <Download className="size-4" aria-hidden="true" />}
                  </Button>
                  <RowMenu
                    label={`More actions for ${row.trackingNumber}`}
                    items={[
                      {
                        label: 'Copy tracking link',
                        onSelect: () =>
                          void navigator.clipboard?.writeText(trackingLink(row.trackingNumber)),
                      },
                    ]}
                  />
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** All of the customer's deliveries with search, date and status filters. */
export default function MyDeliveriesPage() {
  const [searchParams] = useSearchParams();
  const initialStatus = searchParams.get('status');

  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ParcelStatusCode | ''>(
    isStatus(initialStatus) ? initialStatus : '',
  );
  const [bookedFrom, setBookedFrom] = useState('');
  const [bookedTo, setBookedTo] = useState('');
  const [page, setPage] = useState(0);

  // Search after the user stops typing for a moment.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const filters: CustomerParcelFilters = {
    search: search || undefined,
    status: status || undefined,
    bookedFrom: bookedFrom || undefined,
    bookedTo: bookedTo || undefined,
    page,
    size: PAGE_SIZE,
  };
  const counts = useApiData('counts', () => parcelsApi.getMyCounts());
  const list = useApiData(JSON.stringify(filters), () => parcelsApi.listMyParcels(filters));
  const countsData = counts.status === 'success' ? counts.data : null;
  const filtered = Boolean(search || status || bookedFrom || bookedTo);

  function reset() {
    setSearchInput('');
    setSearch('');
    setStatus('');
    setBookedFrom('');
    setBookedTo('');
    setPage(0);
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">My Deliveries</h1>
          <p className="mt-1 text-slate-500">View and manage all your delivery requests.</p>
        </div>
        <ButtonLink to={PATHS.customerBook} size="lg" className="bg-brand-800 hover:bg-brand-900">
          <Plus className="size-5" aria-hidden="true" />
          Book a New Delivery
        </ButtonLink>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Total Deliveries"
          value={countsData ? countAll(countsData) : 0}
          caption="All time"
          icon={Package}
          tone="neutral"
          loading={counts.status === 'loading'}
        />
        <StatCard
          label="Active Deliveries"
          value={countsData ? countActive(countsData) : 0}
          caption="In transit / out for delivery"
          icon={Truck}
          tone="info"
          loading={counts.status === 'loading'}
        />
        <StatCard
          label="Delivered"
          value={countsData?.DELIVERED ?? 0}
          caption="Successfully delivered"
          icon={CircleCheck}
          tone="success"
          loading={counts.status === 'loading'}
        />
        <StatCard
          label="Failed"
          value={countsData?.FAILED ?? 0}
          caption="Delivery attempts failed"
          icon={CircleX}
          tone="danger"
          loading={counts.status === 'loading'}
        />
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs sm:p-5">
        <div className="mb-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_200px_auto]">
          <div className="relative">
            <Search
              className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400"
              aria-hidden="true"
            />
            <label htmlFor="deliveries-search" className="sr-only">
              Search deliveries
            </label>
            <input
              id="deliveries-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Search by tracking number, destination or order ID..."
              className={inputClasses(false, 'h-11 pl-10')}
            />
          </div>

          <fieldset className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm shadow-xs">
            <legend className="sr-only">Booking date range</legend>
            <CalendarDays className="size-4 shrink-0 text-slate-500" aria-hidden="true" />
            <label htmlFor="booked-from" className="sr-only">
              From
            </label>
            <input
              id="booked-from"
              type="date"
              value={bookedFrom}
              max={bookedTo || undefined}
              onChange={(event) => {
                setBookedFrom(event.target.value);
                setPage(0);
              }}
              className="min-w-0 bg-transparent text-slate-700 outline-none"
            />
            <span className="text-slate-400" aria-hidden="true">
              –
            </span>
            <label htmlFor="booked-to" className="sr-only">
              To
            </label>
            <input
              id="booked-to"
              type="date"
              value={bookedTo}
              min={bookedFrom || undefined}
              onChange={(event) => {
                setBookedTo(event.target.value);
                setPage(0);
              }}
              className="min-w-0 bg-transparent text-slate-700 outline-none"
            />
          </fieldset>

          <div>
            <label htmlFor="deliveries-status" className="sr-only">
              Status
            </label>
            <select
              id="deliveries-status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as ParcelStatusCode | '');
                setPage(0);
              }}
              className={inputClasses(false, 'h-11')}
            >
              <option value="">All Statuses</option>
              {STATUSES.map((code) => (
                <option key={code} value={code}>
                  {STATUS_META[code].label}
                </option>
              ))}
            </select>
          </div>

          <Button variant="outline" onClick={reset} disabled={!filtered && !searchInput}>
            Reset
          </Button>
        </div>

        {list.status === 'loading' && (
          <div className="space-y-2" aria-busy="true" aria-label="Loading deliveries">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-14 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}

        {list.status === 'error' && (
          <StateView
            icon={CircleX}
            tone="danger"
            title="Could not load your deliveries"
            message={list.message}
            actions={
              <Button variant="outline" size="sm" onClick={list.retry}>
                Try again
              </Button>
            }
          />
        )}

        {list.status === 'success' && list.data.content.length === 0 && (
          <StateView
            icon={PackageOpen}
            title={filtered ? 'No deliveries match your filters' : 'No deliveries yet'}
            message={
              filtered
                ? 'Try another search or clear the filters.'
                : 'Book your first delivery to see it here.'
            }
            actions={
              filtered ? (
                <Button variant="outline" size="sm" onClick={reset}>
                  Clear filters
                </Button>
              ) : (
                <ButtonLink to={PATHS.customerBook} size="sm">
                  Book a Delivery
                </ButtonLink>
              )
            }
          />
        )}

        {list.status === 'success' && list.data.content.length > 0 && (
          <>
            <DeliveriesTable rows={list.data.content} />
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-slate-500">
                Showing {list.data.page.number * list.data.page.size + 1} to{' '}
                {list.data.page.number * list.data.page.size + list.data.content.length} of{' '}
                {list.data.page.totalElements} deliveries
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
    </div>
  );
}
