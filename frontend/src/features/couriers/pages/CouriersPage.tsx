import { CircleDot, CircleMinus, CirclePause, CircleX, Plus, Search, Users } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { couriersApi } from '../../../api/couriers';
import { getErrorMessage } from '../../../api/errors';
import { serviceAreasApi } from '../../../api/serviceAreas';
import StateView from '../../../components/feedback/StateView';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { useSettings } from '../../../hooks/useSettings';
import type { Courier, CourierAvailability, CourierFilters } from '../../../types/courier';
import Pagination from '../../shared-parcels/components/Pagination';
import RowMenu from '../../shared-parcels/components/RowMenu';
import StatCard from '../../shared-parcels/components/StatCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { useApiData } from '../../shared-parcels/useApiData';
import { useDebouncedValue } from '../../shared-dispatch/useDebouncedValue';
import AddCourierModal from '../components/AddCourierModal';
import CourierDrawer from '../components/CourierDrawer';
import EditCourierModal from '../components/EditCourierModal';
import { availabilityVisual, AVAILABILITY_OPTIONS, initials, VEHICLE_TYPES } from '../courierRules';

const MAX_PAGE_SIZE = 100;

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

function percent(part: number, total: number): string {
  return total === 0 ? '0%' : `${Math.round((part / total) * 100)}%`;
}

