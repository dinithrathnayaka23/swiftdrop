import { generatePath } from 'react-router';
import { parcelsApi } from '../../api/parcels';
import { PATHS } from '../../routes/paths';

/** Saves {@code blob} as a file through a temporary link. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** Downloads a parcel's receipt as receipt-<trackingNumber>.pdf (.txt for the mock sample). */
export async function downloadReceipt(parcelId: string, trackingNumber: string): Promise<void> {
  const blob = await parcelsApi.downloadReceipt(parcelId);
  const extension = blob.type === 'application/pdf' ? 'pdf' : 'txt';
  saveBlob(blob, `receipt-${trackingNumber}.${extension}`);
}

/** Public tracking page link for a parcel (shareable, no sign-in needed). */
export function trackingLink(trackingNumber: string): string {
  return window.location.origin + generatePath(PATHS.trackResult, { trackingNumber });
}
