import { Bike, MapPin } from 'lucide-react';
import { useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import { cn } from '../../../utils/cn';
import { useApiData } from '../../shared-parcels/useApiData';
import { courierApi } from '../api';
import type { CourierProfile, CourierStatus } from '../types';

const STATUSES: { value: CourierStatus; label: string; active: string }[] = [
  { value: 'ONLINE', label: 'Online', active: 'bg-brand-600 text-white' },
  { value: 'ON_BREAK', label: 'On break', active: 'bg-amber-500 text-white' },
  { value: 'OFFLINE', label: 'Offline', active: 'bg-slate-700 text-white' },
];

const STATUS_HINTS: Record<CourierStatus, string> = {
  ONLINE: 'You can be given new deliveries.',
  ON_BREAK: 'You keep your deliveries, but get no new ones.',
  OFFLINE: 'You keep your deliveries, but get no new ones.',
};

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

function ProfileView({ initial }: { initial: CourierProfile }) {
  const [profile, setProfile] = useState(initial);
  const [saving, setSaving] = useState<CourierStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function choose(status: CourierStatus) {
    if (status === profile.status || saving) return;
    setSaving(status);
    setError(null);
    try {
      setProfile(await courierApi.changeStatus(status));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(null);
    }
  }

  return (
    <section aria-label="Your profile" className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="flex items-center gap-3">
        <div
          className="flex size-11 shrink-0 items-center justify-center rounded-full bg-brand-50 text-sm font-semibold text-brand-700"
          aria-hidden="true"
        >
          {initials(profile.fullName)}
        </div>
        <div className="min-w-0">
          <p className="truncate text-base font-semibold text-slate-900">{profile.fullName}</p>
          <p className="flex items-center gap-1 text-sm text-slate-500">
            <Bike className="size-4 shrink-0" aria-hidden="true" />
            {profile.vehicleType} · <span className="font-mono">{profile.vehicleRegistration}</span>
          </p>
        </div>
      </div>

      <p className="mt-3 flex items-start gap-1 text-sm text-slate-600">
        <MapPin className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
        {profile.serviceAreas.length > 0 ? profile.serviceAreas.join(', ') : 'Any area'}
      </p>

      {!profile.active && (
        <Alert tone="warning" className="mt-3">
          Your courier account is deactivated. Contact your dispatcher.
        </Alert>
      )}

      <div className="mt-4" role="group" aria-label="Availability">
        <div className="grid grid-cols-3 gap-1 rounded-xl bg-slate-100 p-1">
          {STATUSES.map(({ value, label, active }) => (
            <button
              key={value}
              type="button"
              onClick={() => void choose(value)}
              aria-pressed={profile.status === value}
              disabled={saving !== null}
              className={cn(
                'min-h-11 rounded-lg px-2 text-sm font-semibold transition disabled:opacity-60',
                profile.status === value ? active : 'text-slate-600 hover:bg-white',
              )}
            >
              {saving === value ? '…' : label}
            </button>
          ))}
        </div>
        <p className="mt-2 text-xs text-slate-500">{STATUS_HINTS[profile.status]}</p>
      </div>

      {error && (
        <Alert tone="error" className="mt-3" title="Couldn't change your availability">
          {error}
        </Alert>
      )}
    </section>
  );
}

/** The courier's profile card: who they are, their vehicle and areas, and Online / On break / Offline. */
export default function CourierProfileCard() {
  const result = useApiData('courier-profile', () => courierApi.getProfile());

  if (result.status === 'loading') {
    return (
      <div
        className="h-40 animate-pulse rounded-2xl border border-slate-200 bg-white"
        aria-label="Loading your profile"
      />
    );
  }
  if (result.status === 'error') {
    // The deliveries still work without the card; keep the message small.
    return (
      <Alert tone="warning" title="Couldn't load your profile">
        {result.message}{' '}
        <button type="button" className="font-semibold underline" onClick={result.retry}>
          Try again
        </button>
      </Alert>
    );
  }
  return <ProfileView initial={result.data} />;
}
