import { useEffect, useRef, useState, type RefObject } from 'react';
import { barcodeDetectorClass, WANTED_FORMATS } from '../barcode';

export type ScannerState =
  /** Asking for the camera. */
  | 'starting'
  /** Camera on, looking for a code. */
  | 'scanning'
  /** The courier refused camera access. */
  | 'denied'
  /** No camera on this device. */
  | 'no-camera'
  /** Anything else went wrong. */
  | 'error';

/** How often a video frame is checked for a code. */
const SCAN_INTERVAL_MS = 300;

function stateFor(err: unknown): ScannerState {
  const name = err instanceof DOMException || err instanceof Error ? err.name : '';
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'denied';
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'no-camera';
  return 'error';
}

/**
 * Shows the back camera in `videoRef` and calls `onDetected` with the first code it reads, then
 * stops looking. The camera is switched off whenever `active` becomes false or the page closes.
 * Only call it with `active` true where `isScannerSupported()` holds. `restart()` tries again after a
 * code was read or the camera failed.
 */
export function useBarcodeScanner(
  videoRef: RefObject<HTMLVideoElement | null>,
  active: boolean,
  onDetected: (rawValue: string) => void,
): { state: ScannerState; restart: () => void } {
  const [state, setState] = useState<ScannerState>('starting');
  const [attempt, setAttempt] = useState(0);
  // The latest callback, so a new inline function doesn't restart the camera.
  const onDetectedRef = useRef(onDetected);
  useEffect(() => {
    onDetectedRef.current = onDetected;
  });

  useEffect(() => {
    const Detector = barcodeDetectorClass();
    if (!active || !Detector) return;
    let cancelled = false;
    let stream: MediaStream | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    // The <video> this run uses, so cleanup detaches the same element.
    const video = videoRef.current;

    async function start(DetectorClass: NonNullable<typeof Detector>) {
      try {
        const supported = await DetectorClass.getSupportedFormats();
        const formats = WANTED_FORMATS.filter((format) => supported.includes(format));
        const detector = new DetectorClass(formats.length > 0 ? { formats } : undefined);
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        });
        if (cancelled || !video) return;
        video.srcObject = stream;
        await video.play();
        if (cancelled) return;
        setState('scanning');

        const tick = async () => {
          if (cancelled) return;
          try {
            const found = (await detector.detect(video)).find((code) => code.rawValue.trim());
            if (found && !cancelled) {
              onDetectedRef.current(found.rawValue);
              return;
            }
          } catch {
            // A frame that can't be read yet (e.g. while the camera warms up): try the next one.
          }
          timer = setTimeout(tick, SCAN_INTERVAL_MS);
        };
        void tick();
      } catch (err) {
        if (!cancelled) setState(stateFor(err));
      }
    }

    void start(Detector);
    return () => {
      cancelled = true;
      clearTimeout(timer);
      stream?.getTracks().forEach((track) => track.stop());
      if (video) video.srcObject = null;
    };
  }, [active, attempt, videoRef]);

  return {
    state,
    restart: () => {
      setState('starting');
      setAttempt((n) => n + 1);
    },
  };
}
