import type { ParcelStatusCode } from '../../types/tracking';
import { STATUS_META } from '../../utils/parcelStatus';
import type { LiveDeliveryFilters } from './types';

/** The filters the table is showing ('' = not set). */
export interface AppliedFilters {
  search: string;
  status: ParcelStatusCode | '';
  bookedFrom: string;
  bookedTo: string;
}

export const NO_FILTERS: AppliedFilters = { search: '', status: '', bookedFrom: '', bookedTo: '' };

export const PAGE_SIZES = [10, 20, 50] as const;

/** "Pending" in the table design is a booked parcel (CREATED). */
export function statusLabel(status: ParcelStatusCode): string {
  return status === 'CREATED' ? 'Pending' : STATUS_META[status].label;
}

/** The API query for these filters and page. */
export function toQuery(filters: AppliedFilters, page: number, size: number): LiveDeliveryFilters {
  return {
    search: filters.search || undefined,
    status: filters.status || undefined,
    bookedFrom: filters.bookedFrom || undefined,
    bookedTo: filters.bookedTo || undefined,
    page,
    size,
  };
}
