import { CalendarDays, CircleX, Download, Filter, PackageOpen, Plus, Search } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { generatePath, useNavigate } from 'react-router';
import { parcelsApi } from '../../../api/parcels';
import { MOCK_ADMIN_AREAS } from '../../../api/mock/mockParcelsApi';
import StateView from '../../../components/feedback/StateView';
import AdminParcelPanel from '../components/AdminParcelPanel';
import Pagination from '../../shared-parcels/components/Pagination';
import RowMenu from '../../shared-parcels/components/RowMenu';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { adminStatusVisual } from '../../shared-parcels/components/statusVisuals';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { PATHS } from '../../../routes/paths';
import { useApiData } from '../../shared-parcels/useApiData';
import type { AdminParcelFilters, AdminParcelRow, AdminParcelTab } from '../../../types/parcel';
import type { ParcelStatusCode } from '../../../types/tracking';
import { cn } from '../../../utils/cn';
import { saveBlob, trackingLink } from '../../shared-parcels/download';
import { formatDate, formatTime } from '../../shared-parcels/parcelDisplay';
import { STATUS_META } from '../../../utils/parcelStatus';

const PAGE_SIZE = 10;
const STATUSES = Object.keys(STATUS_META) as ParcelStatusCode[];

const TABS: { id: AdminParcelTab; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'UNASSIGNED', label: 'Unassigned' },
  { id: 'ASSIGNED', label: 'Assigned' },
  { id: 'IN_TRANSIT', label: 'In Transit' },
  { id: 'DELIVERED', label: 'Delivered' },
  { id: 'FAILED', label: 'Failed' },
];

type DateRange = 'ALL' | 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS';
const DATE_RANGES: { id: DateRange; label: string; days?: number }[] = [
  { id: 'ALL', label: 'All time' },
  { id: 'TODAY', label: 'Today', days: 0 },
  { id: 'LAST_7_DAYS', label: 'Last 7 days', days: 7 },
  { id: 'LAST_30_DAYS', label: 'Last 30 days', days: 30 },
];

function isoDay(daysAgo: number): string {
  return new Date(Date.now() - daysAgo * 86_400_000).toISOString().slice(0, 10);
}

function isToday(iso: string): boolean {
  return iso.slice(0, 10) === isoDay(0);
}

/** Current page as CSV for the Export button. */
function toCsv(rows: AdminParcelRow[]): string {
  const header = [
    'Tracking No.',
    'Merchant',
    'Recipient',
    'Destination',
    'Weight (kg)',
    'Status',
    'Created At',
  ];
  const quote = (value: string | number) => `"${String(value).replaceAll('"', '""')}"`;
  const lines = rows.map((row) =>
    [
      row.trackingNumber,
      row.merchantName ?? 'Customer booking',
      row.recipientName,
      row.destination,
      row.weightKg,
      adminStatusVisual(row.status, row.dispatchState).label,
      row.createdAt,
    ]
      .map(quote)
      .join(','),
  );
  return [header.map(quote).join(','), ...lines].join('\n');
}

function FilterSelect({
  id,
  label,
  value,
  onChange,
  children,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-xs font-medium text-slate-600">
        {label}
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={inputClasses(false, 'h-10')}
      >
        {children}
      </select>
    </div>
  );
}

