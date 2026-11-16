import {
  Bike,
  CircleCheck,
  CircleX,
  MapPin,
  Package,
  PackageX,
  RefreshCw,
  Truck,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { generatePath, Link } from 'react-router';
import { dispatchApi } from '../../../api/dispatch';
import { parcelsApi } from '../../../api/parcels';
import Button from '../../../components/ui/Button';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { PATHS } from '../../../routes/paths';
import StatCard from '../../shared-parcels/components/StatCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { useApiData } from '../../shared-parcels/useApiData';
import { availabilityVisual } from '../../couriers/courierRules';
import RetriedNotice from '../components/RetriedNotice';
import RetryDialog from '../components/RetryDialog';
import StatusDonut from '../components/StatusDonut';
import type { FailedParcelRef } from '../failedActions';

/** Automatic refresh while the browser tab is visible. */
const OVERVIEW_REFRESH_MS = 60_000;
const BLOCK_ROWS = 5;

/**
 * Loads like useApiData, but keeps showing the last data while a refresh is under way, so the
 * dashboard doesn't flash empty every minute.
 */
function useKeptData<T>(key: string, load: () => Promise<T>) {
  const state = useApiData(key, load);
  const [kept, setKept] = useState<T>();
  if (state.status === 'success' && state.data !== kept) setKept(state.data);
  return {
    data: state.status === 'success' ? state.data : kept,
    loading: state.status === 'loading',
    error: state.status === 'error' ? state.message : undefined,
    retry: state.retry,
  };
}

type Kept<T> = ReturnType<typeof useKeptData<T>>;

/** A labelled dashboard block that loads and fails on its own. */
function Block<T>({
  title,
  icon: Icon,
  action,
  data,
  className,
  children,
}: {
  title: string;
  icon: LucideIcon;
  action?: ReactNode;
  data: Kept<T>;
  className?: string;
  children: (value: T) => ReactNode;
}) {
  const headingId = useId();
  return (
    <section
      aria-labelledby={headingId}
      className={`rounded-2xl border border-slate-200 bg-white p-5 shadow-xs ${className ?? ''}`}
    >
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2
          id={headingId}
          className="flex items-center gap-2 text-base font-semibold text-slate-900"
        >
          <Icon className="size-5 text-brand-700" aria-hidden="true" />
          {title}
        </h2>
        {action}
      </div>
      {data.data !== undefined ? (
        <>
          {data.error && (
            <p className="mb-3 text-sm text-red-700">
              Couldn&apos;t refresh: {data.error}{' '}
              <button type="button" className="font-semibold underline" onClick={data.retry}>
                Try again
              </button>
            </p>
          )}
          {children(data.data)}
        </>
      ) : data.error ? (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          <p>
            Couldn&apos;t load {title.toLowerCase()}: {data.error}
          </p>
          <Button variant="outline" size="sm" className="mt-2" onClick={data.retry}>
            Try again
          </Button>
        </div>
      ) : (
        <div className="space-y-2" aria-busy="true" aria-label={`Loading ${title.toLowerCase()}`}>
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="h-10 animate-pulse rounded-lg bg-slate-100" />
          ))}
        </div>
      )}
    </section>
  );
}

function ViewAll({ to, label }: { to: string; label: string }) {
  return (
    <Link to={to} className="text-sm font-medium text-brand-700 hover:underline">
      View All<span className="sr-only"> {label}</span>
    </Link>
  );
}

