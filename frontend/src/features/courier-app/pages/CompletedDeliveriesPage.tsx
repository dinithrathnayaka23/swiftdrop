import { Camera, ClipboardCheck } from 'lucide-react';
import { useCallback, useState } from 'react';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Button from '../../../components/ui/Button';
import SelectField from '../../../components/ui/SelectField';
import TextField from '../../../components/ui/TextField';
import { useDateFormat } from '../../../hooks/useDateFormat';
import Pagination from '../../shared-parcels/components/Pagination';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { useApiData } from '../../shared-parcels/useApiData';
import { courierApi } from '../api';
import ProofPhotoViewer from '../components/ProofPhotoViewer';
import type { CompletedDelivery, DeliveryOutcome } from '../types';

const OUTCOMES = [
  { value: '', label: 'All' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'FAILED_ATTEMPT', label: 'Failed attempt' },
];

function Row({ label, value }: { label: string; value: string }) {
  return (
    <p className="text-sm text-slate-600">
      <span className="font-medium text-slate-900">{label}:</span> {value}
    </p>
  );
}

function CompletedCard({
  delivery,
  onViewPhoto,
}: {
  delivery: CompletedDelivery;
  onViewPhoto: (delivery: CompletedDelivery) => void;
}) {
  const { formatDateTime } = useDateFormat();
  const failed = delivery.outcome === 'FAILED_ATTEMPT';
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="font-mono text-base font-semibold text-slate-900">
            {delivery.trackingNumber}
          </h2>
          <p className="text-xs text-slate-500">{formatDateTime(delivery.completedAt)}</p>
        </div>
        <StatusPill visual={statusVisual(delivery.outcome)} />
      </div>
      <div className="mt-3 flex flex-col gap-1">
        <p className="text-sm text-slate-900">{delivery.recipientName}</p>
        <p className="text-sm text-slate-500">
          {delivery.dropoffArea}, {delivery.dropoffCity}
        </p>
        {failed ? (
          <>
            <Row label="Reason" value={delivery.failureReason ?? 'Not given'} />
            {delivery.note && <Row label="Note" value={delivery.note} />}
          </>
        ) : (
          delivery.receivedBy && <Row label="Received by" value={delivery.receivedBy} />
        )}
      </div>
      {delivery.proofPhotoUrl && (
        <Button
          variant="outline"
          size="sm"
          className="mt-3"
          onClick={() => onViewPhoto(delivery)}
          aria-label={`View photo for ${delivery.trackingNumber}`}
        >
          <Camera className="size-4" aria-hidden="true" />
          View photo
        </Button>
      )}
    </article>
  );
}

/** The courier's deliveries and failed attempts, newest first, with filters and pages. */
export default function CompletedDeliveriesPage() {
  const [outcome, setOutcome] = useState<DeliveryOutcome | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [photoOf, setPhotoOf] = useState<CompletedDelivery | null>(null);
  const closePhoto = useCallback(() => setPhotoOf(null), []);

  // Stop before asking the server for an impossible range (it would answer 400).
  const rangeError = from && to && to < from ? 'The end date is before the start date.' : undefined;
  const filter = {
    outcome: outcome || undefined,
    from: from || undefined,
    to: to || undefined,
    page,
  };
  const result = useApiData(`courier-completed:${JSON.stringify(filter)}`, () =>
    rangeError ? Promise.resolve(null) : courierApi.getCompleted(filter),
  );

  /** Every filter change starts again at the first page. */
  function change<T>(set: (value: T) => void) {
    return (value: T) => {
      set(value);
      setPage(0);
    };
  }

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Completed Deliveries</h1>
        <p className="mt-1 text-sm text-slate-500">
          Your deliveries and failed attempts, newest first. Without dates: the last 7 days.
        </p>
      </div>

      <SectionCard>
        <div className="grid gap-3 sm:grid-cols-3">
          <SelectField
            label="Outcome"
            options={OUTCOMES}
            value={outcome}
            onChange={(e) => change(setOutcome)(e.target.value as DeliveryOutcome | '')}
          />
          <TextField
            label="From"
            type="date"
            value={from}
            onChange={(e) => change(setFrom)(e.target.value)}
          />
          <TextField
            label="To"
            type="date"
            value={to}
            onChange={(e) => change(setTo)(e.target.value)}
            error={rangeError}
          />
        </div>
      </SectionCard>

      {result.status === 'loading' && <LoadingState message="Loading your completed deliveries." />}

      {result.status === 'error' && (
        <ErrorState
          title="Couldn't load your completed deliveries"
          message={result.message}
          onAction={result.retry}
        />
      )}

      {result.status === 'success' && result.data && result.data.content.length === 0 && (
        <EmptyState
          icon={ClipboardCheck}
          title="Nothing here yet"
          message="Deliveries and failed attempts you complete show up here."
          className="rounded-2xl border border-slate-200 bg-white"
        />
      )}

      {result.status === 'success' && result.data && result.data.content.length > 0 && (
        <>
          <p className="text-sm text-slate-500">
            {result.data.page.totalElements}{' '}
            {result.data.page.totalElements === 1 ? 'delivery' : 'deliveries'}
          </p>
          <ul className="flex flex-col gap-3">
            {result.data.content.map((delivery) => (
              <li key={`${delivery.parcelId}-${delivery.completedAt}`}>
                <CompletedCard delivery={delivery} onViewPhoto={setPhotoOf} />
              </li>
            ))}
          </ul>
          {result.data.page.totalPages > 1 && (
            <div className="flex justify-center">
              <Pagination
                page={result.data.page.number}
                totalPages={result.data.page.totalPages}
                onChange={setPage}
              />
            </div>
          )}
        </>
      )}

      {photoOf?.proofPhotoUrl && (
        <ProofPhotoViewer
          url={photoOf.proofPhotoUrl}
          title={photoOf.trackingNumber}
          onClose={closePhoto}
        />
      )}
    </section>
  );
}
