import { Bike, Mail, MapPin, Pencil, Phone, Truck, X, type LucideIcon } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { generatePath, Link } from 'react-router';
import { couriersApi } from '../../../api/couriers';
import { getErrorMessage } from '../../../api/errors';
import LoadingState from '../../../components/feedback/LoadingState';
import { ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { PATHS } from '../../../routes/paths';
import type { Courier, CourierAvailability, CourierAssignmentRow } from '../../../types/courier';
import { cn } from '../../../utils/cn';
import { STATUS_META } from '../../../utils/parcelStatus';
import Pagination from '../../shared-parcels/components/Pagination';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { useApiData } from '../../shared-parcels/useApiData';
import {
  assignmentVisual,
  availabilityVisual,
  AVAILABILITY_OPTIONS,
  initials,
} from '../courierRules';
import { useActiveZones } from '../activeZones';

type Tab = 'overview' | 'current' | 'history';
const TABS: { id: Tab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'current', label: 'Current Deliveries' },
  { id: 'history', label: 'History' },
];
const HISTORY_PAGE_SIZE = 10;

function Block({
  icon: Icon,
  title,
  action,
  children,
}: {
  icon: LucideIcon;
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-slate-200 p-4">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
          <Icon className="size-4 text-slate-600" aria-hidden="true" />
          {title}
        </h3>
        {action}
      </div>
      {children}
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-1.5 text-sm">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right font-medium text-slate-800">{children}</dd>
    </div>
  );
}

