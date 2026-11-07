import {
  ArrowLeft,
  Map as MapIcon,
  MapPin,
  Package,
  PackageSearch,
  Phone,
  Store,
  Truck,
  User,
  type LucideIcon,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import LoadingState from '../../../components/feedback/LoadingState';
import { ErrorState } from '../../../components/feedback/States';
import StateView from '../../../components/feedback/StateView';
import ButtonLink from '../../../components/ui/ButtonLink';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { PATHS } from '../../../routes/paths';
import CopyButton from '../../shared-parcels/components/CopyButton';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatusHistoryList from '../../shared-parcels/components/StatusHistoryList';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { formatDimensions, PARCEL_TYPE_LABELS } from '../../shared-parcels/parcelDisplay';
import { useApiData } from '../../shared-parcels/useApiData';
import type { LiveStatusUpdate } from '../../shared-live/types';
import { useLiveUpdates } from '../../shared-live/useLiveUpdates';
import { adminLiveApi } from '../api';
import LiveIndicator from '../../shared-live/LiveIndicator';
import { statusLabel } from '../filters';
import type { LiveDeliveryDetail } from '../types';

/** The delivery with a live status change applied, if it is newer than what was loaded. */
function withUpdate(
  detail: LiveDeliveryDetail,
  update: LiveStatusUpdate | null,
): LiveDeliveryDetail {
  if (!update || update.statusVersion <= detail.statusVersion) return detail;
  return {
    ...detail,
    status: update.status,
    statusVersion: update.statusVersion,
    lastUpdatedAt: update.changedAt,
    history: [...detail.history, { status: update.status, changedAt: update.changedAt }],
  };
}

function Heading({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return (
    <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
      <Icon className="size-5 text-slate-500" aria-hidden="true" />
      {children}
    </h2>
  );
}

function Details({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="mt-4 divide-y divide-slate-100 text-sm">
      {items.map(([label, value]) => (
        <div key={label} className="flex justify-between gap-4 py-2.5">
          <dt className="text-slate-500">{label}</dt>
          <dd className="text-right font-medium text-slate-900">{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}

const LINK =
  'inline-flex items-center gap-2 rounded-sm font-medium text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600';

/** Admin: one delivery on its own page, kept up to date live. */
export default function DeliveryDetailPage() {
  const { trackingNumber = '' } = useParams();
  const [reloads, setReloads] = useState(0);
  const [update, setUpdate] = useState<LiveStatusUpdate | null>(null);
  const { formatDateTime } = useDateFormat();
  const detail = useApiData(`${trackingNumber}#${reloads}`, () =>
    adminLiveApi.getDelivery(trackingNumber),
  );
  const found = detail.status === 'success' && detail.data ? withUpdate(detail.data, update) : null;

  const connection = useLiveUpdates({
    // The public stream of this one parcel: it carries no personal data, and needs no token.
    path: found ? `/public/tracking/${encodeURIComponent(found.trackingNumber)}/stream` : null,
    knownVersions: found ? { [found.trackingNumber]: found.statusVersion } : {},
    onUpdate: setUpdate,
    onReconnect: () => setReloads((n) => n + 1),
  });

  const back = (
    <Link
      to={PATHS.adminDeliveries}
      className="inline-flex items-center gap-1.5 rounded-md text-sm font-medium text-slate-600 hover:text-slate-900"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Back to Deliveries
    </Link>
  );

  if (detail.status === 'loading') {
    return (
      <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        {back}
        <LoadingState title="Loading delivery..." />
      </div>
    );
  }
  if (detail.status === 'error') {
    return (
      <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        {back}
        <ErrorState message={detail.message} onAction={detail.retry} />
      </div>
    );
  }
  if (!found) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        {back}
        <StateView
          icon={PackageSearch}
          title="Delivery Not Found"
          message={`No parcel has the tracking number ${trackingNumber}.`}
          actions={<ButtonLink to={PATHS.adminDeliveries}>Back to Deliveries</ButtonLink>}
        />
      </div>
    );
  }

  const address = found.dropoffAddress;
  const addressText = [
    address.addressLine1,
    address.addressLine2,
    address.city,
    address.district,
    address.postalCode,
  ]
    .filter(Boolean)
    .join(', ');
  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(addressText)}`;

  return (
    <div className="mx-auto max-w-6xl space-y-5 px-4 py-6 sm:px-6 lg:py-8">
      {back}

      <SectionCard>
        <div className="flex flex-wrap items-center gap-4">
          <span className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-orange-50 text-orange-500">
            <Package className="size-7" strokeWidth={1.8} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-bold tracking-tight text-slate-900">
                #{found.trackingNumber}
              </h1>
              <CopyButton value={found.trackingNumber} label="Copy tracking number" />
              <StatusPill
                visual={{ ...statusVisual(found.status), label: statusLabel(found.status) }}
              />
            </div>
            <p className="mt-1 text-sm text-slate-500">
              Booked on {formatDateTime(found.bookedAt)} · Last update{' '}
              {formatDateTime(found.lastUpdatedAt)}
            </p>
          </div>
          <LiveIndicator state={connection} />
        </div>
      </SectionCard>

      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <SectionCard>
            <Heading icon={User}>Recipient Information</Heading>
            <p className="mt-4 font-medium text-slate-900">{found.recipientName}</p>
            <a href={`tel:${found.recipientPhone.replace(/\s+/g, '')}`} className={`${LINK} mt-1`}>
              <Phone className="size-4" aria-hidden="true" />
              {found.recipientPhone}
            </a>

            <div className="mt-6 border-t border-slate-100 pt-5">
              <Heading icon={MapPin}>Delivery Address</Heading>
              <address className="mt-3 text-sm leading-6 text-slate-700 not-italic">
                {address.addressLine1}
                {address.addressLine2 && (
                  <>
                    <br />
                    {address.addressLine2}
                  </>
                )}
                <br />
                {address.city}, {address.district}
                {address.postalCode && ` ${address.postalCode}`}
              </address>
              <a
                href={mapsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className={`${LINK} mt-3`}
              >
                <MapIcon className="size-4" aria-hidden="true" />
                Open in Maps
                <span className="sr-only">(opens in a new tab)</span>
              </a>
            </div>
          </SectionCard>

          <SectionCard>
            <Heading icon={Package}>Parcel Information</Heading>
            <Details
              items={[
                ['Type', PARCEL_TYPE_LABELS[found.parcelType]],
                ['Weight', `${found.weightKg} kg`],
                ['Dimensions', formatDimensions(found.lengthCm, found.widthCm, found.heightCm)],
                ['Contents', found.contentDescription],
                ['Special Instructions', found.deliveryInstructions],
                ['From', found.fromCity],
              ]}
            />
          </SectionCard>
        </div>

        <div className="space-y-5">
          <SectionCard>
            <Heading icon={Store}>Merchant</Heading>
            <p className="mt-3 text-sm text-slate-700">
              {found.merchantName ?? 'Customer booking (no merchant)'}
            </p>
          </SectionCard>

          <SectionCard>
            <Heading icon={Truck}>Courier Information</Heading>
            <p className="mt-3 text-sm text-slate-700">
              {found.courierName ?? 'Not available yet'}
            </p>
          </SectionCard>

          <SectionCard title="Timeline">
            <StatusHistoryList history={found.history} />
          </SectionCard>
        </div>
      </div>
    </div>
  );
}
