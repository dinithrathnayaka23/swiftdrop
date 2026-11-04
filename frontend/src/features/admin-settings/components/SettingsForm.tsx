import { Bell, Package, RotateCcw, Save, Settings2, TriangleAlert, Wrench } from 'lucide-react';
import { useMemo, useState, type FormEvent } from 'react';
import { flushSync } from 'react-dom';
import { ApiRequestError, getErrorMessage } from '../../../api/errors';
import { settingsApi } from '../../../api/settings';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import Checkbox from '../../../components/ui/Checkbox';
import SelectField from '../../../components/ui/SelectField';
import Switch from '../../../components/ui/Switch';
import TextField from '../../../components/ui/TextField';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { useForm } from '../../../hooks/useForm';
import { useSettings } from '../../../hooks/useSettings';
import { useUnsavedChangesPrompt } from '../../../hooks/useUnsavedChangesPrompt';
import type { SystemSettings } from '../../../types/settings';
import { cn } from '../../../utils/cn';
import {
  DATE_FORMAT_OPTIONS,
  FAILED_ACTION_OPTIONS,
  ITEMS_PER_PAGE_OPTIONS,
  PARCEL_TYPE_OPTIONS,
  TIME_FORMAT_OPTIONS,
  timeZoneOptions,
} from '../options';
import {
  FIELD_TAB,
  toRequest,
  toTextValues,
  toToggles,
  validateSettings,
  type SettingsTab,
  type SettingsTextValues,
  type SettingsToggles,
} from '../settingsForm';
import SettingsCard from './SettingsCard';

const TABS: { id: SettingsTab; label: string; icon: typeof Settings2 }[] = [
  { id: 'general', label: 'General', icon: Settings2 },
  { id: 'parcels', label: 'Parcel Settings', icon: Package },
  { id: 'notifications', label: 'Notifications', icon: Bell },
  { id: 'maintenance', label: 'Maintenance', icon: Wrench },
];

const NOTIFICATIONS: { key: keyof SettingsToggles; label: string }[] = [
  { key: 'notifyFailedDeliveries', label: 'Email notifications for failed deliveries' },
  { key: 'notifyNewMerchants', label: 'Email notifications for new merchant registrations' },
  { key: 'notifyCourierIssues', label: 'Email notifications for courier issues' },
  { key: 'dailySummaryReport', label: 'Daily operational summary report' },
];

interface SettingsFormProps {
  initial: SystemSettings;
  onSaved: (settings: SystemSettings) => void;
  /** Loads the latest settings, discarding unsaved changes. */
  onReload: () => void;
}

