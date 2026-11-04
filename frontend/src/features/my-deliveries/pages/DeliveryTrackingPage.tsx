import {
  ArrowLeft,
  Check,
  CircleX,
  Download,
  FileText,
  Info,
  MapPin,
  MoveRight,
  Package,
  PackageSearch,
  Pencil,
  Share2,
  Wifi,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { parcelsApi } from '../../../api/parcels';
import LoadingState from '../../../components/feedback/LoadingState';
import StateView from '../../../components/feedback/StateView';
import CopyButton from '../../shared-parcels/components/CopyButton';
import DeliveryProgress from '../../shared-parcels/components/DeliveryProgress';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatusHistoryList from '../../shared-parcels/components/StatusHistoryList';
import { statusVisual, TONE_TILE } from '../../shared-parcels/components/statusVisuals';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { useApiData } from '../../shared-parcels/useApiData';
import { PATHS } from '../../../routes/paths';
import type { Address, CustomerParcelDetail } from '../../../types/parcel';
import { cn } from '../../../utils/cn';
import { formatDateTime } from '../../../utils/date';
import { downloadReceipt, trackingLink } from '../../shared-parcels/download';
import {
  buildProgress,
  formatDimensions,
  formatFee,
  formatRelative,
  PARCEL_TYPE_LABELS,
} from '../../shared-parcels/parcelDisplay';
import { STATUS_META } from '../../../utils/parcelStatus';

function AddressCard({ title, address }: { title: string; address: Address }) {
  return (
    <div className="flex flex-1 gap-3 rounded-xl bg-slate-50 p-4">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
        <MapPin className="size-4" aria-hidden="true" />
      </span>
      <address className="text-sm leading-6 text-slate-600 not-italic">
        <span className="block font-semibold text-slate-900">{title}</span>
        {address.contactName}
        <br />
        {address.addressLine1}
        {address.addressLine2 && (
          <>
            <br />
            {address.addressLine2}
          </>
        )}
        <br />
        {address.city}
        {address.postalCode ? ` ${address.postalCode}` : ''}, {address.district}
        <br />
        Sri Lanka
        <br />
        {address.contactPhone}
      </address>
    </div>
  );
}

function DetailRows({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[minmax(0,180px)_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-slate-500">{label}</dt>
          <dd className="font-medium text-slate-900">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function IconTile({ children }: { children: ReactNode }) {
  return (
    <span className="hidden size-14 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-500 sm:flex">
      {children}
    </span>
  );
}

function Details({ parcel }: { parcel: CustomerParcelDetail }) {
  const [shared, setShared] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const visual = statusVisual(parcel.status);
  const StatusIcon = visual.icon;
  const lastItem = parcel.history[parcel.history.length - 1];
  const statusLine =
    lastItem?.status === 'FAILED' && lastItem.failureReason
      ? `Reason: ${lastItem.failureReason}`
      : lastItem?.note || STATUS_META[parcel.status].description;

  async function share() {
    try {
      await navigator.clipboard.writeText(trackingLink(parcel.trackingNumber));
      setShared(true);
      setTimeout(() => setShared(false), 2000);
    } catch {
      // Clipboard blocked: the tracking number is still on screen to copy by hand.
    }
  }

  async function receipt() {
    setDownloading(true);
    try {
      await downloadReceipt(parcel.id, parcel.trackingNumber);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div>
        <Link
          to={PATHS.customerDeliveries}
          className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back to My Deliveries
        </Link>
        <header className="mt-3 flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Delivery Details</h1>
            <p className="mt-1 text-slate-500">
              Track your parcel and view all delivery information.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button variant="outline" size="lg" onClick={() => void share()}>
              {shared ? (
                <Check className="size-4" aria-hidden="true" />
              ) : (
                <Share2 className="size-4" aria-hidden="true" />
              )}
              {shared ? 'Link Copied' : 'Share Tracking Link'}
            </Button>
            <Button
              size="lg"
              className="bg-brand-800 hover:bg-brand-900"
              loading={downloading}
              onClick={() => void receipt()}
            >
              {!downloading && <Download className="size-4" aria-hidden="true" />}
              Download Receipt
            </Button>
          </div>
        </header>
      </div>

      {/* Summary */}
      <section className="grid gap-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs lg:grid-cols-[minmax(0,1fr)_auto_minmax(260px,300px)] lg:items-center">
        <div className="flex items-center gap-5">
          <span className="flex size-20 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-700">
            <Package className="size-10" strokeWidth={1.6} aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <p className="text-sm text-slate-600">Tracking Number</p>
            <p className="mt-1 flex items-center gap-2 text-2xl font-bold break-all text-slate-900">
              {parcel.trackingNumber}
              <CopyButton value={parcel.trackingNumber} label="Copy tracking number" />
            </p>
            <p className="mt-1 text-sm text-slate-500">
              {parcel.orderReference && (
                <>
                  Order ID: {parcel.orderReference} <span className="mx-2 text-slate-300">|</span>
                </>
              )}
              Booked on {formatDateTime(parcel.bookedAt)}
            </p>
          </div>
        </div>

        <div className="text-sm lg:border-l lg:border-slate-200 lg:pl-8">
          <p className="flex items-center gap-2 font-medium text-slate-800">
            <span className="size-2.5 rounded-full bg-brand-500" aria-hidden="true" />
            Live Updates
            <span title="Status changes appear here as the courier updates them.">
              <Info className="size-4 text-slate-400" aria-hidden="true" />
            </span>
          </p>
          <p className="mt-2 text-slate-500">
            Last updated: {formatDateTime(parcel.lastUpdatedAt)}
          </p>
        </div>

        <div
          className={cn(
            'flex items-center gap-4 rounded-xl p-4',
            visual.tone === 'danger'
              ? 'bg-red-50'
              : visual.tone === 'success'
                ? 'bg-brand-50'
                : 'bg-blue-50',
          )}
        >
          <span
            className={cn(
              'flex size-12 shrink-0 items-center justify-center rounded-xl bg-white/70',
              TONE_TILE[visual.tone],
            )}
          >
            <StatusIcon className="size-6" aria-hidden="true" />
          </span>
          <div>
            <p
              className={cn(
                'text-xl font-semibold',
                visual.tone === 'danger'
                  ? 'text-red-700'
                  : visual.tone === 'success'
                    ? 'text-brand-700'
                    : 'text-blue-700',
              )}
            >
              {visual.label}
            </p>
            <p className="mt-0.5 text-xs text-slate-600">{statusLine}</p>
          </div>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="space-y-6">
          <SectionCard title="Delivery Information">
            <div className="flex flex-col items-stretch gap-3 md:flex-row md:items-center">
              <AddressCard title="Pickup Address" address={parcel.pickupAddress} />
              <MoveRight
                className="mx-auto size-5 shrink-0 rotate-90 text-slate-400 md:rotate-0"
                aria-hidden="true"
              />
              <AddressCard title="Delivery Address" address={parcel.dropoffAddress} />
            </div>
          </SectionCard>

          <SectionCard
            title="Parcel Details"
            action={
              <Button
                variant="ghost"
                size="sm"
                disabled={parcel.status !== 'CREATED'}
                title={
                  parcel.status === 'CREATED'
                    ? 'Edit this booking'
                    : 'A parcel can only be edited before pickup'
                }
              >
                <Pencil className="size-4" aria-hidden="true" />
                Edit
              </Button>
            }
          >
            <div className="flex gap-6">
              <IconTile>
                <Package className="size-7" strokeWidth={1.6} aria-hidden="true" />
              </IconTile>
              <DetailRows
                rows={[
                  ['Item Description', parcel.contentDescription || '—'],
                  ['Weight', `${parcel.weightKg} kg`],
                  [
                    'Dimensions (L × W × H)',
                    formatDimensions(parcel.lengthCm, parcel.widthCm, parcel.heightCm) ?? '—',
                  ],
                  ['Parcel Type', PARCEL_TYPE_LABELS[parcel.parcelType]],
                  ['Special Instructions', parcel.deliveryInstructions || '—'],
                ]}
              />
            </div>
          </SectionCard>

          <SectionCard title="Fee Information">
            <div className="flex gap-6">
              <IconTile>
                <FileText className="size-7" strokeWidth={1.6} aria-hidden="true" />
              </IconTile>
              <DetailRows
                rows={[
                  ['Estimated Delivery Fee', formatFee(parcel.quotedFee, parcel.feeCurrency)],
                  [
                    'Final Delivery Fee',
                    formatFee(parcel.finalFee ?? parcel.quotedFee, parcel.feeCurrency),
                  ],
                  [
                    'Payment Method',
                    <span key="payment" className="inline-flex flex-wrap items-center gap-2">
                      {parcel.paymentMethod ?? '—'}
                      {parcel.paymentStatus === 'PAID' && (
                        <span className="inline-flex items-center gap-1 rounded-md bg-brand-50 px-2 py-0.5 text-xs font-semibold text-brand-700">
                          <Check className="size-3.5" aria-hidden="true" />
                          Paid
                        </span>
                      )}
                    </span>,
                  ],
                ]}
              />
            </div>
          </SectionCard>
        </div>

        <div className="space-y-6">
          <SectionCard
            title="Delivery Progress"
            action={
              <span className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
                <span className="size-2 rounded-full bg-brand-600" aria-hidden="true" />
                Updated {formatRelative(parcel.lastUpdatedAt)}
              </span>
            }
          >
            <DeliveryProgress steps={buildProgress(parcel.history)} />
          </SectionCard>

          <SectionCard title="Status History">
            <StatusHistoryList history={parcel.history} />
            <div className="mt-4 flex gap-4 rounded-xl bg-amber-50 p-4">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-600">
                <Wifi className="size-5" aria-hidden="true" />
              </span>
              <div className="text-sm">
                <p className="font-medium text-slate-900">
                  You'll receive live updates as your parcel moves through each stage.
                </p>
                <p className="mt-1 text-slate-600">
                  If updates are delayed, we'll notify you via email and SMS.
                </p>
              </div>
            </div>
          </SectionCard>
        </div>
      </div>
    </div>
  );
}

/** One of the customer's deliveries: addresses, parcel, fee, progress and history. */
export default function DeliveryTrackingPage() {
  const { trackingNumber = '' } = useParams();
  const parcel = useApiData(trackingNumber, () => parcelsApi.getMyParcel(trackingNumber));

  if (parcel.status === 'loading') return <LoadingState title="Loading delivery..." />;

  if (parcel.status === 'error') {
    return (
      <StateView
        icon={CircleX}
        tone="danger"
        title="Something Went Wrong"
        message={parcel.message}
        actions={
          <Button variant="outline" onClick={parcel.retry}>
            Try Again
          </Button>
        }
      />
    );
  }

  if (!parcel.data) {
    return (
      <StateView
        icon={PackageSearch}
        title="Delivery Not Found"
        message="We couldn't find this delivery in your account."
        actions={<ButtonLink to={PATHS.customerDeliveries}>Back to My Deliveries</ButtonLink>}
      />
    );
  }

  return <Details parcel={parcel.data} />;
}
