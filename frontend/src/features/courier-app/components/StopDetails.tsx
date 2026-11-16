import { MapPin, Phone } from 'lucide-react';
import { formatStopAddress } from '../format';
import type { CourierStop } from '../types';

interface StopDetailsProps {
  label: string;
  stop: CourierStop;
}

/** Who to meet and where, with a one-tap call button. */
export default function StopDetails({ label, stop }: StopDetailsProps) {
  return (
    <div>
      <p className="text-xs font-semibold tracking-wide text-slate-500 uppercase">{label}</p>
      <p className="mt-1 text-base font-semibold text-slate-900">{stop.contactName}</p>
      <p className="mt-1 flex gap-2 text-sm text-slate-600">
        <MapPin className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
        <span>{formatStopAddress(stop)}</span>
      </p>
      <a
        href={`tel:${stop.contactPhone}`}
        className="mt-3 inline-flex min-h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-800 hover:bg-slate-50"
        aria-label={`Call ${stop.contactName}`}
      >
        <Phone className="size-4" aria-hidden="true" />
        {stop.contactPhone}
      </a>
    </div>
  );
}
