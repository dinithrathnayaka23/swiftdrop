import { ArrowLeft, Check } from 'lucide-react';
import { useEffect, useState } from 'react';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import Spinner from '../../../components/ui/Spinner';
import { bookingApi } from '../../../api/booking';
import { getErrorMessage } from '../../../api/errors';
import type { BookingConfirmation, BookingRequest, FeeEstimate } from '../../../types/booking';

function formatLkr(amount: number): string {
  return new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' }).format(amount);
}

interface ReviewStepProps {
  booking: BookingRequest;
  onBack: () => void;
  onBooked: (confirmation: BookingConfirmation) => void;
}

export default function ReviewStep({ booking, onBack, onBooked }: ReviewStepProps) {
  const [estimate, setEstimate] = useState<FeeEstimate | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    bookingApi
      .estimate(booking)
      .then((result) => {
        if (!active) return;
        setEstimate(result);
        setLoadError(null);
      })
      .catch((err: unknown) => {
        if (active) setLoadError(getErrorMessage(err));
      });
    return () => {
      active = false;
    };
  }, [booking, attempt]);

  function retry() {
    setLoadError(null);
    setEstimate(null);
    setAttempt((n) => n + 1);
  }

  async function confirm() {
    setSubmitError(null);
    setSubmitting(true);
    try {
      onBooked(await bookingApi.book(booking));
    } catch (err) {
      setSubmitError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">Review & Confirm</h2>
        <p className="text-sm text-slate-500">
          Check your details and the delivery fee before booking.
        </p>
      </div>

      <dl className="grid gap-4 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">
        <div>
          <dt className="font-medium text-slate-500">Pickup</dt>
          <dd className="text-slate-900">
            {booking.pickup.addressLine1}, {booking.pickup.city}, {booking.pickup.district}
          </dd>
        </div>
        <div>
          <dt className="font-medium text-slate-500">Drop-off</dt>
          <dd className="text-slate-900">
            {booking.dropoff.addressLine1}, {booking.dropoff.city}, {booking.dropoff.district}
          </dd>
        </div>
        <div>
          <dt className="font-medium text-slate-500">Parcel</dt>
          <dd className="text-slate-900">
            {booking.parcelType.toLowerCase()}, {booking.weightKg} kg
          </dd>
        </div>
      </dl>

      <section aria-labelledby="fee-title" className="rounded-xl border border-slate-200 p-4">
        <h3 id="fee-title" className="mb-3 font-semibold text-slate-900">
          Estimated delivery fee
        </h3>

        {!estimate && !loadError && (
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <Spinner className="size-4" />
            Calculating your fee...
          </div>
        )}

        {loadError && (
          <div className="space-y-3">
            <Alert tone="error">{loadError}</Alert>
            <Button variant="outline" size="sm" onClick={retry}>
              Try Again
            </Button>
          </div>
        )}

        {estimate && (
          <>
            <ul className="space-y-2 text-sm">
              {estimate.lines.map((line) => (
                <li key={line.label} className="flex justify-between">
                  <span className="text-slate-600">{line.label}</span>
                  <span className="text-slate-900">{formatLkr(line.amount)}</span>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex justify-between border-t border-slate-200 pt-3 font-semibold text-slate-900">
              <span>Total</span>
              <span>{formatLkr(estimate.totalFee)}</span>
            </div>
          </>
        )}
      </section>

      {submitError && <Alert tone="error">{submitError}</Alert>}

      <div className="flex items-center justify-between gap-3">
        <Button variant="outline" onClick={onBack} disabled={submitting}>
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back
        </Button>
        <Button onClick={confirm} loading={submitting} disabled={!estimate}>
          <Check className="size-4" aria-hidden="true" />
          Confirm Booking
        </Button>
      </div>
    </div>
  );
}
