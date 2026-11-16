/**
 * The browser's built-in barcode reader (Barcode Detection API: Chrome and Edge on Android, among
 * others). It isn't in TypeScript's DOM types yet, so the little we use is declared here. No npm
 * package is needed; where it's missing, the courier types the tracking number instead.
 */

export interface DetectedBarcode {
  rawValue: string;
  format: string;
}

export interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>;
}

export interface BarcodeDetectorClass {
  new (options?: { formats?: string[] }): BarcodeDetectorLike;
  getSupportedFormats(): Promise<string[]>;
}

/** Label formats worth looking for: QR codes and the usual 1D shipping barcodes. */
export const WANTED_FORMATS = ['qr_code', 'code_128', 'code_39', 'data_matrix', 'ean_13'];

export function barcodeDetectorClass(): BarcodeDetectorClass | null {
  return (globalThis as { BarcodeDetector?: BarcodeDetectorClass }).BarcodeDetector ?? null;
}

/** Whether this browser can scan with the camera at all. */
export function isScannerSupported(): boolean {
  return (
    barcodeDetectorClass() !== null &&
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function'
  );
}

/**
 * The tracking number in a scanned code. Usually the code is the number itself; a QR code may hold a
 * tracking link instead (…/track/SD2611120001 or …?trackingNumber=SD2611120001), so that is read too.
 */
export function trackingNumberFrom(raw: string): string {
  const value = raw.trim();
  if (/^https?:\/\//i.test(value)) {
    try {
      const url = new URL(value);
      const param = url.searchParams.get('trackingNumber') ?? url.searchParams.get('tn');
      const last = url.pathname.split('/').filter(Boolean).pop();
      return (param ?? last ?? '').replace(/\s+/g, '').toUpperCase();
    } catch {
      // Not a real URL after all: fall through and use the text as it is.
    }
  }
  return value.replace(/\s+/g, '').toUpperCase();
}
