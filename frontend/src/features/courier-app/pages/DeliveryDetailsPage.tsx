import { ArrowLeft, PackageSearch } from 'lucide-react';
import { Link, useParams } from 'react-router';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import ButtonLink from '../../../components/ui/ButtonLink';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { PATHS } from '../../../routes/paths';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatusHistoryList from '../../shared-parcels/components/StatusHistoryList';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { formatDimensions, PARCEL_TYPE_LABELS } from '../../shared-parcels/parcelDisplay';
import { useApiData } from '../../shared-parcels/useApiData';
import { courierApi } from '../api';
import DeliveryActions from '../components/DeliveryActions';
import StopDetails from '../components/StopDetails';
import type { CourierDeliveryDetail } from '../types';

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-sm text-slate-900">{value}</dd>
    </div>
  );
}

function DeliveryView({ delivery }: { delivery: CourierDeliveryDetail }) {
  const { formatDateTime } = useDateFormat();
  const waitingForPickup = delivery.currentStatus === 'CREATED';
  const pickup = <StopDetails label="Pick up from" stop={delivery.pickup} />;
  const dropoff = <StopDetails label="Deliver to" stop={delivery.dropoff} />;
  const dimensions = formatDimensions(delivery.lengthCm, delivery.widthCm, delivery.heightCm);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-mono text-xl font-bold text-slate-900">{delivery.trackingNumber}</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            Booked {formatDateTime(delivery.bookedAt)}
          </p>
        </div>
        <StatusPill visual={statusVisual(delivery.currentStatus)} />
      </div>

      <DeliveryActions parcelId={delivery.parcelId} allowedActions={delivery.allowedActions} />

      {/* The next stop first: the pickup until the parcel is collected, then the recipient. */}
      <SectionCard>
        <div className="flex flex-col gap-5">
          {waitingForPickup ? pickup : dropoff}
          <hr className="border-slate-100" />
          {waitingForPickup ? dropoff : pickup}
        </div>
      </SectionCard>

      {delivery.deliveryInstructions && (
        <SectionCard title="Delivery Instructions">
          <p className="text-sm text-slate-700">{delivery.deliveryInstructions}</p>
        </SectionCard>
      )}

      <SectionCard title="Parcel">
        <dl className="grid grid-cols-2 gap-4">
          <Detail label="Type" value={PARCEL_TYPE_LABELS[delivery.parcelType]} />
          <Detail label="Weight" value={`${delivery.weightKg} kg`} />
          {dimensions && <Detail label="Size" value={dimensions} />}
          {delivery.contentDescription && (
            <Detail label="Contents" value={delivery.contentDescription} />
          )}
        </dl>
      </SectionCard>

      <SectionCard title="Status History">
        <StatusHistoryList history={delivery.timeline} />
      </SectionCard>
    </div>
  );
}

/** One of the courier's deliveries: both stops, the parcel, the next steps and the history. */
export default function DeliveryDetailsPage() {
  const { parcelId = '' } = useParams();
  const result = useApiData(`courier-delivery:${parcelId}`, () => courierApi.getDelivery(parcelId));

  return (
    <section>
      <Link
        to={PATHS.courierHome}
        className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Today's deliveries
      </Link>

      {result.status === 'loading' && <LoadingState message="Loading the delivery." />}

      {result.status === 'error' && (
        <ErrorState
          title="Couldn't load this delivery"
          message={result.message}
          onAction={result.retry}
        />
      )}

      {result.status === 'success' && result.data === null && (
        <EmptyState
          icon={PackageSearch}
          title="Delivery not found"
          message="This delivery isn't assigned to you, or it no longer exists."
          action={
            <ButtonLink to={PATHS.courierHome} variant="outline">
              Back to today's deliveries
            </ButtonLink>
          }
        />
      )}

      {result.status === 'success' && result.data !== null && (
        <DeliveryView delivery={result.data} />
      )}
    </section>
  );
}
