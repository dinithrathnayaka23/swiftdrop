import { CircleCheck, CircleX, Clock, Mail, Phone, Truck, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import type { ParcelStatusCode, TrackingResult } from '../../types/tracking';
import { cn } from '../../utils/cn';
import { useDateFormat } from '../../hooks/useDateFormat';
import { useSettings } from '../../hooks/useSettings';
import { isFailureStatus } from '../../utils/parcelStatus';
import Alert from '../ui/Alert';
import ParcelStatusBadge from './ParcelStatusBadge';
import TrackingTimeline from './TrackingTimeline';
import { buildTrackingTimeline, TRACKING_STATUS_TEXT } from './trackingSteps';

const STATUS_ICON: Record<ParcelStatusCode, { icon: LucideIcon; className: string }> = {
  CREATED: { icon: Clock, className: 'bg-slate-100 text-slate-500' },
  PICKED_UP: { icon: Truck, className: 'bg-blue-50 text-blue-600' },
  IN_TRANSIT: { icon: Truck, className: 'bg-blue-50 text-blue-600' },
  OUT_FOR_DELIVERY: { icon: Truck, className: 'bg-amber-50 text-amber-600' },
  DELIVERED: { icon: CircleCheck, className: 'bg-brand-50 text-brand-600' },
  FAILED_ATTEMPT: { icon: CircleX, className: 'bg-red-50 text-red-600' },
  FAILED: { icon: CircleX, className: 'bg-red-50 text-red-600' },
};

function InfoPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-slate-200 p-5">
      <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}

const CONTACT_LINK =
  'inline-flex items-center gap-2 rounded-sm font-medium text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600';

/** Support contact from the System Settings, shown when a delivery failed. */
function SupportContact() {
  const { contactEmail, contactPhone } = useSettings().settings;
  return (
    <div className="mt-4 rounded-lg bg-slate-50 p-4 text-sm">
      <p className="font-medium text-slate-900">Need help with this delivery?</p>
      <p className="mt-1 text-slate-500">
        Contact our support team and quote your tracking number.
      </p>
      <ul className="mt-3 space-y-2">
        <li>
          <a href={`mailto:${contactEmail}`} className={CONTACT_LINK}>
            <Mail className="size-4" aria-hidden="true" />
            {contactEmail}
          </a>
        </li>
        {contactPhone && (
          <li>
            <a href={`tel:${contactPhone.replace(/\s+/g, '')}`} className={CONTACT_LINK}>
              <Phone className="size-4" aria-hidden="true" />
              {contactPhone}
            </a>
          </li>
        )}
      </ul>
    </div>
  );
}

export default function TrackingResultCard({
  result,
  live,
}: {
  result: TrackingResult;
  /** Shown at the end of the header row, e.g. the live-updates badge. */
  live?: ReactNode;
}) {
  const { formatDateTime } = useDateFormat();
  const statusText = TRACKING_STATUS_TEXT[result.currentStatus];
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
        {live && <span className="ml-auto">{live}</span>}
      </div>
      <p className="mt-1.5 text-sm text-slate-500">
        Last updated: {formatDateTime(result.lastUpdatedAt)}
      </p>

      {result.currentStatus === 'CREATED' && (
        <Alert tone="info" title="Waiting for pickup" className="mt-6">
          Delivery progress will show here once a courier has collected the parcel.
        </Alert>
      )}

      <div className="mt-8">
        <TrackingTimeline steps={buildTrackingTimeline(result)} />
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
            <p className="text-base font-semibold text-slate-900">{statusText.title}</p>
          </div>
          <p className="mt-3 text-sm leading-6 text-slate-500">{statusText.description}</p>
          {isFailureStatus(result.currentStatus) && <SupportContact />}
        </InfoPanel>
      </div>
    </div>
  );
}