/** Admin list of all parcels with tabs, filters and a details panel. */
export default function ParcelListPage() {
  const [tab, setTab] = useState<AdminParcelTab>('ALL');
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ParcelStatusCode | ''>('');
  const [area, setArea] = useState('');
  const [dateRange, setDateRange] = useState<DateRange>('ALL');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [openId, setOpenId] = useState<string | null>(null);
  const [panelClosed, setPanelClosed] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const range = DATE_RANGES.find((item) => item.id === dateRange);
  const filters: AdminParcelFilters = {
    tab,
    search: search || undefined,
    status: status || undefined,
    area: area || undefined,
    createdFrom: range?.days !== undefined ? isoDay(range.days) : undefined,
    page,
    size: PAGE_SIZE,
  };
  const counts = useApiData('admin-counts', () => parcelsApi.getAdminCounts());
  const list = useApiData(JSON.stringify(filters), () => parcelsApi.listAdminParcels(filters));
  const rows = list.status === 'success' ? list.data.content : [];

  // Show the first parcel in the side panel (large screens) until the admin picks one.
  const panelId = panelClosed ? null : (openId ?? rows[0]?.id ?? null);

  /** Wide screens show the details beside the list; smaller ones open the parcel detail page. */
  function open(row: AdminParcelRow) {
    if (window.matchMedia?.('(min-width: 80rem)').matches === false) {
      navigate(generatePath(PATHS.adminParcelDetail, { parcelId: row.id }));
      return;
    }
    setOpenId(row.id);
    setPanelClosed(false);
  }

  function close() {
    setOpenId(null);
    setPanelClosed(true);
  }

  function changeFilter(apply: () => void) {
    apply();
    setPage(0);
    setSelected(new Set());
  }

  function toggleRow(id: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const allSelected = rows.length > 0 && rows.every((row) => selected.has(row.id));

  return (
    <div className="mx-auto grid max-w-[1600px] gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
      <div className="min-w-0 space-y-5">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Parcels</h1>
            <p className="mt-1 text-slate-500">
              View, manage and assign incoming parcels to couriers.
            </p>
          </div>
          <div className="flex gap-3">
            <Button
              className="bg-brand-800 hover:bg-brand-900"
              disabled
              title="Adding parcels from the admin portal is not available yet"
            >
              <Plus className="size-4" aria-hidden="true" />
              Add Parcel
            </Button>
            <Button
              variant="outline"
              disabled={rows.length === 0}
              onClick={() => saveBlob(new Blob([toCsv(rows)], { type: 'text/csv' }), 'parcels.csv')}
            >
              <Download className="size-4" aria-hidden="true" />
              Export
            </Button>
          </div>
        </header>

        <section className="rounded-2xl border border-slate-200 bg-white shadow-xs">
          <div
            role="tablist"
            aria-label="Parcel groups"
            className="flex gap-1 overflow-x-auto border-b border-slate-200 px-3"
          >
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                aria-selected={tab === item.id}
                onClick={() => changeFilter(() => setTab(item.id))}
                className={cn(
                  'flex shrink-0 items-center gap-2 border-b-2 px-3 py-4 text-sm font-medium',
                  tab === item.id
                    ? 'border-brand-700 text-brand-700'
                    : 'border-transparent text-slate-600 hover:text-slate-900',
                )}
              >
                {item.label}
                <span
                  className={cn(
                    'rounded-md px-2 py-0.5 text-xs font-semibold',
                    tab === item.id ? 'bg-brand-50 text-brand-700' : 'bg-slate-100 text-slate-600',
                  )}
                >
                  {counts.status === 'success' ? counts.data[item.id] : '–'}
                </span>
              </button>
            ))}
          </div>

          <div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_150px_150px_150px_auto] xl:items-end">
            <div className="relative md:col-span-2 xl:col-span-1">
              <Search
                className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <label htmlFor="parcels-search" className="sr-only">
                Search parcels
              </label>
              <input
                id="parcels-search"
                type="search"
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Search by tracking number, recipient or merchant..."
                className={inputClasses(false, 'h-10 pl-10')}
              />
            </div>
            <FilterSelect
              id="parcels-status"
              label="Status"
              value={status}
              onChange={(value) => changeFilter(() => setStatus(value as ParcelStatusCode | ''))}
            >
              <option value="">All Statuses</option>
              {STATUSES.map((code) => (
                <option key={code} value={code}>
                  {STATUS_META[code].label}
                </option>
              ))}
            </FilterSelect>
            <FilterSelect
              id="parcels-area"
              label="Service Area"
              value={area}
              onChange={(value) => changeFilter(() => setArea(value))}
            >
              <option value="">All Areas</option>
              {MOCK_ADMIN_AREAS.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </FilterSelect>
            <div>
              <label
                htmlFor="parcels-date"
                className="mb-1 flex items-center gap-1 text-xs font-medium text-slate-600"
              >
                <CalendarDays className="size-3.5" aria-hidden="true" />
                Date Range
              </label>
              <select
                id="parcels-date"
                value={dateRange}
                onChange={(event) =>
                  changeFilter(() => setDateRange(event.target.value as DateRange))
                }
                className={inputClasses(false, 'h-10')}
              >
                {DATE_RANGES.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
            <Button
              variant="outline"
              className="h-10"
              disabled
              title="More filters are not available yet"
            >
              <Filter className="size-4" aria-hidden="true" />
              More Filters
            </Button>
          </div>

          {list.status === 'loading' && (
            <div className="space-y-2 px-4 pb-4" aria-busy="true" aria-label="Loading parcels">
              {Array.from({ length: 8 }, (_, i) => (
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
              icon={PackageOpen}
              title="No Parcels Found"
              message="No parcels match these filters."
            />
          )}

          {list.status === 'success' && rows.length > 0 && (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[960px] text-left text-sm">
                  <thead className="border-y border-slate-200 bg-slate-50 text-xs text-slate-600">
                    <tr>
                      <th scope="col" className="w-12 px-4 py-3">
                        <input
                          type="checkbox"
                          aria-label="Select all parcels on this page"
                          checked={allSelected}
                          onChange={() =>
                            setSelected(
                              allSelected ? new Set() : new Set(rows.map((row) => row.id)),
                            )
                          }
                          className="size-4 rounded border-slate-300 accent-brand-700"
                        />
                      </th>
                      {[
                        'Tracking No.',
                        'Merchant',
                        'Recipient',
                        'Destination',
                        'Weight',
                        'Status',
                        'Created At',
                        'Action',
                      ].map((label) => (
                        <th key={label} scope="col" className="px-3 py-3 font-medium">
                          {label}
                        </th>
                      ))}
                      <th scope="col" className="w-12 px-3 py-3">
                        <span className="sr-only">More</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((row) => {
                      const unassigned =
                        row.status === 'CREATED' && row.dispatchState === 'UNASSIGNED';
                      return (
                        <tr key={row.id} className={cn(panelId === row.id && 'bg-brand-50/40')}>
                          <td className="px-4 py-3">
                            <input
                              type="checkbox"
                              aria-label={`Select parcel ${row.trackingNumber}`}
                              checked={selected.has(row.id)}
                              onChange={() => toggleRow(row.id)}
                              className="size-4 rounded border-slate-300 accent-brand-700"
                            />
                          </td>
                          <td className="px-3 py-3 font-semibold text-slate-900">
                            #{row.trackingNumber}
                          </td>
                          <td className="px-3 py-3 text-slate-700">{row.merchantName ?? '—'}</td>
                          <td className="px-3 py-3 text-slate-700">{row.recipientName}</td>
                          <td className="px-3 py-3 text-slate-700">{row.destination}</td>
                          <td className="px-3 py-3 text-slate-700">{row.weightKg} kg</td>
                          <td className="px-3 py-3">
                            <StatusPill
                              visual={adminStatusVisual(row.status, row.dispatchState)}
                              className="rounded-full"
                            />
                          </td>
                          <td className="px-3 py-3 text-slate-700">
                            {isToday(row.createdAt)
                              ? formatTime(row.createdAt)
                              : formatDate(row.createdAt)}
                          </td>
                          <td className="px-3 py-3">
                            <Button
                              size="sm"
                              variant={unassigned ? 'primary' : 'outline'}
                              className={cn(
                                'min-w-16',
                                unassigned && 'bg-brand-800 hover:bg-brand-900',
                              )}
                              onClick={() => open(row)}
                            >
                              {unassigned ? 'Assign' : 'View'}
                              <span className="sr-only"> {row.trackingNumber}</span>
                            </Button>
                          </td>
                          <td className="px-3 py-3">
                            <RowMenu
                              vertical
                              label={`More actions for ${row.trackingNumber}`}
                              items={[
                                { label: 'View details', onSelect: () => open(row) },
                                {
                                  label: 'Copy tracking link',
                                  onSelect: () =>
                                    void navigator.clipboard?.writeText(
                                      trackingLink(row.trackingNumber),
                                    ),
                                },
                              ]}
                            />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3 p-4">
                <p className="text-sm text-slate-500">
                  {selected.size > 0 ? `${selected.size} selected · ` : ''}Showing{' '}
                  {list.data.page.number * list.data.page.size + 1}–
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
      </div>

      {/* Details beside the list on xl screens (smaller screens use the detail page). */}
      {panelId && (
        <aside className="hidden self-start rounded-2xl border border-slate-200 bg-white shadow-xs xl:sticky xl:top-4 xl:block xl:max-h-[calc(100vh-2rem)] xl:overflow-hidden">
          <AdminParcelPanel key={panelId} parcelId={panelId} onClose={close} />
        </aside>
      )}
    </div>
  );
}