export default function SettingsForm({ initial, onSaved, onReload }: SettingsFormProps) {
  const { refresh } = useSettings();
  const { formatDateTime } = useDateFormat();
  const initialText = useMemo(() => toTextValues(initial), [initial]);
  const initialToggles = useMemo(() => toToggles(initial), [initial]);

  const form = useForm<SettingsTextValues>(initialText, validateSettings);
  const [toggles, setToggles] = useState<SettingsToggles>(initialToggles);
  const [tab, setTab] = useState<SettingsTab>('general');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [confirmMaintenance, setConfirmMaintenance] = useState(false);

  const zones = useMemo(() => timeZoneOptions(initial.timeZone), [initial.timeZone]);
  const dirty =
    (Object.keys(initialText) as (keyof SettingsTextValues)[]).some(
      (key) => form.values[key] !== initialText[key],
    ) ||
    (Object.keys(initialToggles) as (keyof SettingsToggles)[]).some(
      (key) => toggles[key] !== initialToggles[key],
    );

  // Not while saving: the saved form replaces this one and nothing is lost.
  const leaving = useUnsavedChangesPrompt(dirty && !saving);

  const tabsWithErrors = new Set(
    (Object.keys(form.errors) as (keyof SettingsTextValues)[])
      .filter((key) => form.errors[key])
      .map((key) => FIELD_TAB[key]),
  );

  const setToggle = (key: keyof SettingsToggles, value: boolean) =>
    setToggles((current) => ({ ...current, [key]: value }));

  const save = form.handleSubmit(async (values) => {
    setSaving(true);
    setFormError(null);
    setConflict(false);
    try {
      const saved = await settingsApi.update(toRequest(initial.version, values, toggles));
      await refresh(); // contact details, formats and maintenance update across the app
      onSaved(saved);
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        setConflict(true);
      } else if (form.applyServerErrors(err)) {
        const first = (err as ApiRequestError).fieldErrors[0]?.field as keyof SettingsTextValues;
        if (FIELD_TAB[first]) setTab(FIELD_TAB[first]);
      } else {
        setFormError(getErrorMessage(err));
      }
      setSaving(false);
    }
  });

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    // Open the tab of the first invalid field first, so it can be shown and focused.
    const errors = validateSettings(form.values);
    const firstInvalid = (Object.keys(initialText) as (keyof SettingsTextValues)[]).find(
      (key) => errors[key],
    );
    if (firstInvalid) flushSync(() => setTab(FIELD_TAB[firstInvalid]));
    save(e);
  };

  return (
    <form onSubmit={onSubmit} noValidate>
      <p className="text-sm text-slate-500">Last saved {formatDateTime(initial.updatedAt)}</p>

      <div
        role="tablist"
        aria-label="Settings sections"
        className="mt-4 flex flex-wrap gap-1 border-b border-slate-200"
      >
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            id={`settings-tab-${id}`}
            aria-selected={tab === id}
            aria-controls={`settings-panel-${id}`}
            onClick={() => setTab(id)}
            className={cn(
              '-mb-px inline-flex shrink-0 items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors',
              tab === id
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:text-slate-800',
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            {label}
            {tabsWithErrors.has(id) && (
              <span className="size-2 rounded-full bg-red-500" aria-label="has errors" />
            )}
          </button>
        ))}
      </div>

      <div className="mt-6 space-y-4">
        {conflict && (
          <Alert tone="warning" title="These settings were changed somewhere else">
            <p>
              Someone saved the settings after you opened this page. Reload to see their changes.
            </p>
            <Button type="button" variant="outline" size="sm" className="mt-3" onClick={onReload}>
              <RotateCcw className="size-4" aria-hidden="true" />
              Reload Settings
            </Button>
          </Alert>
        )}
        {formError && <Alert tone="error">{formError}</Alert>}
      </div>

      {/* General */}
      <div
        role="tabpanel"
        id="settings-panel-general"
        aria-labelledby="settings-tab-general"
        hidden={tab !== 'general'}
        className="mt-4 grid gap-6 xl:grid-cols-2"
      >
        <SettingsCard
          title="Company Information"
          description="Shown to customers, merchants and couriers as your support contact."
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Company Name" maxLength={120} {...form.field('companyName')} />
            <TextField
              label="Email"
              type="email"
              maxLength={255}
              hint="Support address shown across the site"
              {...form.field('contactEmail')}
            />
            <TextField
              label="Phone"
              type="tel"
              placeholder="+94 77 123 4567"
              optional
              {...form.field('contactPhone')}
            />
            <TextField
              label="Address"
              placeholder="No. 15, Park Road, Colombo 07"
              maxLength={255}
              optional
              {...form.field('companyAddress')}
            />
          </div>
        </SettingsCard>

        <SettingsCard
          title="Application Settings"
          description="How dates, times and lists are shown to everyone."
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField label="Default Timezone" options={zones} {...form.field('timeZone')} />
            <SelectField
              label="Date Format"
              options={DATE_FORMAT_OPTIONS}
              {...form.field('dateFormat')}
            />
            <SelectField
              label="Time Format"
              options={TIME_FORMAT_OPTIONS}
              {...form.field('timeFormat')}
            />
            <SelectField
              label="Items Per Page"
              options={ITEMS_PER_PAGE_OPTIONS}
              {...form.field('itemsPerPage')}
            />
          </div>
        </SettingsCard>
      </div>

      {/* Parcels */}
      <div
        role="tabpanel"
        id="settings-panel-parcels"
        aria-labelledby="settings-tab-parcels"
        hidden={tab !== 'parcels'}
        className="mt-4"
      >
        <SettingsCard
          title="Parcel Settings"
          description="Defaults and limits for booking, and how failed deliveries are handled."
        >
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <SelectField
              label="Default Parcel Type"
              options={PARCEL_TYPE_OPTIONS}
              {...form.field('defaultParcelType')}
            />
            <TextField
              label="Maximum Parcel Weight (kg)"
              inputMode="decimal"
              {...form.field('maxParcelWeightKg')}
            />
            <TextField
              label="Maximum Dimensions (cm)"
              inputMode="decimal"
              hint="Longest side of a parcel"
              {...form.field('maxParcelDimensionCm')}
            />
            <SelectField
              label="Default Failed Delivery Action"
              options={FAILED_ACTION_OPTIONS}
              {...form.field('failedDeliveryAction')}
            />
            <Switch
              label="Auto Assign Courier"
              description="Assign new parcels to an available courier automatically."
              checked={toggles.autoAssignCourier}
              onChange={(value) => setToggle('autoAssignCourier', value)}
            />
          </div>
        </SettingsCard>
      </div>

      {/* Notifications */}
      <div
        role="tabpanel"
        id="settings-panel-notifications"
        aria-labelledby="settings-tab-notifications"
        hidden={tab !== 'notifications'}
        className="mt-4"
      >
        <SettingsCard
          title="Notification Settings"
          description="Emails sent to the admin about operations."
        >
          <div className="space-y-3">
            {NOTIFICATIONS.map(({ key, label }) => (
              <div key={key}>
                <Checkbox
                  label={label}
                  checked={toggles[key]}
                  onChange={(e) => setToggle(key, e.target.checked)}
                />
              </div>
            ))}
          </div>
        </SettingsCard>
      </div>

      {/* Maintenance */}
      <div
        role="tabpanel"
        id="settings-panel-maintenance"
        aria-labelledby="settings-tab-maintenance"
        hidden={tab !== 'maintenance'}
        className="mt-4"
      >
        <SettingsCard title="System Maintenance" description="Take the system offline for upkeep.">
          <Switch
            label="Maintenance Mode"
            tone="danger"
            description="When enabled, the system will be unavailable for regular users. You can still use the admin portal."
            checked={toggles.maintenanceMode}
            onChange={(value) =>
              value ? setConfirmMaintenance(true) : setToggle('maintenanceMode', false)
            }
          />
          {toggles.maintenanceMode && !initialToggles.maintenanceMode && (
            <Alert tone="warning" className="mt-4">
              Save the settings to start maintenance.
            </Alert>
          )}
        </SettingsCard>
      </div>

      <div className="mt-6 flex flex-col-reverse gap-3 border-t border-slate-200 pt-5 sm:flex-row sm:items-center sm:justify-end">
        {dirty && (
          <Button type="button" variant="ghost" onClick={onReload} disabled={saving}>
            Discard Changes
          </Button>
        )}
        <Button type="submit" loading={saving} disabled={!dirty}>
          {!saving && <Save className="size-4" aria-hidden="true" />}
          {saving ? 'Saving...' : 'Save Changes'}
        </Button>
      </div>

      <ConfirmDialog
        open={confirmMaintenance}
        icon={Wrench}
        title="Turn on maintenance mode?"
        message="Customers, merchants and couriers won't be able to use SwiftDrop until you turn it off. You can still use the admin portal."
        confirmLabel="Turn On"
        onCancel={() => setConfirmMaintenance(false)}
        onConfirm={() => {
          setToggle('maintenanceMode', true);
          setConfirmMaintenance(false);
        }}
      />
      <ConfirmDialog
        open={leaving.state === 'blocked'}
        icon={TriangleAlert}
        title="Leave without saving?"
        message="You have unsaved changes to the system settings. If you leave now, they will be lost."
        confirmLabel="Leave Page"
        cancelLabel="Stay"
        onCancel={() => leaving.reset?.()}
        onConfirm={() => leaving.proceed?.()}
      />
      <span className="sr-only" aria-live="polite">
        {dirty ? 'You have unsaved changes' : ''}
      </span>
    </form>
  );
}
