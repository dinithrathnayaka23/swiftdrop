import { isScannerSupported, trackingNumberFrom } from './barcode';

describe('trackingNumberFrom', () => {
  it('reads a plain label, ignoring spaces and case', () => {
    expect(trackingNumberFrom(' sd 2611 1200 01\n')).toBe('SD2611120001');
  });

  it('reads the number out of a tracking link in a QR code', () => {
    expect(trackingNumberFrom('https://swiftdrop.lk/track/SD2611120001')).toBe('SD2611120001');
    expect(trackingNumberFrom('https://swiftdrop.lk/track?trackingNumber=sd2611120002')).toBe(
      'SD2611120002',
    );
  });
});

describe('isScannerSupported', () => {
  afterEach(() => {
    delete (globalThis as { BarcodeDetector?: unknown }).BarcodeDetector;
  });

  it('needs both the barcode detector and the camera', () => {
    expect(isScannerSupported()).toBe(false);

    (globalThis as { BarcodeDetector?: unknown }).BarcodeDetector = class {};
    const hadCamera = typeof navigator.mediaDevices?.getUserMedia === 'function';
    expect(isScannerSupported()).toBe(hadCamera);
  });
});
