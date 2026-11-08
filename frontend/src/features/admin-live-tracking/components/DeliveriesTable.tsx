import { MapPin, Package } from 'lucide-react';
import { generatePath } from 'react-router';
import ButtonLink from '../../../components/ui/ButtonLink';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { cn } from '../../../utils/cn';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { statusLabel } from '../filters';
import { DELIVERY_DETAIL_PATH } from '../paths';
import type { LiveDeliveryRow } from '../types';

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

const NONE = <span className="text-slate-400">—</span>;

/**
 * The deliveries table. Rows in {@code highlighted} just changed status live and are tinted for a
 * moment.
 */
export default function DeliveriesTable({
  rows,
  highlighted,
}: {
  rows: LiveDeliveryRow[];
  highlighted: ReadonlySet<string>;
}) {
  const { formatShortDateTime } = useDateFormat();
  const th = 'px-4 py-3 text-left text-xs font-medium text-slate-500';
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[900px] text-sm">
        <caption className="sr-only">Deliveries, most recently updated first</caption>
        <thead className="border-b border-slate-200">
          <tr>
            <th scope="col" className={th}>
              Tracking No.
            </th>
            <th scope="col" className={th}>
              Recipient
            </th>
            <th scope="col" className={th}>
              Merchant
            </th>
            <th scope="col" className={th}>
              Courier
            </th>
            <th scope="col" className={th}>
              Destination
            </th>
            <th scope="col" className={th}>
              Status
            </th>
            <th scope="col" className={th}>
              Last Update
            </th>
            <th scope="col" className={th}>
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row) => (
            <tr
              key={row.trackingNumber}
              className={cn(
                'transition-colors duration-700',
                highlighted.has(row.trackingNumber) ? 'bg-amber-50' : 'hover:bg-slate-50',
              )}
            >
              <td className="px-4 py-3">
                <span className="flex items-center gap-3 font-medium text-slate-900">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-orange-50 text-orange-500">
                    <Package className="size-4" aria-hidden="true" />
                  </span>
                  #{row.trackingNumber}
                </span>
              </td>
              <td className="px-4 py-3">
                <span className="block text-slate-900">{row.recipientName}</span>
                <span className="block text-xs text-slate-500">{row.recipientPhone}</span>
              </td>
              <td className="px-4 py-3 text-slate-700">{row.merchantName ?? NONE}</td>
              <td className="px-4 py-3">
                {row.courierName ? (
                  <span className="flex items-center gap-2 text-slate-700">
                    <span
                      className="flex size-7 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700"
                      aria-hidden="true"
                    >
                      {initials(row.courierName)}
                    </span>
                    {row.courierName}
                  </span>
                ) : (
                  NONE
                )}
              </td>
              <td className="px-4 py-3">
                <span className="flex items-center gap-1.5 text-slate-700">
                  <MapPin className="size-4 text-slate-400" aria-hidden="true" />
                  {row.destinationCity}
                </span>
              </td>
              <td className="px-4 py-3">
                <StatusPill
                  visual={{ ...statusVisual(row.status), label: statusLabel(row.status) }}
                />
              </td>
              <td className="px-4 py-3 whitespace-nowrap text-slate-600">
                {formatShortDateTime(row.lastUpdatedAt)}
              </td>
              <td className="px-4 py-3 text-right">
                <ButtonLink
                  to={generatePath(DELIVERY_DETAIL_PATH, { trackingNumber: row.trackingNumber })}
                  variant="outline"
                  size="sm"
                  aria-label={`View delivery ${row.trackingNumber}`}
                >
                  View
                </ButtonLink>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
