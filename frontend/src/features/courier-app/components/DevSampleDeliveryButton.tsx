import { FlaskConical } from 'lucide-react';
import { useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import Button from '../../../components/ui/Button';
import { courierApi } from '../api';

interface DevSampleDeliveryButtonProps {
  /** Called after a sample was added, so the list can reload. */
  onAdded: () => void;
}

/**
 * Development builds only: gives the signed-in courier a sample delivery (the backend's dev-profile
 * helper), so the courier screens can be tried without an admin assigning parcels.
 */
export default function DevSampleDeliveryButton({ onAdded }: DevSampleDeliveryButtonProps) {
  const [adding, setAdding] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function add() {
    setAdding(true);
    setMessage(null);
    try {
      const sample = await courierApi.addSampleDelivery();
      setMessage(`Added ${sample.trackingNumber}.`);
      onAdded();
    } catch (err) {
      setMessage(`Couldn't add a sample: ${getErrorMessage(err)}`);
    } finally {
      setAdding(false);
    }
  }

  return (
    <div className="rounded-xl border border-dashed border-slate-300 p-3">
      <p className="text-xs font-semibold tracking-wide text-slate-500 uppercase">
        Development only
      </p>
      <Button
        variant="outline"
        size="sm"
        className="mt-2"
        loading={adding}
        onClick={() => void add()}
      >
        <FlaskConical className="size-4" aria-hidden="true" />
        Add sample delivery
      </Button>
      {message && (
        <p className="mt-2 text-sm text-slate-600" role="status">
          {message}
        </p>
      )}
    </div>
  );
}
