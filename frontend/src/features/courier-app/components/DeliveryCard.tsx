import { ChevronRight, MapPin, Package } from 'lucide-react';
import { generatePath, Link } from 'react-router';
import { PATHS } from '../../../routes/paths';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { PARCEL_TYPE_LABELS } from '../../shared-parcels/parcelDisplay';
import type { CourierDelivery } from '../types';
import { formatStopAddress } from '../format';

/** One delivery in Today's Deliveries; the whole card opens Delivery Details. */
export default function DeliveryCard({ delivery }: { delivery: CourierDelivery }) {
  const waitingForPickup = delivery.pickup !== null;
  const stop = delivery.pickup ?? delivery.dropoff;
  return (
    <Link
      to={generatePath(PATHS.courierDeliveryDetail, { parcelId: delivery.parcelId })}
      className="block rounded-2xl border border-slate-200 bg-white p-4 shadow-xs transition-colors hover:border-brand-300 focus-visible:outline-2 focus-visible:outline-brand-600"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="font-mono text-sm font-semibold text-slate-900">{delivery.trackingNumber}</p>
        <StatusPill visual={statusVisual(delivery.currentStatus)} />
      </div>

      <p className="mt-3 text-xs font-semibold tracking-wide text-slate-500 uppercase">
        {waitingForPickup ? 'Pick up from' : 'Deliver to'}
      </p>
      <p className="text-base font-semibold text-slate-900">{stop.contactName}</p>
      <p className="mt-1 flex gap-2 text-sm text-slate-600">
        <MapPin className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
        <span>{formatStopAddress(stop)}</span>
      </p>

      <div className="mt-3 flex items-center justify-between gap-3 text-sm text-slate-500">
        <span className="flex items-center gap-2">
          <Package className="size-4 text-slate-400" aria-hidden="true" />
          {PARCEL_TYPE_LABELS[delivery.parcelType]} · {delivery.weightKg} kg
        </span>
        <ChevronRight className="size-5 text-slate-400" aria-hidden="true" />
      </div>
      {delivery.deliveryInstructions && (
        <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {delivery.deliveryInstructions}
        </p>
      )}
    </Link>
  );
}
