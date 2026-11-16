import { useState, type FormEvent } from 'react';
import { couriersApi } from '../../../api/couriers';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import PasswordField from '../../../components/ui/PasswordField';
import PhoneField from '../../../components/ui/PhoneField';
import SelectField from '../../../components/ui/SelectField';
import TextField from '../../../components/ui/TextField';
import type { Courier } from '../../../types/courier';
import { DEFAULT_COUNTRY_CODE } from '../../../utils/phone';
import Modal from '../../shared-dispatch/components/Modal';
import {
  courierErrorsFrom,
  toNewCourier,
  validateNewCourier,
  VEHICLE_TYPES,
  type CourierFormErrors,
  type CourierFormValues,
} from '../courierRules';
import { keepActive, useActiveZones } from '../activeZones';
import ServiceAreaPicker from './ServiceAreaPicker';

const EMPTY: CourierFormValues = {
  fullName: '',
  email: '',
  countryCode: DEFAULT_COUNTRY_CODE,
  phone: '',
  password: '',
  vehicleType: '',
  vehicleRegistration: '',
  serviceAreaIds: [],
};

interface AddCourierModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (courier: Courier) => void;
}

/**
 * Add Courier: creates the courier's sign-in account (active, email already confirmed) and profile.
 * The dispatcher passes the first password on; the courier starts Offline.
 */
export default function AddCourierModal({ open, onClose, onCreated }: AddCourierModalProps) {
  const [values, setValues] = useState<CourierFormValues>(EMPTY);
  const [errors, setErrors] = useState<CourierFormErrors>({});
  const [message, setMessage] = useState<string>();
  const [saving, setSaving] = useState(false);
  const activeZones = useActiveZones();

  function set<K extends keyof CourierFormValues>(key: K, value: CourierFormValues[K]) {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  }

  function close() {
    setValues(EMPTY);
    setErrors({});
    setMessage(undefined);
    onClose();
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = validateNewCourier(values);
    setErrors(found);
    setMessage(undefined);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    try {
      const zones = activeZones.status === 'success' ? activeZones.data.content : [];
      const created = await couriersApi.create(
        toNewCourier({ ...values, serviceAreaIds: keepActive(values.serviceAreaIds, zones) }),
      );
      setValues(EMPTY);
      onCreated(created);
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
      open={open}
      title="Add Courier"
      description="Creates the courier's sign-in account. They start Offline and can sign in at once."
      onClose={close}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="add-courier-form" loading={saving}>
            Add Courier
          </Button>
        </>
      }
    >
      <form
        id="add-courier-form"
        noValidate
        onSubmit={submit}
        className="grid gap-4 sm:grid-cols-2"
      >
        {message && (
          <Alert tone="error" className="sm:col-span-2">
            {message}
          </Alert>
        )}
        <div className="sm:col-span-2">
          <TextField
            label="Full Name"
            name="fullName"
            autoComplete="off"
            value={values.fullName}
            onChange={(e) => set('fullName', e.target.value)}
            error={errors.fullName}
          />
        </div>
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="off"
          value={values.email}
          onChange={(e) => set('email', e.target.value)}
          error={errors.email}
        />
        <PhoneField
          label="Phone Number"
          name="phone"
          countryCode={values.countryCode}
          number={values.phone}
          onCountryCodeChange={(code) => set('countryCode', code)}
          onNumberChange={(number) => set('phone', number)}
          error={errors.phone}
        />
        <div className="sm:col-span-2">
          <PasswordField
            label="First Password"
            name="password"
            autoComplete="new-password"
            value={values.password}
            onChange={(e) => set('password', e.target.value)}
            error={errors.password}
            hint="8+ characters with a letter and a number. Give it to the courier; they can change it later."
          />
        </div>
        <SelectField
          label="Vehicle Type"
          name="vehicleType"
          value={values.vehicleType}
          onChange={(e) => set('vehicleType', e.target.value)}
          options={[
            { value: '', label: 'Choose a vehicle type' },
            ...VEHICLE_TYPES.map((t) => ({ value: t, label: t })),
          ]}
          error={errors.vehicleType}
        />
        <TextField
          label="Vehicle Registration"
          name="vehicleRegistration"
          placeholder="WP CAB-1234"
          value={values.vehicleRegistration}
          onChange={(e) => set('vehicleRegistration', e.target.value)}
          error={errors.vehicleRegistration}
        />
        <div className="sm:col-span-2">
          <ServiceAreaPicker
            value={values.serviceAreaIds}
            onChange={(ids) => set('serviceAreaIds', ids)}
            activeZones={activeZones}
            error={errors.serviceAreaIds}
          />
        </div>
      </form>
    </Modal>
  );
}
