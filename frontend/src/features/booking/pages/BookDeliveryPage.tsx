import { ArrowLeft, Info, CircleCheck } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { useForm } from '../../../hooks/useForm';
import { useSettings } from '../../../hooks/useSettings';
import { PATHS } from '../../../routes/paths';
import type { BookingConfirmation, BookingRequest } from '../../../types/booking';
import AddressStep from '../components/AddressStep';
import ParcelStep from '../components/ParcelStep';
import ReviewStep from '../components/ReviewStep';
import Stepper from '../components/Stepper';
import {
  BOOKING_STEPS,
  initialAddressValues,
  initialParcelValues,
  toBookingRequest,
  validateAddressStep,
  validateParcelStep,
  type AddressValues,
  type ParcelValues,
} from '../bookingForm';

export default function BookDeliveryPage() {
  const navigate = useNavigate();
  const { settings } = useSettings();
  const [step, setStep] = useState(1);
  const [booked, setBooked] = useState<BookingConfirmation | null>(null);
  const [parcelValues, setParcelValues] = useState<ParcelValues | null>(null);
  const [addressValues, setAddressValues] = useState<AddressValues | null>(null);

  const addressForm = useForm<AddressValues>(initialAddressValues(), validateAddressStep);
  const limits = {
    maxWeightKg: Number(settings?.maxParcelWeightKg ?? 50),
    maxSideCm: Number(settings?.maxParcelDimensionCm ?? 120),
  };
  const parcelForm = useForm<ParcelValues>(
    initialParcelValues(settings?.defaultParcelType ?? 'PARCEL'),
    (values) => validateParcelStep(values, limits),
  );

  // Built only once both steps are complete, so the review step always gets the final data.
  const bookingRequest: BookingRequest | null = useMemo(
    () => (addressValues && parcelValues ? toBookingRequest(addressValues, parcelValues) : null),
    [addressValues, parcelValues],
  );

  if (booked) {
    return (
      <div className="mx-auto max-w-2xl space-y-6 rounded-xl border border-slate-200 bg-white p-8 text-center shadow-xs">
        <CircleCheck className="mx-auto size-12 text-brand-500" aria-hidden="true" />
        <h1 className="text-2xl font-bold text-slate-900">Booking confirmed</h1>
        <p className="text-slate-600">Your tracking number is</p>
        <p className="font-mono text-2xl font-semibold text-slate-900">{booked.trackingNumber}</p>
        <p className="text-sm text-slate-500">
          Total: {booked.currency} {booked.totalFee.toFixed(2)}
        </p>
        <div className="flex flex-wrap justify-center gap-3">
          <Button variant="outline" onClick={() => navigate(PATHS.customerDashboard)}>
            Back to Dashboard
          </Button>
          <Button onClick={() => navigate(`/customer/deliveries/${booked.trackingNumber}`)}>
            Track this parcel
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div>
        <Link
          to={PATHS.customerDashboard}
          className="mb-3 inline-flex items-center gap-1.5 text-sm text-slate-600 hover:text-slate-900"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back to Dashboard
        </Link>
        <h1 className="text-3xl font-bold text-slate-900">Book a Delivery</h1>
        <p className="mt-1 text-slate-600">
          Send a parcel anywhere with SwiftDrop. Get an instant estimate and confirm your booking.
        </p>
      </div>

      <Stepper steps={BOOKING_STEPS} current={step} />

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
          {step === 1 && (
            <AddressStep
              form={addressForm}
              onContinue={(values) => {
                setAddressValues(values);
                setStep(2);
              }}
              onCancel={() => navigate(PATHS.customerDashboard)}
            />
          )}

          {step === 2 && (
            <ParcelStep
              form={parcelForm}
              limits={limits}
              onBack={() => setStep(1)}
              onContinue={(values) => {
                setParcelValues(values);
                setStep(3);
              }}
            />
          )}

          {step === 3 && bookingRequest && (
            <ReviewStep booking={bookingRequest} onBack={() => setStep(2)} onBooked={setBooked} />
          )}

          {step === 3 && !bookingRequest && (
            <Alert tone="error">Something went wrong. Please start the booking again.</Alert>
          )}
        </div>

        <aside className="h-fit rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
          <h2 className="mb-3 flex items-center gap-2 font-semibold text-slate-900">
            <Info className="size-4 text-brand-500" aria-hidden="true" />
            Important information
          </h2>
          <ul className="list-disc space-y-2 pl-5 text-sm text-slate-600">
            <li>Please make sure all address details are correct.</li>
            <li>The delivery fee is calculated again when you confirm.</li>
            <li>You will get a tracking number after a successful booking.</li>
            <li>Prohibited items are not allowed.</li>
          </ul>
        </aside>
      </div>
    </div>
  );
}
