import { X } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import LoadingState from '../../../components/feedback/LoadingState';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { courierApi } from '../api';

interface ProofPhotoViewerProps {
  /** The delivery's `proofPhotoUrl`. */
  url: string;
  title: string;
  onClose: () => void;
}

type PhotoState =
  { status: 'loading' } | { status: 'ready'; src: string } | { status: 'error'; message: string };

/**
 * A proof photo in a dialog. The photo endpoint needs the sign-in token, which an <img src> can't
 * send, so the photo is fetched through the API client and shown from a temporary object URL.
 */
export default function ProofPhotoViewer({ url, title, onClose }: ProofPhotoViewerProps) {
  const titleId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);
  const [photo, setPhoto] = useState<PhotoState>({ status: 'loading' });

  useEffect(() => {
    let objectUrl: string | null = null;
    let active = true;
    courierApi.getProofPhoto(url).then(
      (blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setPhoto({ status: 'ready', src: objectUrl });
      },
      (err: unknown) => {
        if (active) setPhoto({ status: 'error', message: getErrorMessage(err) });
      },
    );
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url]);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-lg rounded-2xl bg-white p-4 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 id={titleId} className="font-mono text-base font-semibold text-slate-900">
            {title}
          </h2>
          <Button
            ref={closeRef}
            variant="ghost"
            size="sm"
            onClick={onClose}
            aria-label="Close photo"
          >
            <X className="size-4" aria-hidden="true" />
          </Button>
        </div>
        {photo.status === 'loading' && <LoadingState message="Loading the photo." />}
        {photo.status === 'error' && (
          <Alert tone="error" title="Couldn't load the photo">
            {photo.message}
          </Alert>
        )}
        {photo.status === 'ready' && (
          <img
            src={photo.src}
            alt={`Proof of delivery for ${title}`}
            className="max-h-[70vh] w-full rounded-xl object-contain"
          />
        )}
      </div>
    </div>
  );
}
