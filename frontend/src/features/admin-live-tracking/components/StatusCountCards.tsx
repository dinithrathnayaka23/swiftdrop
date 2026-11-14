import { CircleAlert, CircleCheck, Clock, RefreshCw, Truck, type LucideIcon } from 'lucide-react';
import type { ParcelStatusCode } from '../../../types/tracking';
import { cn } from '../../../utils/cn';
import type { LiveStatusCounts } from '../types';

interface Card {
  label: string;
  icon: LucideIcon;
  /** Statuses added up for this card; empty = every status (the total). */
  statuses: ParcelStatusCode[];
  tile: string;
  chip: string;
}

const CARDS: Card[] = [
  {
    label: 'Total Deliveries',
    icon: Truck,
    statuses: [],
    tile: 'bg-blue-50 text-blue-600',
    chip: '',
  },
  {
    label: 'In Transit',
    icon: RefreshCw,
    statuses: ['PICKED_UP', 'IN_TRANSIT'],
    tile: 'bg-blue-50 text-blue-600',
    chip: 'bg-blue-50 text-blue-700',
  },
  {
    label: 'Out for Delivery',
    icon: Clock,
    statuses: ['OUT_FOR_DELIVERY'],
    tile: 'bg-amber-50 text-amber-600',
    chip: 'bg-amber-50 text-amber-700',
  },
  {
    label: 'Delivered',
    icon: CircleCheck,
    statuses: ['DELIVERED'],
    tile: 'bg-brand-50 text-brand-600',
    chip: 'bg-brand-50 text-brand-700',
  },
  {
    label: 'Failed',
    icon: CircleAlert,
    // Failed attempts (waiting for the dispatcher) and failed deliveries, like the Parcels Failed tab.
    statuses: ['FAILED_ATTEMPT', 'FAILED'],
    tile: 'bg-red-50 text-red-600',
    chip: 'bg-red-50 text-red-700',
  },
];

function sum(counts: LiveStatusCounts, statuses: ParcelStatusCode[]): number {
  const codes = statuses.length > 0 ? statuses : (Object.keys(counts) as ParcelStatusCode[]);
  return codes.reduce((total, code) => total + (counts[code] ?? 0), 0);
}

/**
 * The five count cards: total, then in transit (picked up + in transit), out for delivery,
 * delivered and failed, each with its share of the total.
 */
export default function StatusCountCards({ counts }: { counts: LiveStatusCounts | null }) {
  const total = counts ? sum(counts, []) : 0;
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
      {CARDS.map(({ label, icon: Icon, statuses, tile, chip }) => {
        const value = counts ? sum(counts, statuses) : null;
        const share = value !== null && total > 0 ? Math.round((value / total) * 100) : 0;
        return (
          <div
            key={label}
            className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs"
          >
            <span
              className={cn('flex size-12 shrink-0 items-center justify-center rounded-full', tile)}
            >
              <Icon className="size-6" strokeWidth={1.8} aria-hidden="true" />
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm text-slate-600">{label}</p>
              <p className="mt-1 flex items-center gap-2">
                {value === null ? (
                  <span className="inline-block h-7 w-10 animate-pulse rounded bg-slate-200" />
                ) : (
                  <span className="text-2xl font-bold text-slate-900">{value}</span>
                )}
                {value !== null && statuses.length > 0 && (
                  <span className={cn('rounded-md px-1.5 py-0.5 text-xs font-semibold', chip)}>
                    {share}%
                  </span>
                )}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}