/** Admin Couriers: who is online, today's workload, the courier drawer and Add Courier. */
export default function CouriersPage() {
  const { settings } = useSettings();
  const size = Math.min(Math.max(settings.itemsPerPage, 1), MAX_PAGE_SIZE);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput.trim(), 300);
  const [availability, setAvailability] = useState<CourierAvailability | ''>('');
  const [serviceAreaId, setServiceAreaId] = useState('');
  const [vehicleType, setVehicleType] = useState('');
  const [page, setPage] = useState(0);
  // Bumped after a change so the list, the cards and the drawer load again.
  const [version, setVersion] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<Courier | null>(null);
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>();

  const filters: CourierFilters = {
    search: search || undefined,
    availability: availability || undefined,
    serviceAreaId: serviceAreaId || undefined,
    vehicleType: vehicleType || undefined,
    page,
    size,
  };
  const list = useApiData(`${JSON.stringify(filters)}#${version}`, () => couriersApi.list(filters));
  const summary = useApiData(`summary#${version}`, () => couriersApi.summary());
  // All zones (active or not) for the filter, so couriers of a deactivated zone can still be found.
  const zones = useApiData('zones-for-filter', () => serviceAreasApi.list({ size: 50 }));
  const rows = list.status === 'success' ? list.data.content : [];

  // A search typed in the box resets the page as soon as it takes effect.
  const [appliedSearch, setAppliedSearch] = useState(search);
  if (appliedSearch !== search) {
    setAppliedSearch(search);
    setPage(0);
  }

  function changeFilter(apply: () => void) {
    apply();
    setPage(0);
  }

  function refresh() {
    setVersion((n) => n + 1);
  }

  async function setCourierAvailability(courier: Courier, next: CourierAvailability) {
    setNotice(undefined);
    try {
      await couriersApi.changeAvailability(courier.id, next);
      setNotice({
        tone: 'success',
        text: `${courier.fullName} is now ${availabilityVisual(next).label}.`,
      });
      refresh();
    } catch (err) {
      setNotice({ tone: 'error', text: getErrorMessage(err) });
    }
  }

  const total = summary.status === 'success' ? summary.data.total : 0;

  return (
    <div className="mx-auto max-w-[1600px] space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Couriers</h1>
          <p className="mt-1 text-slate-500">
            Manage couriers, view their status and today&apos;s workload.
          </p>
        </div>
        <Button className="bg-brand-800 hover:bg-brand-900" onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden="true" />
          Add Courier
        </Button>
      </header>

      <section aria-label="Courier summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Total Couriers"
          value={total}
          caption="Active couriers"
          icon={Users}
          tone="info"
          loading={summary.status === 'loading'}
        />
        <StatCard
          label="Online"
          value={summary.status === 'success' ? summary.data.online : 0}
          caption={`${percent(summary.status === 'success' ? summary.data.online : 0, total)} of couriers`}
          icon={CircleDot}
          tone="success"
          loading={summary.status === 'loading'}
        />
        <StatCard
          label="On Break"
          value={summary.status === 'success' ? summary.data.onBreak : 0}
          caption={`${percent(summary.status === 'success' ? summary.data.onBreak : 0, total)} of couriers`}
          icon={CirclePause}
          tone="warning"
          loading={summary.status === 'loading'}
        />
        <StatCard
          label="Offline"
          value={summary.status === 'success' ? summary.data.offline : 0}
          caption={`${percent(summary.status === 'success' ? summary.data.offline : 0, total)} of couriers`}
          icon={CircleMinus}
          tone="danger"
          loading={summary.status === 'loading'}
        />
      </section>

      {notice && (
        <Alert tone={notice.tone} className="flex items-start justify-between gap-3">
          {notice.text}
        </Alert>
      )}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-xs">
        <div className="grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(3,minmax(0,1fr))] lg:items-end">
          <div className="relative sm:col-span-2 lg:col-span-1">
            <label
              htmlFor="couriers-search"
              className="mb-1 block text-xs font-medium text-slate-600"
            >
              Search
            </label>
            <Search
              className="pointer-events-none absolute bottom-3 left-3.5 size-4 text-slate-400"
              aria-hidden="true"
            />
            <input
              id="couriers-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Name, email, phone or vehicle registration..."
              className={inputClasses(false, 'h-10 pl-10')}
            />
          </div>
          <FilterSelect
            id="couriers-status"
            label="Status"
            value={availability}
            onChange={(value) =>
              changeFilter(() => setAvailability(value as CourierAvailability | ''))
            }
          >
            <option value="">All</option>
            {AVAILABILITY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </FilterSelect>
          <FilterSelect
            id="couriers-area"
            label="Service Area"
            value={serviceAreaId}
            onChange={(value) => changeFilter(() => setServiceAreaId(value))}
          >
            <option value="">All Areas</option>
            {zones.status === 'success' &&
              zones.data.content.map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.active ? zone.name : `${zone.name} (inactive)`}
                </option>
              ))}
          </FilterSelect>
          <FilterSelect
            id="couriers-vehicle"
            label="Vehicle Type"
            value={vehicleType}
            onChange={(value) => changeFilter(() => setVehicleType(value))}
          >
            <option value="">All Types</option>
            {VEHICLE_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </FilterSelect>
        </div>

        {list.status === 'loading' && (
          <div className="space-y-2 px-4 pb-4" aria-busy="true" aria-label="Loading couriers">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-14 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}

        {list.status === 'error' && (
          <StateView
            icon={CircleX}
            tone="danger"
            title="Could not load couriers"
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
            icon={Users}
            title="No Couriers Found"
            message="No couriers match these filters."
          />
        )}

        {list.status === 'success' && rows.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-xs text-slate-600">
                  <tr>
                    {[
                      'Courier',
                      'Status',
                      "Today's Deliveries",
                      'Vehicle',
                      'Service Areas',
                      'Actions',
                    ].map((h) => (
                      <th key={h} scope="col" className="px-4 py-3 font-semibold">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((courier) => {
                    const { completedToday, todaysTotal } = courier.workload;
                    const share =
                      todaysTotal === 0 ? 0 : Math.round((completedToday / todaysTotal) * 100);
                    const canAssign = courier.availability === 'ONLINE' && courier.active;
                    return (
                      <tr key={courier.id} className="hover:bg-slate-50/60">
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700">
                              {initials(courier.fullName)}
                            </span>
                            <div className="min-w-0">
                              <p className="font-semibold text-slate-900">{courier.fullName}</p>
                              <p className="truncate text-xs text-slate-500">{courier.phone}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap items-center gap-1.5">
                            <StatusPill visual={availabilityVisual(courier.availability)} />
                            {!courier.active && (
                              <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                                Deactivated
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <p className="font-medium text-slate-800">
                            {completedToday} / {todaysTotal}
                          </p>
                          <div
                            className="mt-1 h-1.5 w-24 overflow-hidden rounded-full bg-slate-100"
                            role="progressbar"
                            aria-label={`${courier.fullName}: ${completedToday} of ${todaysTotal} deliveries completed today`}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-valuenow={share}
                          >
                            <div
                              className="h-full rounded-full bg-brand-600"
                              style={{ width: `${share}%` }}
                            />
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <p className="text-slate-800">{courier.vehicleType}</p>
                          <p className="text-xs text-slate-500">{courier.vehicleRegistration}</p>
                        </td>
                        <td className="max-w-[220px] px-4 py-3 text-slate-600">
                          <span className="line-clamp-2">
                            {courier.serviceAreas.length === 0
                              ? 'Any area'
                              : courier.serviceAreas
                                  .map((area) => area.name ?? 'Unknown')
                                  .join(', ')}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-1">
                            {canAssign ? (
                              <Button
                                size="sm"
                                className="w-20 bg-brand-800 hover:bg-brand-800 aria-disabled:cursor-not-allowed aria-disabled:opacity-70"
                                aria-disabled="true"
                                title="Assigning from this page comes next"
                                aria-describedby="couriers-assign-later"
                                onClick={(event) => event.preventDefault()}
                              >
                                Assign
                              </Button>
                            ) : (
                              <Button
                                variant="outline"
                                size="sm"
                                className="w-20"
                                onClick={() => setOpenId(courier.id)}
                                aria-label={`View ${courier.fullName}`}
                              >
                                View
                              </Button>
                            )}
                            <RowMenu
                              label={`More actions for ${courier.fullName}`}
                              vertical
                              items={[
                                { label: 'View details', onSelect: () => setOpenId(courier.id) },
                                ...AVAILABILITY_OPTIONS.filter(
                                  (option) => option.value !== courier.availability,
                                ).map((option) => ({
                                  label: `Set ${option.label}`,
                                  onSelect: () => setCourierAvailability(courier, option.value),
                                  disabled: option.value === 'ONLINE' && !courier.active,
                                })),
                                {
                                  label: 'Edit vehicle and areas',
                                  onSelect: () => setEditing(courier),
                                },
                              ]}
                            />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <p id="couriers-assign-later" className="sr-only">
              Assigning parcels from the Couriers page is not available yet.
            </p>
            <div className="flex flex-wrap items-center justify-between gap-3 p-4">
              <p className="text-sm text-slate-500">
                Showing {list.data.page.number * list.data.page.size + 1}–
                {list.data.page.number * list.data.page.size + rows.length} of{' '}
                {list.data.page.totalElements} couriers
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

      {openId && (
        <CourierDrawer
          courierId={openId}
          version={version}
          onClose={() => setOpenId(null)}
          onEdit={setEditing}
          onChanged={refresh}
        />
      )}

      <AddCourierModal
        open={adding}
        onClose={() => setAdding(false)}
        onCreated={(courier) => {
          setAdding(false);
          setNotice({
            tone: 'success',
            text: `${courier.fullName} was added. They start Offline.`,
          });
          refresh();
        }}
      />

      {editing && (
        <EditCourierModal
          courier={editing}
          onClose={() => setEditing(null)}
          onSaved={(courier) => {
            setEditing(null);
            setNotice({ tone: 'success', text: `${courier.fullName} was updated.` });
            refresh();
          }}
        />
      )}
    </div>
  );
}
