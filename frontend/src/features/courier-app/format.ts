import type { CourierStop } from './types';

/** "12 Galle Road, Colombo 03, Colombo" */
export function formatStopAddress(stop: CourierStop): string {
  return [stop.addressLine1, stop.addressLine2, stop.city, stop.district, stop.postalCode]
    .filter(Boolean)
    .join(', ');
}
