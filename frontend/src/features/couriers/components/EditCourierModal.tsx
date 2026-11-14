import { useState, type FormEvent } from 'react';
import { couriersApi } from '../../../api/couriers';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import SelectField from '../../../components/ui/SelectField';
import TextField from '../../../components/ui/TextField';
import type { Courier, CourierUpdate } from '../../../types/courier';
import Modal from '../../shared-dispatch/components/Modal';
import {
  courierErrorsFrom,
  validateRegistration,
  validateVehicleType,
  VEHICLE_TYPES,
  type CourierFormErrors,
} from '../courierRules';
import { keepActive, useActiveZones } from '../activeZones';
import ServiceAreaPicker from './ServiceAreaPicker';

interface EditCourierModalProps {
  courier: Courier;
  onClose: () => void;
  onSaved: (courier: Courier) => void;
}

/** Edit the vehicle and the service areas (PATCH; the service areas replace the whole set). */
export default function EditCourierModal({ courier, onClose, onSaved }: EditCourierModalProps) {
  const [vehicleType, setVehicleType] = useState(courier.vehicleType);
  const [registration, setRegistration] = useState(courier.vehicleRegistration);
  const [areaIds, setAreaIds] = useState(courier.serviceAreaIds);
  const [errors, setErrors] = useState<CourierFormErrors>({});
  const [message, setMessage] = useState<string>();
  const [saving, setSaving] = useState(false);
  const activeZones = useActiveZones();
  // Keep a non-standard type the courier already has selectable.
  const types = VEHICLE_TYPES.includes(courier.vehicleType)
    ? VEHICLE_TYPES
    : [courier.vehicleType, ...VEHICLE_TYPES];

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found: CourierFormErrors = {
      vehicleType: validateVehicleType(vehicleType),
      vehicleRegistration: validateRegistration(registration),
    };
    if (activeZones.status !== 'success')
      found.serviceAreaIds = 'Wait for the service areas to load';
    setMessage(undefined);
    if (found.vehicleType || found.vehicleRegistration || found.serviceAreaIds) {
      setErrors(found);
      return;
    }
    const update: CourierUpdate = {
      vehicleType: vehicleType.trim(),
      vehicleRegistration: registration.trim(),
      serviceAreaIds: keepActive(
        areaIds,
        activeZones.status === 'success' ? activeZones.data.content : [],
      ),
    };
    setSaving(true);
    try {
      onSaved(await couriersApi.update(courier.id, update));
    } catch (err) {
      const { fields, message: text } = courierErrorsFrom(err);
      setErrors(fields);
      setMessage(text);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      title="Edit Vehicle and Service Areas"
      description={courier.fullName}
      onClose={onClose}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="edit-courier-form" loading={saving}>
            Save Changes
          </Button>
        </>
      }
    >
      <form
        id="edit-courier-form"
        noValidate
        onSubmit={submit}
        className="grid gap-4 sm:grid-cols-2"
      >
        {message && (
          <Alert tone="error" className="sm:col-span-2">
            {message}
          </Alert>
        )}
        <SelectField
          label="Vehicle Type"
          name="vehicleType"
          value={vehicleType}
          onChange={(e) => setVehicleType(e.target.value)}
          options={types.map((t) => ({ value: t, label: t }))}
          error={errors.vehicleType}
        />
        <TextField
          label="Vehicle Registration"
          name="vehicleRegistration"
          value={registration}
          onChange={(e) => setRegistration(e.target.value)}
          error={errors.vehicleRegistration}
        />
        <div className="sm:col-span-2">
          <ServiceAreaPicker
            value={areaIds}
            onChange={setAreaIds}
            activeZones={activeZones}
            current={courier.serviceAreas}
            error={errors.serviceAreaIds}
          />
        </div>
      </form>
    </Modal>
  );
}