function OverviewTab({
  courier,
  onEdit,
  onAvailability,
}: {
  courier: Courier;
  onEdit: () => void;
  onAvailability: (availability: CourierAvailability) => Promise<void>;
}) {
  const activeZones = useActiveZones();
  const activeIds =
    activeZones.status === 'success' ? new Set(activeZones.data.content.map((z) => z.id)) : null;
  const [changing, setChanging] = useState<CourierAvailability>();
  const [error, setError] = useState<string>();
  const { workload } = courier;

  async function change(availability: CourierAvailability) {
    setChanging(availability);
    setError(undefined);
    try {
      await onAvailability(availability);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setChanging(undefined);
    }
  }

  return (
    <div className="space-y-4">
      <Block icon={Truck} title="Availability">
        <div role="radiogroup" aria-label="Availability" className="grid grid-cols-3 gap-2">
          {AVAILABILITY_OPTIONS.map((option) => {
            const selected = courier.availability === option.value;
            return (
              <button
                key={option.value}
                type="button"
                role="radio"
                aria-checked={selected}
                disabled={changing !== undefined}
                onClick={() => !selected && change(option.value)}
                className={cn(
                  'rounded-lg border px-3 py-2 text-sm font-medium transition-colors disabled:opacity-60',
                  selected
                    ? 'border-brand-600 bg-brand-50 text-brand-800'
                    : 'border-slate-200 text-slate-600 hover:bg-slate-50',
                )}
              >
                {changing === option.value ? 'Saving…' : option.label}
              </button>
            );
          })}
        </div>
        {!courier.active && (
          <p className="mt-2 text-sm text-slate-500">
            This courier is deactivated and cannot go online.
          </p>
        )}
        {error && (
          <Alert tone="error" className="mt-3">
            {error}
          </Alert>
        )}
      </Block>

      <Block icon={Phone} title="Contact">
        <dl>
          <Row label="Phone">{courier.phone}</Row>
          <Row label="Email">
            <span className="inline-flex items-center gap-1.5 break-all">
              <Mail className="size-3.5 text-slate-400" aria-hidden="true" />
              {courier.email}
            </span>
          </Row>
          {courier.accountStatus !== 'ACTIVE' && (
            <Row label="Account">{courier.accountStatus.replace('_', ' ').toLowerCase()}</Row>
          )}
        </dl>
      </Block>

      <Block
        icon={Bike}
        title="Vehicle"
        action={
          <Button variant="outline" size="sm" onClick={onEdit}>
            <Pencil className="size-3.5" aria-hidden="true" />
            Edit
          </Button>
        }
      >
        <dl>
          <Row label="Type">{courier.vehicleType}</Row>
          <Row label="Registration">{courier.vehicleRegistration}</Row>
        </dl>
      </Block>

      <Block
        icon={MapPin}
        title="Service Areas"
        action={
          <Button variant="outline" size="sm" onClick={onEdit} aria-label="Edit service areas">
            <Pencil className="size-3.5" aria-hidden="true" />
            Edit
          </Button>
        }
      >
        {courier.serviceAreas.length === 0 ? (
          <p className="text-sm text-slate-500">
            No service areas: this courier can take parcels anywhere.
          </p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {courier.serviceAreas.map((area) => (
              <li
                key={area.id}
                className="inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-2.5 py-1 text-xs text-slate-700"
              >
                {area.name ?? 'Unknown area'}
                {activeIds && !activeIds.has(area.id) && (
                  <span className="rounded bg-red-50 px-1.5 text-[11px] font-semibold text-red-700">
                    inactive
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </Block>

      <Block icon={Truck} title="Today's Workload">
        <dl>
          <Row label="Completed today">
            {workload.completedToday} / {workload.todaysTotal}
          </Row>
          <Row label="In progress">{workload.inProgress}</Row>
          <Row label="Waiting for pickup">{workload.assigned}</Row>
        </dl>
      </Block>
    </div>
  );
}

function AssignmentList({ rows, empty }: { rows: CourierAssignmentRow[]; empty: string }) {
  const { formatShortDateTime } = useDateFormat();
  if (rows.length === 0) return <p className="py-6 text-center text-sm text-slate-500">{empty}</p>;
  return (
    <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
      {rows.map((row) => (
        <li key={row.id} className="space-y-1 px-4 py-3 text-sm">
          <div className="flex items-center justify-between gap-2">
            {row.trackingNumber ? (
              <Link
                to={generatePath(PATHS.adminParcelDetail, { parcelId: row.parcelId })}
                className="font-semibold text-brand-700 hover:underline"
              >
                #{row.trackingNumber}
              </Link>
            ) : (
              <span className="font-semibold text-slate-500">Parcel unavailable</span>
            )}
            <StatusPill visual={assignmentVisual(row.status)} />
          </div>
          <p className="text-slate-600">
            {row.destinationCity ?? '—'}
            {row.parcelStatus && (
              <span className="text-slate-400"> · {STATUS_META[row.parcelStatus].label}</span>
            )}
          </p>
          <p className="text-xs text-slate-500">
            Assigned {formatShortDateTime(row.assignedAt)}
            {row.endedAt && <> · Ended {formatShortDateTime(row.endedAt)}</>}
          </p>
          {row.reason && <p className="text-xs text-slate-500">Reason: {row.reason}</p>}
        </li>
      ))}
    </ul>
  );
}

function DeliveriesTab({ courierId, open }: { courierId: string; open: boolean }) {
  const [page, setPage] = useState(0);
  const size = open ? 50 : HISTORY_PAGE_SIZE;
  const rows = useApiData(`${courierId}|${open}|${page}`, () =>
    couriersApi.listAssignments(courierId, { open, page, size }),
  );
  if (rows.status === 'loading') return <LoadingState title="Loading deliveries…" message="" />;
  if (rows.status === 'error') {
    return (
      <ErrorState title="Couldn't load deliveries" message={rows.message} onAction={rows.retry} />
    );
  }
  return (
    <div className="space-y-4">
      <AssignmentList
        rows={rows.data.content}
        empty={open ? 'No deliveries in progress or waiting.' : 'No finished deliveries yet.'}
      />
      {rows.data.page.totalPages > 1 && (
        <div className="flex justify-center">
          <Pagination page={page} totalPages={rows.data.page.totalPages} onChange={setPage} />
        </div>
      )}
    </div>
  );
}

interface CourierDrawerProps {
  courierId: string;
  /** Changes when the courier was saved elsewhere (edit modal), to reload. */
  version: number;
  onClose: () => void;
  onEdit: (courier: Courier) => void;
  /** After a change made here (availability), so the list can refresh. */
  onChanged: () => void;
}

/** The courier's details beside the list: overview, current deliveries and history. */
export default function CourierDrawer({
  courierId,
  version,
  onClose,
  onEdit,
  onChanged,
}: CourierDrawerProps) {
  const titleId = useId();
  const [tab, setTab] = useState<Tab>('overview');
  const [reload, setReload] = useState(0);
  const courier = useApiData(`${courierId}#${version}#${reload}`, () => couriersApi.get(courierId));
  const { formatDate } = useDateFormat();
  const panelRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    panelRef.current?.querySelector<HTMLElement>('button')?.focus();
    const onKey = (e: KeyboardEvent) => {
      // A modal on top handles its own Escape.
      if (
        e.key === 'Escape' &&
        !document.querySelector('[role="dialog"][aria-modal="true"]:not([data-drawer])')
      ) {
        onCloseRef.current();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      opener?.focus?.();
    };
  }, []);

  async function changeAvailability(availability: CourierAvailability) {
    await couriersApi.changeAvailability(courierId, availability);
    setReload((n) => n + 1);
    onChanged();
  }

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-slate-900/30" onClick={onClose}>
      <div
        ref={panelRef}
        data-drawer
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="flex h-full w-full max-w-md flex-col bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 px-5 py-4">
          {courier.status === 'success' ? (
            <div className="flex items-center gap-3">
              <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-brand-50 text-base font-semibold text-brand-700">
                {initials(courier.data.fullName)}
              </span>
              <div>
                <h2 id={titleId} className="text-lg font-semibold text-slate-900">
                  {courier.data.fullName}
                </h2>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                  <StatusPill visual={availabilityVisual(courier.data.availability)} />
                  {!courier.data.active && (
                    <span className="rounded bg-slate-100 px-2 py-0.5">Deactivated</span>
                  )}
                  <span>Joined {formatDate(courier.data.createdAt)}</span>
                </div>
              </div>
            </div>
          ) : (
            <h2 id={titleId} className="text-lg font-semibold text-slate-900">
              Courier
            </h2>
          )}
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
            aria-label="Close courier details"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>

        <div
          role="tablist"
          aria-label="Courier details"
          className="flex border-b border-slate-200 px-3"
        >
          {TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              id={`courier-tab-${item.id}`}
              aria-selected={tab === item.id}
              aria-controls={`courier-panel-${item.id}`}
              onClick={() => setTab(item.id)}
              className={cn(
                'flex-1 border-b-2 px-2 py-3 text-sm font-medium',
                tab === item.id
                  ? 'border-brand-700 text-brand-800'
                  : 'border-transparent text-slate-500 hover:text-slate-700',
              )}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div
          role="tabpanel"
          id={`courier-panel-${tab}`}
          aria-labelledby={`courier-tab-${tab}`}
          className="flex-1 overflow-y-auto p-5"
        >
          {tab === 'overview' &&
            (courier.status === 'loading' ? (
              <LoadingState title="Loading courier…" message="" />
            ) : courier.status === 'error' ? (
              <ErrorState
                title="Couldn't load the courier"
                message={courier.message}
                onAction={courier.retry}
              />
            ) : (
              <OverviewTab
                courier={courier.data}
                onEdit={() => onEdit(courier.data)}
                onAvailability={changeAvailability}
              />
            ))}
          {tab === 'current' && <DeliveriesTab courierId={courierId} open />}
          {tab === 'history' && <DeliveriesTab courierId={courierId} open={false} />}
        </div>
      </div>
    </div>
  );
}
