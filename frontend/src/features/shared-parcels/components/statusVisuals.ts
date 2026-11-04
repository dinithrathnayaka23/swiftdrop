import {
  CircleCheck,
  CircleX,
  Clock,
  Package,
  PackageCheck,
  Truck,
  UserCheck,
  type LucideIcon,
} from 'lucide-react';
import type { DispatchState } from '../../../types/parcel';
import type { ParcelStatusCode } from '../../../types/tracking';
import { STATUS_META, type StatusTone } from '../../../utils/parcelStatus';

export interface Visual {
  label: string;
  icon: LucideIcon;
  tone: StatusTone;
}

const STATUS_ICONS: Record<ParcelStatusCode, LucideIcon> = {
  CREATED: Package,
  PICKED_UP: PackageCheck,
  IN_TRANSIT: Truck,
  OUT_FOR_DELIVERY: Truck,
  DELIVERED: CircleCheck,
  FAILED: CircleX,
};

export function statusVisual(status: ParcelStatusCode): Visual {
  return {
    label: STATUS_META[status].label,
    icon: STATUS_ICONS[status],
    tone: STATUS_META[status].tone,
  };
}

const DISPATCH_VISUALS: Record<DispatchState, Visual> = {
  UNASSIGNED: { label: 'Unassigned', icon: Clock, tone: 'warning' },
  ASSIGNED: { label: 'Assigned', icon: UserCheck, tone: 'info' },
};

/**
 * A CREATED parcel without a courier. Until dispatch reports assignments (dispatchState null),
 * every CREATED parcel counts as unassigned, as in the backend's tab counts.
 */
export function isUnassigned(
  status: ParcelStatusCode,
  dispatchState?: DispatchState | null,
): boolean {
  return status === 'CREATED' && dispatchState !== 'ASSIGNED';
}

/** Admin view: a CREATED parcel shows whether dispatch has assigned it; later statuses show as usual. */
export function adminStatusVisual(
  status: ParcelStatusCode,
  dispatchState?: DispatchState | null,
): Visual {
  if (status === 'CREATED') {
    return DISPATCH_VISUALS[isUnassigned(status, dispatchState) ? 'UNASSIGNED' : 'ASSIGNED'];
  }
  return statusVisual(status);
}

/** Pill colours per tone. */
export const TONE_PILL: Record<StatusTone, string> = {
  neutral: 'bg-slate-100 text-slate-700',
  info: 'bg-blue-50 text-blue-700',
  success: 'bg-brand-50 text-brand-700',
  warning: 'bg-amber-50 text-amber-700',
  danger: 'bg-red-50 text-red-700',
};

/** Round icon tile colours per tone (stat cards, history icons). */
export const TONE_TILE: Record<StatusTone, string> = {
  neutral: 'bg-slate-100 text-slate-600',
  info: 'bg-blue-50 text-blue-600',
  success: 'bg-brand-50 text-brand-600',
  warning: 'bg-amber-50 text-amber-600',
  danger: 'bg-red-50 text-red-600',
};
