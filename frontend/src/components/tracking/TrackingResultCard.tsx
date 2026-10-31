import {
  CircleCheck,
  CircleX,
  Clock,
  TriangleAlert,
  Truck,
  Undo2,
  type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import type { ParcelStatusCode, TrackingResult } from '../../types/tracking';
import { cn } from '../../utils/cn';
import { formatDateTime } from '../../utils/date';
import { buildTimeline, STATUS_META } from '../../utils/parcelStatus';
import ParcelStatusBadge from './ParcelStatusBadge';
import TrackingTimeline from './TrackingTimeline';

const STATUS_ICON: Record<ParcelStatusCode, { icon: LucideIcon; className: string }> = {
  BOOKED: { icon: Clock, className: 'bg-slate-100 text-slate-500' },
  ASSIGNED: { icon: Clock, className: 'bg-blue-50 text-blue-600' },
  PICKED_UP: { icon: Truck, className: 'bg-blue-50 text-blue-600' },
  IN_TRANSIT: { icon: Truck, className: 'bg-blue-50 text-blue-600' },
  OUT_FOR_DELIVERY: { icon: Truck, className: 'bg-amber-50 text-amber-600' },
  DELIVERED: { icon: CircleCheck, className: 'bg-brand-50 text-brand-600' },
  FAILED_ATTEMPT: { icon: TriangleAlert, className: 'bg-red-50 text-red-600' },
  RETURNED: { icon: Undo2, className: 'bg-slate-100 text-slate-600' },
  CANCELLED: { icon: CircleX, className: 'bg-red-50 text-red-600' },
};

function InfoPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-slate-200 p-5">
      <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export default function TrackingResultCard({ result }: { result: TrackingResult }) {
  const meta = STATUS_META[result.currentStatus];
  const { icon: StatusIcon, className: iconClass } = STATUS_ICON[result.currentStatus];

  const details: [string, string][] = [
    ['From', result.fromCity],
    ['To', result.toCity],
    ['Weight', `${result.weightKg} kg`],
    ['Booked on', formatDateTime(result.bookedAt)],
  ];

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          #{result.trackingNumber}
        </h1>
        <ParcelStatusBadge status={result.currentStatus} />
      </div>
      <p className="mt-1.5 text-sm text-slate-500">
        Last updated: {formatDateTime(result.lastUpdatedAt)}
      </p>

      <div className="mt-8">
        <TrackingTimeline steps={buildTimeline(result)} />
      </div>

      <div className="mt-8 grid gap-4 md:grid-cols-2">
        <InfoPanel title="Parcel Information">
          <dl className="space-y-3 text-sm">
            {details.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-4">
                <dt className="text-slate-500">{label}</dt>
                <dd className="text-right font-medium text-slate-900">{value}</dd>
              </div>
            ))}
          </dl>
        </InfoPanel>

        <InfoPanel title="Current Status">
          <div className="flex items-center gap-3">
            <span
              className={cn('flex size-11 items-center justify-center rounded-full', iconClass)}
            >
              <StatusIcon className="size-5" aria-hidden="true" />
            </span>
            <p className="text-base font-semibold text-slate-900">{meta.label}</p>
          </div>
          <p className="mt-3 text-sm leading-6 text-slate-500">{meta.description}</p>
        </InfoPanel>
      </div>
    </div>
  );
}
