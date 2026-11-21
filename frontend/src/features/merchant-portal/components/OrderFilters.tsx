import { CalendarDays, Search, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { cn } from '../../../utils/cn';
import { STATUS_META } from '../../../utils/parcelStatus';
import { hasFilters, MAX_SEARCH_LENGTH, ORDER_STATUSES, SEARCH_DELAY_MS } from '../orderFilters';
import type { MerchantOrderFilters } from '../types';

interface OrderFiltersProps {
  filters: MerchantOrderFilters;
  /** Called with the full new set of filters. */
  onChange: (filters: MerchantOrderFilters) => void;
}

function Chip({
  pressed,
  onClick,
  children,
}: {
  pressed: boolean;
  onClick: () => void;
  children: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        'rounded-full border px-3 py-1 text-sm font-medium whitespace-nowrap transition',
        'focus-visible:ring-4 focus-visible:ring-brand-100 focus-visible:outline-none',
        pressed
          ? 'border-brand-600 bg-brand-600 text-white'
          : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
      )}
    >
      {children}
    </button>
  );
}

/** Search box, status chips and creation date range of the orders list. */
export default function OrderFilters({ filters, onChange }: OrderFiltersProps) {
  const search = filters.search ?? '';
  const [input, setInput] = useState(search);
  // Follow the search when it changes from outside (e.g. "Clear filters" or the back button).
  const [shownSearch, setShownSearch] = useState(search);
  if (search !== shownSearch) {
    setShownSearch(search);
    setInput(search);
  }

  // The latest props, so the search timer isn't restarted by unrelated re-renders.
  const latest = useRef({ filters, onChange });
  useEffect(() => {
    latest.current = { filters, onChange };
  });

  useEffect(() => {
    const next = input.trim();
    if (next === search) return;
    const timer = setTimeout(() => {
      latest.current.onChange({ ...latest.current.filters, search: next || undefined });
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [input, search]);

  const filtered = hasFilters(filters);

  function clear() {
    setInput(''); // also drops text that hasn't been searched yet
    onChange({});
  }

  return (
    <div className="mb-4 space-y-3">
      <div className="flex flex-col gap-3 lg:flex-row">
        <div className="relative min-w-0 flex-1">
          <Search
            className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400"
            aria-hidden="true"
          />
          <label htmlFor="orders-search" className="sr-only">
            Search orders
          </label>
          <input
            id="orders-search"
            type="search"
            value={input}
            maxLength={MAX_SEARCH_LENGTH}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Search by tracking number, order ref, recipient or city..."
            className={inputClasses(false, 'h-11 pl-10')}
          />
        </div>

        <fieldset className="flex h-11 min-w-0 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm shadow-xs">
          <legend className="sr-only">Created between</legend>
          <CalendarDays className="size-4 shrink-0 text-slate-500" aria-hidden="true" />
          <label htmlFor="orders-from" className="sr-only">
            Created from
          </label>
          <input
            id="orders-from"
            type="date"
            value={filters.from ?? ''}
            max={filters.to || undefined}
            onChange={(event) => onChange({ ...filters, from: event.target.value || undefined })}
            className="min-w-0 flex-1 bg-transparent text-slate-700 outline-none"
          />
          <span className="text-slate-400" aria-hidden="true">
            –
          </span>
          <label htmlFor="orders-to" className="sr-only">
            Created to
          </label>
          <input
            id="orders-to"
            type="date"
            value={filters.to ?? ''}
            min={filters.from || undefined}
            onChange={(event) => onChange({ ...filters, to: event.target.value || undefined })}
            className="min-w-0 flex-1 bg-transparent text-slate-700 outline-none"
          />
        </fieldset>

        {(filtered || input.trim()) && (
          <Button variant="outline" className="h-11 shrink-0" onClick={clear}>
            <X className="size-4" aria-hidden="true" />
            Clear filters
          </Button>
        )}
      </div>

      <div role="group" aria-label="Filter by status" className="flex flex-wrap gap-2">
        <Chip pressed={!filters.status} onClick={() => onChange({ ...filters, status: undefined })}>
          All
        </Chip>
        {ORDER_STATUSES.map((code) => (
          <Chip
            key={code}
            pressed={filters.status === code}
            onClick={() =>
              onChange({ ...filters, status: filters.status === code ? undefined : code })
            }
          >
            {STATUS_META[code].label}
          </Chip>
        ))}
      </div>
    </div>
  );
}
