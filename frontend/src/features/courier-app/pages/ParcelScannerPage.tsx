import { CameraOff, ScanLine, Search } from 'lucide-react';
import { useRef, useState, type FormEvent } from 'react';
import { generatePath, useNavigate } from 'react-router';
import { getErrorMessage } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import TextField from '../../../components/ui/TextField';
import { PATHS } from '../../../routes/paths';
import SectionCard from '../../shared-parcels/components/SectionCard';
import { courierApi } from '../api';
import { isScannerSupported, trackingNumberFrom } from '../barcode';
import { useBarcodeScanner, type ScannerState } from '../hooks/useBarcodeScanner';

const CAMERA_PROBLEMS: Partial<Record<ScannerState, string>> = {
  denied:
    'Camera access is blocked. Allow the camera in your browser settings, or type the number.',
  'no-camera': 'No camera was found on this device. Type the tracking number instead.',
  error: "The camera couldn't be started. Type the tracking number instead.",
};

type Lookup =
  | { status: 'idle' }
  | { status: 'looking'; trackingNumber: string }
  | { status: 'not-found'; trackingNumber: string }
  | { status: 'error'; message: string };

function CameraView({
  onDetected,
  paused,
}: {
  onDetected: (rawValue: string) => void;
  paused: boolean;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const { state, restart } = useBarcodeScanner(videoRef, !paused, onDetected);
  const problem = CAMERA_PROBLEMS[state];

  if (problem) {
    return (
      <div className="flex flex-col gap-3">
        <Alert tone="warning" title="Can't use the camera">
          {problem}
        </Alert>
        <Button variant="outline" onClick={restart}>
          Try the camera again
        </Button>
      </div>
    );
  }

  return (
    <div className="relative overflow-hidden rounded-xl bg-slate-900">
      <video
        ref={videoRef}
        className="aspect-[4/3] w-full object-cover"
        muted
        playsInline
        aria-label="Camera view"
      />
      {/* The frame to aim the label at. */}
      <div
        className="pointer-events-none absolute inset-x-8 top-1/2 h-24 -translate-y-1/2 rounded-lg border-2 border-white/80"
        aria-hidden="true"
      />
      <p className="absolute inset-x-0 bottom-0 bg-slate-900/60 px-3 py-2 text-center text-sm text-white">
        {state === 'scanning' ? 'Point the camera at the label' : 'Starting the camera…'}
      </p>
    </div>
  );
}

/** Opens one of the courier's deliveries by scanning its label, or by typing the tracking number. */
export default function ParcelScannerPage() {
  const navigate = useNavigate();
  const [typed, setTyped] = useState('');
  const [lookup, setLookup] = useState<Lookup>({ status: 'idle' });
  const scannerSupported = isScannerSupported();

  async function find(trackingNumber: string) {
    if (!trackingNumber) return;
    setLookup({ status: 'looking', trackingNumber });
    try {
      const delivery = await courierApi.findByTrackingNumber(trackingNumber);
      if (delivery) {
        navigate(generatePath(PATHS.courierDeliveryDetail, { parcelId: delivery.parcelId }));
      } else {
        setLookup({ status: 'not-found', trackingNumber });
      }
    } catch (err) {
      setLookup({ status: 'error', message: getErrorMessage(err) });
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void find(trackingNumberFrom(typed));
  }

  const looking = lookup.status === 'looking';
  // After a code is read the camera stops until the courier scans again.
  const cameraPaused = lookup.status !== 'idle';

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Scan Parcel</h1>
        <p className="mt-1 text-sm text-slate-500">
          Scan the label to open the delivery, or type its tracking number.
        </p>
      </div>

      {scannerSupported ? (
        <CameraView
          onDetected={(raw) => void find(trackingNumberFrom(raw))}
          paused={cameraPaused}
        />
      ) : (
        <Alert tone="info" title="Scanning isn't available in this browser">
          <span className="inline-flex items-center gap-1">
            <CameraOff className="size-4" aria-hidden="true" />
            Type the tracking number below. Chrome on Android can scan labels.
          </span>
        </Alert>
      )}

      {lookup.status === 'looking' && (
        <Alert tone="info">Looking up {lookup.trackingNumber}…</Alert>
      )}
      {lookup.status === 'not-found' && (
        <Alert tone="warning" title="Not one of your deliveries">
          {lookup.trackingNumber} isn't assigned to you, or the number is wrong.
        </Alert>
      )}
      {lookup.status === 'error' && (
        <Alert tone="error" title="Couldn't look up the parcel">
          {lookup.message}
        </Alert>
      )}
      {scannerSupported && cameraPaused && !looking && (
        <Button variant="outline" onClick={() => setLookup({ status: 'idle' })}>
          <ScanLine className="size-4" aria-hidden="true" />
          Scan again
        </Button>
      )}

      <SectionCard title="Type the tracking number">
        <form onSubmit={submit} className="flex flex-col gap-3" noValidate>
          <TextField
            label="Tracking number"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            placeholder="SD2611120001"
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck={false}
            inputMode="text"
          />
          <Button type="submit" fullWidth loading={looking} disabled={typed.trim() === ''}>
            <Search className="size-4" aria-hidden="true" />
            Find Delivery
          </Button>
        </form>
      </SectionCard>
    </section>
  );
}
