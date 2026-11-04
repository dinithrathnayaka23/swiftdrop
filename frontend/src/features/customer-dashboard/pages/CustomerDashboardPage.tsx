import {
  ArrowRight,
  CircleCheck,
  CircleX,
  Package,
  PackageOpen,
  Plus,
  Search,
  Truck,
} from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { generatePath, Link, useNavigate } from 'react-router';
import { parcelsApi } from '../../../api/parcels';
import StateView from '../../../components/feedback/StateView';
import RecentNotificationsCard from '../components/RecentNotificationsCard';
import CopyButton from '../../shared-parcels/components/CopyButton';
import RowMenu from '../../shared-parcels/components/RowMenu';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatCard from '../../shared-parcels/components/StatCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { useApiData } from '../../shared-parcels/useApiData';
import { useAuth } from '../../../hooks/useAuth';
import { PATHS } from '../../../routes/paths';
import type { CustomerParcelSummary } from '../../../types/parcel';
import { downloadReceipt, trackingLink } from '../../shared-parcels/download';
import { countActive, formatDate, formatTime } from '../../shared-parcels/parcelDisplay';
import { validateTrackingNumber } from '../../../utils/validation';

function TrackParcelCard() {
  const navigate = useNavigate();
  const [value, setValue] = useState('');
  const [error, setError] = useState<string>();

  function submit(event: FormEvent) {
    event.preventDefault();
    const problem = validateTrackingNumber(value);
    setError(problem);
    if (!problem)
      navigate(generatePath(PATHS.trackResult, { trackingNumber: value.trim().toUpperCase() }));
  }

  return (
    <SectionCard>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <span className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-stone-100 text-stone-600">
          <Package className="size-7" strokeWidth={1.8} aria-hidden="true" />
        </span>
        <form className="min-w-0 flex-1" onSubmit={submit} noValidate>
          <h2 className="text-lg font-semibold text-slate-900">Track a Parcel</h2>
          <p className="mt-0.5 text-sm text-slate-500">
            Enter your tracking number to see the latest delivery status.
          </p>
          <div className="mt-4 flex flex-col gap-3 sm:flex-row">
            <label htmlFor="dashboard-track" className="sr-only">
              Tracking number
            </label>
            <input
              id="dashboard-track"
              value={value}
              onChange={(event) => setValue(event.target.value)}
              placeholder="e.g. SD123456789"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? 'dashboard-track-error' : undefined}
              className={inputClasses(Boolean(error), 'h-12 flex-1')}
            />
            <Button type="submit" size="lg" className="bg-brand-800 hover:bg-brand-900">
              <Search className="size-4" aria-hidden="true" />
              Track Parcel
            </Button>
          </div>
          {error && (
            <p id="dashboard-track-error" className="mt-2 text-sm text-red-600">
              {error}
            </p>
          )}
        </form>
      </div>
    </SectionCard>
  );
}

function RecentShipments({ rows }: { rows: CustomerParcelSummary[] }) {
  if (rows.length === 0) {
    return (
      <StateView
        icon={PackageOpen}
        title="No deliveries yet"
        message="Book your first delivery and it will show up here."
        actions={
          <ButtonLink to={PATHS.customerBook} size="sm">
            Book a Delivery
          </ButtonLink>
        }
      />
    );
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium text-slate-600">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">
              Tracking Number
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Destination
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Booking Date
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
              <td className="px-4 py-3 font-medium text-slate-900">
                <span className="inline-flex items-center gap-1">
                  {row.trackingNumber}
                  <CopyButton
                    value={row.trackingNumber}
                    label={`Copy tracking number ${row.trackingNumber}`}
                  />
                </span>
              </td>
              <td className="px-4 py-3 text-slate-600">
                {row.destinationCity}, {row.destinationDistrict}
              </td>
              <td className="px-4 py-3 text-slate-700">
                {formatDate(row.bookedAt)}
                <span className="block text-xs text-slate-500">{formatTime(row.bookedAt)}</span>
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
                  <RowMenu
                    label={`More actions for ${row.trackingNumber}`}
                    items={[
                      {
                        label: 'Copy tracking link',
                        onSelect: () =>
                          void navigator.clipboard?.writeText(trackingLink(row.trackingNumber)),
                      },
                      {
                        label: 'Download receipt',
                        onSelect: () => void downloadReceipt(row.id, row.trackingNumber),
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

/** Customer home: counts, quick tracking, recent shipments and notifications. */
export default function CustomerDashboardPage() {
  const { user } = useAuth();
  const counts = useApiData('counts', () => parcelsApi.getMyCounts());
  const recent = useApiData('recent', () => parcelsApi.listMyParcels({ page: 0, size: 5 }));
  const firstName = user?.displayName.split(' ')[0] ?? 'there';
  const countsData = counts.status === 'success' ? counts.data : null;

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
            Welcome back, {firstName} <span aria-hidden="true">👋</span>
          </h1>
          <p className="mt-1 text-slate-500">
            Manage your deliveries, track parcels and book new shipments with SwiftDrop.
          </p>
        </div>
        <ButtonLink to={PATHS.customerBook} size="lg" className="bg-brand-800 hover:bg-brand-900">
          <Plus className="size-5" aria-hidden="true" />
          Book a Delivery
        </ButtonLink>
      </header>

      {counts.status === 'error' && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
          Could not load your delivery counts. {counts.message}{' '}
          <button type="button" className="font-semibold underline" onClick={counts.retry}>
            Try again
          </button>
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        <StatCard
          label="Active Deliveries"
          value={countsData ? countActive(countsData) : 0}
          caption="Currently in transit"
          icon={Truck}
          tone="info"
          to={PATHS.customerDeliveries}
          loading={counts.status === 'loading'}
        />
        <StatCard
          label="Delivered"
          value={countsData?.DELIVERED ?? 0}
          caption="Successfully delivered"
          icon={CircleCheck}
          tone="success"
          to={`${PATHS.customerDeliveries}?status=DELIVERED`}
          loading={counts.status === 'loading'}
        />
        <StatCard
          label="Failed Deliveries"
          value={countsData?.FAILED ?? 0}
          caption="Delivery attempts failed"
          icon={CircleX}
          tone="danger"
          to={`${PATHS.customerDeliveries}?status=FAILED`}
          loading={counts.status === 'loading'}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="min-w-0 space-y-6">
          <TrackParcelCard />
          <SectionCard
            title="Recent Shipments"
            subtitle="Your latest delivery requests"
            action={
              <Link
                to={PATHS.customerDeliveries}
                className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800"
              >
                View all deliveries
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            }
          >
            {recent.status === 'loading' && (
              <div className="space-y-2" aria-busy="true" aria-label="Loading recent shipments">
                {Array.from({ length: 4 }, (_, i) => (
                  <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
                ))}
              </div>
            )}
            {recent.status === 'error' && (
              <StateView
                icon={CircleX}
                tone="danger"
                title="Could not load your shipments"
                message={recent.message}
                actions={
                  <Button variant="outline" size="sm" onClick={recent.retry}>
                    Try again
                  </Button>
                }
              />
            )}
            {recent.status === 'success' && <RecentShipments rows={recent.data.content} />}
          </SectionCard>
        </div>
        <RecentNotificationsCard />
      </div>
    </div>
  );
}
