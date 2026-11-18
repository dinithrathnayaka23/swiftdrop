import { ArrowLeft, CircleX, MapPin, MoveRight, PackageSearch } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import LoadingState from '../../../components/feedback/LoadingState';
import StateView from '../../../components/feedback/StateView';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { PATHS } from '../../../routes/paths';
import CopyButton from '../../shared-parcels/components/CopyButton';
import DeliveryProgress from '../../shared-parcels/components/DeliveryProgress';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatusHistoryList from '../../shared-parcels/components/StatusHistoryList';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import {
  buildProgress,
  formatDate,
  formatDimensions,
  formatFee,
  formatTime,
  latestFailureReason,
  PARCEL_TYPE_LABELS,
} from '../../shared-parcels/parcelDisplay';
import { useApiData } from '../../shared-parcels/useApiData';
import { merchantPortalApi } from '../api';
import type { MerchantOrderAddress, MerchantOrderDetail } from '../types';

function AddressCard({ title, address }: { title: string; address: MerchantOrderAddress | null }) {
  return (
    <div className="flex flex-1 gap-3 rounded-xl bg-slate-50 p-4">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
        <MapPin className="size-4" aria-hidden="true" />
      </span>
      <address className="text-sm leading-6 text-slate-600 not-italic">
        <span className="block font-semibold text-slate-900">{title}</span>
        {address ? (
          <>
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
            {[address.city, address.postalCode].filter(Boolean).join(' ')}
            {address.district && `, ${address.district}`}
            <br />
            {address.contactPhone}
          </>
        ) : (
          '—'
        )}
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

function Details({ order }: { order: MerchantOrderDetail }) {
  const failureReason = latestFailureReason(order.timeline);
  const delivered = [...order.timeline].reverse().find((entry) => entry.status === 'DELIVERED');

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <Link
        to={PATHS.merchantOrders}
        className="inline-flex items-center gap-1 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Back to Orders
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="inline-flex items-center gap-2 text-2xl font-bold text-slate-900 sm:text-3xl">
            {order.trackingNumber}
            <CopyButton value={order.trackingNumber} label="Copy tracking number" />
          </h1>
          <p className="mt-1 text-slate-500">
            {order.externalOrderReference ? `Your order ${order.externalOrderReference} · ` : ''}
            Created {formatDate(order.createdAt)}, {formatTime(order.createdAt)}
          </p>
        </div>
        <StatusPill visual={statusVisual(order.status)} />
      </header>

      {failureReason && (
        <p role="status" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
          Reason: {failureReason}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="space-y-6">
          <SectionCard title="Delivery Information">
            <div className="flex flex-col items-stretch gap-3 md:flex-row md:items-center">
              <AddressCard title="Pickup Address" address={order.pickupAddress} />
              <MoveRight
                className="mx-auto size-5 shrink-0 rotate-90 text-slate-400 md:rotate-0"
                aria-hidden="true"
              />
              <AddressCard title="Delivery Address" address={order.dropoffAddress} />
            </div>
          </SectionCard>

          <SectionCard title="Parcel Details">
            <DetailRows
              rows={[
                ['Item Description', order.contentDescription || '—'],
                ['Weight', order.weightKg != null ? `${order.weightKg} kg` : '—'],
                [
                  'Dimensions (L × W × H)',
                  formatDimensions(order.lengthCm, order.widthCm, order.heightCm) ?? '—',
                ],
                ['Parcel Type', PARCEL_TYPE_LABELS[order.parcelType]],
                ['Special Instructions', order.deliveryInstructions || '—'],
                [
                  'Delivery Fee',
                  order.quotedFee != null
                    ? formatFee(order.quotedFee, order.feeCurrency ?? 'LKR')
                    : '—',
                ],
                ...(delivered?.recipientName
                  ? [['Received By', delivered.recipientName] as [string, ReactNode]]
                  : []),
              ]}
            />
          </SectionCard>
        </div>

        <div className="space-y-6">
          <SectionCard title="Delivery Progress">
            <DeliveryProgress steps={buildProgress(order.timeline)} />
          </SectionCard>
          <SectionCard title="Status History">
            <StatusHistoryList history={order.timeline} />
          </SectionCard>
        </div>
      </div>
    </div>
  );
}

/** One of the merchant's orders: addresses, parcel details, progress and status history. */
export default function OrderDetailsPage() {
  const { trackingNumber = '' } = useParams();
  const order = useApiData(trackingNumber, () => merchantPortalApi.getOrder(trackingNumber));

  if (order.status === 'loading') return <LoadingState title="Loading order..." />;

  if (order.status === 'error') {
    return (
      <StateView
        icon={CircleX}
        tone="danger"
        title="Something Went Wrong"
        message={order.message}
        actions={
          <Button variant="outline" onClick={order.retry}>
            Try Again
          </Button>
        }
      />
    );
  }

  if (!order.data) {
    return (
      <StateView
        icon={PackageSearch}
        title="Order Not Found"
        message="We couldn't find this order in your account."
        actions={<ButtonLink to={PATHS.merchantOrders}>Back to Orders</ButtonLink>}
      />
    );
  }

  return <Details order={order.data} />;
}
