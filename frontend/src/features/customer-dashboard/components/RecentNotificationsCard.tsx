import { ArrowRight, CircleCheck, CircleX, Info, Truck, type LucideIcon } from 'lucide-react';
import { useState } from 'react';
import { cn } from '../../../utils/cn';
import { formatRelative } from '../../shared-parcels/parcelDisplay';

/**
 * "Recent Notifications" panel of the customer dashboard.
 *
 * <p>SAMPLE DATA: notifications belong to the notification module (Member 10). Replace
 * {@link SAMPLE_NOTIFICATIONS} with their API when it exists.
 */

type Kind = 'transit' | 'delivered' | 'failed' | 'info';

interface SampleNotification {
  id: string;
  kind: Kind;
  title: string;
  message: string;
  hoursAgo: number;
}

const SAMPLE_NOTIFICATIONS: SampleNotification[] = [
  {
    id: '1',
    kind: 'transit',
    title: 'Parcel out for delivery',
    message: 'Your parcel SD2508220098 is out for delivery.',
    hoursAgo: 2,
  },
  {
    id: '2',
    kind: 'transit',
    title: 'In transit',
    message: 'Your parcel SD2508240012 is now in transit to Colombo.',
    hoursAgo: 24,
  },
  {
    id: '3',
    kind: 'delivered',
    title: 'Delivered successfully',
    message: 'Your parcel SD2508200045 has been delivered.',
    hoursAgo: 72,
  },
  {
    id: '4',
    kind: 'failed',
    title: 'Delivery failed',
    message: 'Delivery attempt for parcel SD2508150021 failed. Reason: Recipient not available.',
    hoursAgo: 168,
  },
  {
    id: '5',
    kind: 'info',
    title: 'Parcel picked up',
    message: 'Your parcel SD2508180176 has been picked up from the sender.',
    hoursAgo: 170,
  },
];

const KIND: Record<Kind, { icon: LucideIcon; tile: string; dot: string }> = {
  transit: { icon: Truck, tile: 'bg-blue-50 text-blue-600', dot: 'bg-blue-500' },
  delivered: { icon: CircleCheck, tile: 'bg-brand-50 text-brand-600', dot: 'bg-brand-600' },
  failed: { icon: CircleX, tile: 'bg-red-50 text-red-600', dot: 'bg-red-500' },
  info: { icon: Info, tile: 'bg-slate-100 text-slate-500', dot: 'bg-slate-400' },
};

export default function RecentNotificationsCard() {
  // Fixed when the card first renders, so the "x ago" labels stay stable between renders.
  const [now] = useState(() => Date.now());
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs sm:p-6">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">Recent Notifications</h2>
        <span className="inline-flex items-center gap-1 text-sm font-medium text-brand-700">
          View all
          <ArrowRight className="size-4" aria-hidden="true" />
        </span>
      </div>
      <ol>
        {SAMPLE_NOTIFICATIONS.map((item) => {
          const kind = KIND[item.kind];
          const Icon = kind.icon;
          return (
            <li
              key={item.id}
              className="relative flex gap-3 border-b border-slate-100 py-4 pl-4 last:border-b-0"
            >
              <span
                aria-hidden="true"
                className={cn('absolute top-6 left-0 size-1.5 rounded-full', kind.dot)}
              />
              <span
                className={cn(
                  'flex size-9 shrink-0 items-center justify-center rounded-full',
                  kind.tile,
                )}
              >
                <Icon className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-semibold text-slate-900">{item.title}</p>
                  <p className="shrink-0 text-xs text-slate-500">
                    {formatRelative(new Date(now - item.hoursAgo * 3_600_000).toISOString(), now)}
                  </p>
                </div>
                <p className="mt-1 text-sm text-slate-500">{item.message}</p>
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
