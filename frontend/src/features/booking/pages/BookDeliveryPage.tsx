import { ArrowLeft, Info } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { useForm } from '../../../hooks/useForm';
import { PATHS } from '../../../routes/paths';
import AddressStep from '../components/AddressStep';
import Stepper from '../components/Stepper';
import {
  BOOKING_STEPS,
  initialAddressValues,
  validateAddressStep,
  type AddressValues,
} from '../bookingForm';

export default function BookDeliveryPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const addressForm = useForm<AddressValues>(initialAddressValues(), validateAddressStep);

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
              onContinue={() => setStep(2)}
              onCancel={() => navigate(PATHS.customerDashboard)}
            />
          )}
          {step > 1 && (
            <div className="space-y-4">
              <Alert tone="info">Parcel details and the review step come next.</Alert>
              <Button onClick={() => setStep(1)}>Back</Button>
            </div>
          )}
        </div>

        <aside className="h-fit rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
          <h2 className="mb-3 flex items-center gap-2 font-semibold text-slate-900">
            <Info className="size-4 text-brand-500" aria-hidden="true" />
            Important information
          </h2>
          <ul className="list-disc space-y-2 pl-5 text-sm text-slate-600">
            <li>Please make sure all address details are correct.</li>
            <li>The delivery fee is calculated in the next step.</li>
            <li>You will get a tracking number after a successful booking.</li>
            <li>Prohibited items are not allowed.</li>
          </ul>
        </aside>
      </div>
    </div>
  );
}