function Table({ headers, children }: { headers: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[520px] text-left text-sm">
        <thead className="border-b border-slate-200 text-xs text-slate-500">
          <tr>
            {headers.map((h) => (
              <th key={h} scope="col" className="py-2 pr-3 font-medium">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">{children}</tbody>
      </table>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return <p className="py-6 text-center text-sm text-slate-500">{text}</p>;
}

function ParcelLink({ parcelId, trackingNumber }: { parcelId: string; trackingNumber: string }) {
  return (
    <Link
      to={generatePath(PATHS.adminParcelDetail, { parcelId })}
      className="font-semibold text-slate-900 hover:text-brand-700 hover:underline"
    >
      #{trackingNumber}
    </Link>
  );
}

const QUICK_ACTIONS: { label: string; to: string; icon: LucideIcon }[] = [
  { label: 'View Unassigned Parcels', to: PATHS.adminUnassigned, icon: Package },
  { label: 'Manage Couriers', to: PATHS.adminCouriers, icon: Users },
  { label: 'Failed Deliveries', to: PATHS.adminFailedDeliveries, icon: PackageX },
  { label: 'Service Areas', to: PATHS.adminServiceAreas, icon: MapPin },
];

/** Admin home: today's numbers, couriers' workload and the parcels that need someone. */
export default function OperationsOverviewPage() {
  const { formatShortDateTime } = useDateFormat();
  const [version, setVersion] = useState(0);
  const [updatedAt, setUpdatedAt] = useState(() => new Date().toISOString());
  const [retrying, setRetrying] = useState<FailedParcelRef | null>(null);
  const [retried, setRetried] = useState<FailedParcelRef>();
  const lastRefresh = useRef(0);

  const refresh = useCallback(() => {
    lastRefresh.current = Date.now();
    setUpdatedAt(new Date().toISOString());
    setVersion((n) => n + 1);
  }, []);

  // Every minute while the tab is visible; on coming back after a minute or more, at once.
  useEffect(() => {
    lastRefresh.current = Date.now();
    const timer = setInterval(() => {
      if (!document.hidden) refresh();
    }, OVERVIEW_REFRESH_MS);
    const onVisible = () => {
      if (!document.hidden && Date.now() - lastRefresh.current >= OVERVIEW_REFRESH_MS) refresh();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [refresh]);

  const overview = useKeptData(`overview#${version}`, () => dispatchApi.overview());
  const unassigned = useKeptData(`overview-unassigned#${version}`, () =>
    parcelsApi.listAdminParcels({ tab: 'UNASSIGNED', page: 0, size: BLOCK_ROWS }),
  );
  const active = useKeptData(`overview-active#${version}`, () =>
    dispatchApi.activeDeliveries({ page: 0, size: BLOCK_ROWS }),
  );
  const failed = useKeptData(`overview-failed#${version}`, () =>
    dispatchApi.listFailed({ scope: 'ATTENTION', page: 0, size: BLOCK_ROWS }),
  );

  // "11:20 AM" in the admin's time zone and time format.
  const updatedLabel = formatShortDateTime(updatedAt).split(', ').at(-1);
  const kpis = overview.data?.kpis;

  return (
    <div className="mx-auto max-w-[1600px] space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Operations Overview</h1>
          <p className="mt-1 text-slate-500">
            Today&apos;s deliveries, couriers and parcels to act on.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-500" aria-live="polite">
            Updated {updatedLabel}
          </span>
          <Button variant="outline" onClick={refresh}>
            <RefreshCw className="size-4" aria-hidden="true" />
            Refresh
          </Button>
        </div>
      </header>

      {retried && <RetriedNotice parcel={retried} />}

      <section aria-label="Today's numbers">
        {overview.error && !kpis ? (
          <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            Couldn&apos;t load today&apos;s numbers: {overview.error}{' '}
            <button type="button" className="font-semibold underline" onClick={overview.retry}>
              Try again
            </button>
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <StatCard
              label="Unassigned Parcels"
              value={kpis?.unassignedParcels ?? 0}
              caption="Waiting for a courier"
              icon={Package}
              tone="warning"
              to={PATHS.adminUnassigned}
              loading={!kpis}
            />
            <StatCard
              label="Active Deliveries"
              value={kpis?.activeDeliveries ?? 0}
              caption="With a courier now"
              icon={Truck}
              tone="info"
              to={PATHS.adminDeliveries}
              loading={!kpis}
            />
            <StatCard
              label="Available Couriers"
              value={kpis ? `${kpis.couriersOnline} / ${kpis.couriersTotal}` : 0}
              caption={kpis ? `${kpis.couriersOnBreak} on break` : ''}
              icon={Bike}
              tone="success"
              to={PATHS.adminCouriers}
              loading={!kpis}
            />
            <StatCard
              label="Completed Today"
              value={kpis?.completedToday ?? 0}
              caption="Since midnight"
              icon={CircleCheck}
              tone="success"
              loading={!kpis}
            />
            <StatCard
              label="Failed Today"
              value={kpis?.failedToday ?? 0}
              caption={kpis ? `${kpis.needsDecision} need a decision` : ''}
              icon={CircleX}
              tone="danger"
              to={PATHS.adminFailedDeliveries}
              loading={!kpis}
            />
          </div>
        )}
      </section>

      <div className="grid gap-5 xl:grid-cols-2">
        <Block
          title="Unassigned Parcels"
          icon={Package}
          data={unassigned}
          action={<ViewAll to={PATHS.adminUnassigned} label="unassigned parcels" />}
        >
          {(page) =>
            page.content.length === 0 ? (
              <Empty text="Every parcel has a courier." />
            ) : (
              <Table
                headers={[
                  'Tracking No.',
                  'Merchant',
                  'Destination',
                  'Weight',
                  'Created At',
                  'Action',
                ]}
              >
                {page.content.map((row) => (
                  <tr key={row.id}>
                    <td className="py-2.5 pr-3">
                      <ParcelLink parcelId={row.id} trackingNumber={row.trackingNumber} />
                    </td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.merchantName ?? '—'}</td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.destination}</td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.weightKg} kg</td>
                    <td className="py-2.5 pr-3 text-slate-700">
                      {formatShortDateTime(row.createdAt)}
                    </td>
                    <td className="py-2.5">
                      <Link
                        to={`${PATHS.adminUnassigned}?parcelId=${encodeURIComponent(row.id)}`}
                        className="inline-flex h-8 items-center rounded-md bg-brand-800 px-3 text-xs font-semibold text-white hover:bg-brand-900"
                        aria-label={`Assign #${row.trackingNumber}`}
                      >
                        Assign
                      </Link>
                    </td>
                  </tr>
                ))}
              </Table>
            )
          }
        </Block>

        <Block
          title="Active Deliveries"
          icon={Truck}
          data={active}
          action={<ViewAll to={PATHS.adminDeliveries} label="active deliveries" />}
        >
          {(page) =>
            page.content.length === 0 ? (
              <Empty text="No courier is delivering right now." />
            ) : (
              <Table headers={['Tracking No.', 'Courier', 'Destination', 'Status']}>
                {page.content.map((row) => (
                  <tr key={`${row.parcelId}-${row.assignedAt}`}>
                    <td className="py-2.5 pr-3">
                      {row.trackingNumber ? (
                        <ParcelLink parcelId={row.parcelId} trackingNumber={row.trackingNumber} />
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.courierName ?? '—'}</td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.destinationCity ?? '—'}</td>
                    <td className="py-2.5">
                      {row.parcelStatus ? (
                        <StatusPill visual={statusVisual(row.parcelStatus)} />
                      ) : (
                        '—'
                      )}
                    </td>
                  </tr>
                ))}
              </Table>
            )
          }
        </Block>

        <Block
          title="Courier Workload"
          icon={Users}
          data={overview}
          action={<ViewAll to={PATHS.adminCouriers} label="couriers" />}
        >
          {(data) =>
            data.courierWorkload.length === 0 ? (
              <Empty text="No active couriers." />
            ) : (
              <Table
                headers={[
                  'Courier',
                  "Today's Deliveries",
                  'Completed',
                  'In Progress',
                  'Pending',
                  'Status',
                ]}
              >
                {data.courierWorkload.map((row) => (
                  <tr key={row.courierId}>
                    <td className="py-2.5 pr-3 font-medium text-slate-900">{row.name ?? '—'}</td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.todaysTotal}</td>
                    <td className="py-2.5 pr-3 text-brand-700">{row.completed}</td>
                    <td className="py-2.5 pr-3 text-sky-700">{row.inProgress}</td>
                    <td className="py-2.5 pr-3 text-amber-700">{row.pending}</td>
                    <td className="py-2.5">
                      <StatusPill visual={availabilityVisual(row.availability)} />
                    </td>
                  </tr>
                ))}
              </Table>
            )
          }
        </Block>

        <Block
          title="Failed deliveries needing a decision"
          icon={PackageX}
          data={failed}
          action={<ViewAll to={PATHS.adminFailedDeliveries} label="failed deliveries" />}
        >
          {(page) =>
            page.content.length === 0 ? (
              <Empty text="Nothing is waiting for a retry or close." />
            ) : (
              <Table headers={['Tracking No.', 'Courier', 'Reason', 'Failed At', 'Actions']}>
                {page.content.map((row) => (
                  <tr key={row.parcelId}>
                    <td className="py-2.5 pr-3">
                      <ParcelLink parcelId={row.parcelId} trackingNumber={row.trackingNumber} />
                    </td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.courierName ?? '—'}</td>
                    <td className="py-2.5 pr-3 text-red-700">{row.failureReason ?? '—'}</td>
                    <td className="py-2.5 pr-3 text-slate-700">
                      {row.failedAt ? formatShortDateTime(row.failedAt) : '—'}
                    </td>
                    <td className="py-2.5">
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          disabled={retrying !== null}
                          onClick={() =>
                            setRetrying({
                              parcelId: row.parcelId,
                              trackingNumber: row.trackingNumber,
                            })
                          }
                          aria-label={`Retry #${row.trackingNumber}`}
                        >
                          Retry
                        </Button>
                        <Link
                          to={generatePath(PATHS.adminParcelDetail, { parcelId: row.parcelId })}
                          className="inline-flex h-8 items-center rounded-md border border-slate-300 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                          aria-label={`View #${row.trackingNumber}`}
                        >
                          View
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </Table>
            )
          }
        </Block>

        <Block title="Today's Delivery Status" icon={CircleCheck} data={overview}>
          {(data) => (
            <StatusDonut
              totalLabel="today"
              segments={[
                { label: 'Completed', value: data.todayStatus.completed, color: '#15803d' },
                { label: 'In Transit', value: data.todayStatus.inTransit, color: '#2563eb' },
                {
                  label: 'Out for Delivery',
                  value: data.todayStatus.outForDelivery,
                  color: '#f59e0b',
                },
                { label: 'Failed', value: data.todayStatus.failed, color: '#dc2626' },
              ]}
            />
          )}
        </Block>

        <section
          aria-label="Quick actions"
          className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs"
        >
          <h2 className="mb-4 text-base font-semibold text-slate-900">Quick Actions</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {QUICK_ACTIONS.map(({ label, to, icon: Icon }) => (
              <Link
                key={label}
                to={to}
                className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-medium text-slate-700 hover:border-brand-200 hover:bg-brand-50/40"
              >
                <Icon className="size-5 text-brand-700" aria-hidden="true" />
                {label}
              </Link>
            ))}
          </div>
        </section>
      </div>

      {retrying && (
        <RetryDialog
          parcel={retrying}
          onClose={() => setRetrying(null)}
          onRetried={(parcel) => {
            setRetrying(null);
            setRetried(parcel);
            refresh();
          }}
        />
      )}
    </div>
  );
}
