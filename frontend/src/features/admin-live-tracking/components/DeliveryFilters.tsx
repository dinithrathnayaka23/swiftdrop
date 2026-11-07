import { Filter, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import type { ParcelStatusCode } from '../../../types/tracking';
import { STATUS_META } from '../../../utils/parcelStatus';
import { statusLabel, type AppliedFilters } from '../filters';

const STATUS_OPTIONS = Object.keys(STATUS_META) as ParcelStatusCode[];

const LABEL = 'mb-1 block text-xs font-medium text-slate-500';

/**
 * Search, status and booking dates. Changes apply when the admin presses Filter (or Enter in the
 * search box), so typing doesn't reload the table on every key.
 */
export default function DeliveryFilters({
  applied,
  onApply,
}: {
  applied: AppliedFilters;
  onApply: (filters: AppliedFilters) => void;
}) {
  const [draft, setDraft] = useState(applied);
  const change = <K extends keyof AppliedFilters>(key: K, value: AppliedFilters[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));

  const datesInvalid =
    draft.bookedFrom !== '' && draft.bookedTo !== '' && draft.bookedTo < draft.bookedFrom;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!datesInvalid) onApply({ ...draft, search: draft.search.trim() });
  };

  return (
    <form
      onSubmit={submit}
      role="search"
      aria-label="Filter deliveries"
      className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs lg:flex-row lg:items-end"
    >
      <div className="flex-1">
        <label htmlFor="delivery-search" className={LABEL}>
          Search
        </label>
        <div className="relative">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400"
            aria-hidden="true"
          />
          <input
            id="delivery-search"
            type="search"
            value={draft.search}
            onChange={(e) => change('search', e.target.value)}
            placeholder="Search by tracking number, recipient or merchant..."
            className={inputClasses(false, 'h-10 pl-9')}
          />
        </div>
      </div>

      <div className="lg:w-44">
        <label htmlFor="delivery-status" className={LABEL}>
          Status
        </label>
        <select
          id="delivery-status"
          value={draft.status}
          onChange={(e) => change('status', e.target.value as AppliedFilters['status'])}
          className={inputClasses(false, 'h-10')}
        >
          <option value="">All Status</option>
          {STATUS_OPTIONS.map((status) => (
            <option key={status} value={status}>
              {statusLabel(status)}
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-2 gap-2 lg:w-80">
        <div>
          <label htmlFor="delivery-from" className={LABEL}>
            Booked from
          </label>
          <input
            id="delivery-from"
            type="date"
            value={draft.bookedFrom}
            onChange={(e) => change('bookedFrom', e.target.value)}
            className={inputClasses(false, 'h-10')}
          />
        </div>
        <div>
          <label htmlFor="delivery-to" className={LABEL}>
            Booked to
          </label>
          <input
            id="delivery-to"
            type="date"
            value={draft.bookedTo}
            min={draft.bookedFrom || undefined}
            onChange={(e) => change('bookedTo', e.target.value)}
            aria-invalid={datesInvalid || undefined}
            aria-describedby={datesInvalid ? 'delivery-dates-error' : undefined}
            className={inputClasses(datesInvalid, 'h-10')}
          />
        </div>
        {datesInvalid && (
          <p id="delivery-dates-error" role="alert" className="col-span-2 text-xs text-red-600">
            The end date can&apos;t be before the start date.
          </p>
        )}
      </div>

      <Button type="submit" variant="outline" className="h-10">
        <Filter className="size-4" aria-hidden="true" />
        Filter
      </Button>
    </form>
  );
}
