import { useState } from 'react';
import { dispatchApi } from '../../../api/dispatch';
import { getErrorMessage } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import type { Courier } from '../../../types/courier';
import type { BulkAssignResult } from '../../../types/dispatch';
import Modal from '../../shared-dispatch/components/Modal';
import OnlineCourierPicker from '../../shared-dispatch/components/OnlineCourierPicker';
import BulkResultSummary from './BulkResultSummary';

export interface ParcelRef {
  id: string;
  trackingNumber: string;
}

interface AssignToCourierDialogProps {
  /** The parcels to assign (fixed while the dialog is open). */
  parcels: ParcelRef[];
  onClose: () => void;
  /**
   * After the request: one parcel closes the dialog straight away; several show the result here
   * until Done, so the page can refresh behind it.
   */
  onAssigned: (courier: Courier, result: BulkAssignResult) => void;
}

/** Parcels first: pick an online courier for one parcel (POST /assignments) or several (bulk). */
export default function AssignToCourierDialog({
  parcels,
  onClose,
  onAssigned,
}: AssignToCourierDialogProps) {
  const single = parcels.length === 1;
  const [courier, setCourier] = useState<Courier | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [result, setResult] = useState<{ courier: Courier; result: BulkAssignResult }>();
  const tracking = new Map(parcels.map((p) => [p.id, p.trackingNumber]));

  async function assign() {
    if (!courier || saving) return;
    setSaving(true);
    setError(undefined);
    try {
      if (single) {
        const assignment = await dispatchApi.assign(parcels[0].id, courier.id);
        onAssigned(courier, { assigned: [assignment], failed: [] });
      } else {
        const outcome = await dispatchApi.assignAll(
          courier.id,
          parcels.map((p) => p.id),
        );
        setResult({ courier, result: outcome });
        onAssigned(courier, outcome);
      }
    } catch (err) {
      // e.g. 409: the courier went offline, or someone else assigned the parcel first.
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      title={single ? `Assign #${parcels[0].trackingNumber}` : `Assign ${parcels.length} Parcels`}
      description="Choose an online courier. Only couriers who can take parcels now are listed."
      onClose={onClose}
      footer={
        result ? (
          <Button onClick={onClose}>Done</Button>
        ) : (
          <>
            <Button variant="outline" onClick={onClose} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={assign} loading={saving} disabled={!courier || saving}>
              {single ? 'Assign' : `Assign ${parcels.length} Parcels`}
            </Button>
          </>
        )
      }
    >
      <div className="space-y-4">
        {!single && !result && (
          <p className="text-sm text-slate-600">
            {parcels.map((p) => `#${p.trackingNumber}`).join(', ')}
          </p>
        )}
        {error && <Alert tone="error">{error}</Alert>}
        {result ? (
          <BulkResultSummary
            result={result.result}
            courierName={result.courier.fullName}
            trackingOf={(id) => tracking.get(id)}
          />
        ) : (
          <OnlineCourierPicker
            selectedId={courier?.id ?? null}
            onSelect={(picked) => {
              setCourier(picked);
              setError(undefined);
            }}
            disabled={saving}
          />
        )}
      </div>
    </Modal>
  );
}
