import { Search } from 'lucide-react';
import { useState } from 'react';
import { couriersApi } from '../../../api/couriers';
import { inputClasses } from '../../../components/ui/fieldStyles';
import Button from '../../../components/ui/Button';
import type { Courier } from '../../../types/courier';
import { cn } from '../../../utils/cn';
import { canTakeParcels } from '../../couriers/courierRules';
import { useApiData } from '../../shared-parcels/useApiData';
import { useDebouncedValue } from '../useDebouncedValue';

interface OnlineCourierPickerProps {
  selectedId: string | null;
  onSelect: (courier: Courier) => void;
  /** Left out of the list (the parcel's current courier, when reassigning). */
  excludeId?: string;
  disabled?: boolean;
}

/**
 * Picks a courier who can be given parcels now: online, active, account active. Shows what the
 * API returns about each: name, vehicle, today's deliveries and service areas.
 */
export default function OnlineCourierPicker({
  selectedId,
  onSelect,
  excludeId,
  disabled = false,
}: OnlineCourierPickerProps) {
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput.trim(), 300);
  const list = useApiData(`online-couriers|${search}`, () =>
    couriersApi.list({
      availability: 'ONLINE',
      active: true,
      search: search || undefined,
      size: 50,
    }),
  );
  const couriers =
    list.status === 'success'
      ? list.data.content.filter((c) => canTakeParcels(c) && c.id !== excludeId)
      : [];

  return (
    <div className="space-y-3">
      <div className="relative">
        <label
          htmlFor="courier-picker-search"
          className="mb-1 block text-sm font-medium text-slate-700"
        >
          Search couriers
        </label>
        <Search
          className="pointer-events-none absolute bottom-3 left-3.5 size-4 text-slate-400"
          aria-hidden="true"
        />
        <input
          id="courier-picker-search"
          type="search"
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          placeholder="Name, phone or vehicle registration..."
          className={inputClasses(false, 'h-10 pl-10')}
        />
      </div>

      <fieldset>
        <legend className="mb-2 text-sm font-medium text-slate-700">Online couriers</legend>
        {list.status === 'loading' && (
          <div className="space-y-2" aria-busy="true" aria-label="Loading couriers">
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="h-16 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}
        {list.status === 'error' && (
          <div className="flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
            <span>{list.message}</span>
            <Button variant="outline" size="sm" onClick={list.retry}>
              Try again
            </Button>
          </div>
        )}
        {list.status === 'success' && couriers.length === 0 && (
          <p className="rounded-lg border border-slate-200 px-3 py-4 text-center text-sm text-slate-500">
            {search ? 'No online courier matches this search.' : 'No courier is online right now.'}
          </p>
        )}
        {couriers.length > 0 && (
          <ul className="max-h-72 space-y-2 overflow-y-auto">
            {couriers.map((courier) => {
              const selected = courier.id === selectedId;
              return (
                <li key={courier.id}>
                  <label
                    className={cn(
                      'flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5 text-sm',
                      selected
                        ? 'border-brand-600 bg-brand-50'
                        : 'border-slate-200 hover:bg-slate-50',
                    )}
                  >
                    <input
                      type="radio"
                      name="courier"
                      value={courier.id}
                      checked={selected}
                      disabled={disabled}
                      onChange={() => onSelect(courier)}
                      className="mt-1 accent-brand-700"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-baseline justify-between gap-x-3">
                        <span className="font-semibold text-slate-900">{courier.fullName}</span>
                        <span className="text-xs text-slate-500">
                          {courier.workload.todaysTotal} today
                        </span>
                      </span>
                      <span className="block text-xs text-slate-600">
                        {courier.vehicleType} · {courier.vehicleRegistration}
                      </span>
                      <span className="block text-xs text-slate-500">
                        {courier.serviceAreas.length === 0
                          ? 'Any area'
                          : courier.serviceAreas.map((a) => a.name ?? 'Unknown').join(', ')}
                      </span>
                    </span>
                  </label>
                </li>
              );
            })}
          </ul>
        )}
      </fieldset>
    </div>
  );
}
