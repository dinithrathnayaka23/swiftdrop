import type { ParcelStatusCode } from '../../types/tracking';
import { STATUS_META } from '../../utils/parcelStatus';
import type { MerchantOrderFilters } from './types';

/** Every parcel status, in lifecycle order, for the status chips. */
export const ORDER_STATUSES = Object.keys(STATUS_META) as ParcelStatusCode[];

/** How long to wait after the last key press before searching. */
export const SEARCH_DELAY_MS = 300;

/** Longest search the server accepts. */
export const MAX_SEARCH_LENGTH = 100;

const DAY = /^\d{4}-\d{2}-\d{2}$/;

function isStatus(value: string | null): value is ParcelStatusCode {
  return value !== null && (ORDER_STATUSES as string[]).includes(value);
}

export function hasFilters(filters: MerchantOrderFilters): boolean {
  return Boolean(filters.search || filters.status || filters.from || filters.to);
}

/** Zero-based page from "?page=" (one-based in the URL, as people read it). */
export function pageFromParams(params: URLSearchParams): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 1 ? page - 1 : 0;
}

/** The filters in the address. Values that can't be valid are ignored. */
export function filtersFromParams(params: URLSearchParams): MerchantOrderFilters {
  const status = params.get('status');
  const from = params.get('from');
  const to = params.get('to');
  return {
    search: params.get('search')?.trim() || undefined,
    status: isStatus(status) ? status : undefined,
    from: from && DAY.test(from) ? from : undefined,
    to: to && DAY.test(to) ? to : undefined,
  };
}

/** Address parameters for these filters and zero-based page; empty values are left out. */
export function toParams(filters: MerchantOrderFilters, page = 0): Record<string, string> {
  const params: Record<string, string> = {};
  if (filters.search) params.search = filters.search;
  if (filters.status) params.status = filters.status;
  if (filters.from) params.from = filters.from;
  if (filters.to) params.to = filters.to;
  if (page > 0) params.page = String(page + 1);
  return params;
}
