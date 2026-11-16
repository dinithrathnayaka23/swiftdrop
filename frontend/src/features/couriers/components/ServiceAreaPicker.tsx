import { useId } from 'react';
import type { CourierServiceArea } from '../../../types/courier';
import type { ActiveZonesState } from '../activeZones';

interface ServiceAreaPickerProps {
  /** The selected zone ids. */
  value: string[];
  onChange: (ids: string[]) => void;
  activeZones: ActiveZonesState;
  /** The courier's current zones with names, to label the ones that are no longer active. */
  current?: CourierServiceArea[];
  error?: string;
}

/** Checkboxes for the active service areas, plus "inactive" chips for selected zones that aren't. */
export default function ServiceAreaPicker({
  value,
  onChange,
  activeZones,
  current = [],
  error,
}: ServiceAreaPickerProps) {
  const groupId = useId();
  const errorId = useId();
  const zones = activeZones.status === 'success' ? activeZones.data.content : [];
  const activeIds = new Set(zones.map((zone) => zone.id));
  const inactiveSelected =
    activeZones.status === 'success'
      ? current.filter((area) => value.includes(area.id) && !activeIds.has(area.id))
      : [];

  function toggle(id: string) {
    onChange(value.includes(id) ? value.filter((v) => v !== id) : [...value, id]);
  }

  return (
    <fieldset aria-describedby={error ? errorId : undefined}>
      <legend id={groupId} className="mb-1.5 block text-sm font-medium text-slate-700">
        Service Areas <span className="font-normal text-slate-400">(optional)</span>
      </legend>
      {inactiveSelected.length > 0 && (
        <div className="mb-2 flex flex-wrap items-center gap-2">
          {inactiveSelected.map((area) => (
            <span
              key={area.id}
              className="inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-2.5 py-1 text-xs text-slate-600"
            >
              {area.name ?? 'Unknown area'}
              <span className="rounded bg-red-50 px-1.5 text-[11px] font-semibold text-red-700">
                inactive
              </span>
            </span>
          ))}
          <span className="text-xs text-slate-500">Inactive areas are removed when you save.</span>
        </div>
      )}
      {activeZones.status === 'loading' && (
        <p className="text-sm text-slate-500">Loading service areas…</p>
      )}
      {activeZones.status === 'error' && (
        <p className="text-sm text-red-600">
          Couldn&apos;t load service areas.{' '}
          <button type="button" className="font-medium underline" onClick={activeZones.retry}>
            Try again
          </button>
        </p>
      )}
      {activeZones.status === 'success' && (
        <div className="grid max-h-48 grid-cols-1 gap-1 overflow-y-auto rounded-lg border border-slate-200 p-2 sm:grid-cols-2">
          {zones.length === 0 && (
            <p className="text-sm text-slate-500">No active service areas yet.</p>
          )}
          {zones.map((zone) => (
            <label
              key={zone.id}
              className="flex items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-slate-50"
            >
              <input
                type="checkbox"
                className="size-4 rounded border-slate-300 text-brand-700"
                checked={value.includes(zone.id)}
                onChange={() => toggle(zone.id)}
              />
              <span className="text-slate-700">{zone.name}</span>
            </label>
          ))}
        </div>
      )}
      {error && (
        <p id={errorId} className="mt-1.5 text-sm text-red-600">
          {error}
        </p>
      )}
    </fieldset>
  );
}
