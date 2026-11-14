import { Search } from 'lucide-react';
import { useState } from 'react';
import { dispatchApi } from '../../../api/dispatch';
import { getErrorMessage } from '../../../api/errors';
import { parcelsApi } from '../../../api/parcels';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { useDateFormat } from '../../../hooks/useDateFormat';
import type { Courier } from '../../../types/courier';
import { MAX_BULK_PARCELS, type BulkAssignResult } from '../../../types/dispatch';
import Modal from '../../shared-dispatch/components/Modal';
import { useDebouncedValue } from '../../shared-dispatch/useDebouncedValue';
import Pagination from '../../shared-parcels/components/Pagination';
import { useApiData } from '../../shared-parcels/useApiData';
import BulkResultSummary from './BulkResultSummary';

const PAGE_SIZE = 10;

interface AssignParcelsToCourierDialogProps {
  courier: Courier;
  onClose: () => void;
  /** After each bulk request, so the page behind can refresh. */
  onAssigned: () => void;
}

/** Courier first: pick unassigned parcels for this courier, then one bulk request. */
export default function AssignParcelsToCourierDialog({
  courier,
  onClose,
  onAssigned,
}: AssignParcelsToCourierDialogProps) {
  const { formatShortDateTime } = useDateFormat();
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput.trim(), 300);
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  // id -> tracking number, kept across pages and searches.
  const [selected, setSelected] = useState<Map<string, string>>(new Map());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [outcome, setOutcome] = useState<{
    result: BulkAssignResult;
    tracking: Map<string, string>;
  }>();

  const [appliedSearch, setAppliedSearch] = useState(search);
  if (appliedSearch !== search) {
    setAppliedSearch(search);
    setPage(0);
  }

  const list = useApiData(`unassigned|${search}|${page}#${version}`, () =>
    parcelsApi.listAdminParcels({
      tab: 'UNASSIGNED',
      search: search || undefined,
      page,
      size: PAGE_SIZE,
    }),
  );
  const rows = list.status === 'success' ? list.data.content : [];
  const tooMany = selected.size > MAX_BULK_PARCELS;

  function toggle(id: string, trackingNumber: string) {
    setSelected((current) => {
      const next = new Map(current);
      if (next.has(id)) next.delete(id);
      else next.set(id, trackingNumber);
      return next;
    });
  }

  async function assign() {
    if (selected.size === 0 || tooMany || saving) return;
    setSaving(true);
    setError(undefined);
    const tracking = new Map(selected);
    try {
      const result = await dispatchApi.assignAll(courier.id, [...tracking.keys()]);
      setOutcome({ result, tracking });
      // Assigned parcels leave the selection; the failed ones stay for another try.
      const done = new Set(result.assigned.map((a) => a.parcelId));
      setSelected((current) => new Map([...current].filter(([id]) => !done.has(id))));
      setVersion((n) => n + 1);
      onAssigned();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      size="lg"
      title={`Assign Parcels to ${courier.fullName}`}
      description="Choose parcels waiting for a courier. Each parcel is assigned on its own."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Close
          </Button>
          <Button
            onClick={assign}
            loading={saving}
            disabled={selected.size === 0 || tooMany || saving}
            aria-describedby={tooMany ? 'assign-parcels-limit' : undefined}
          >
            {selected.size === 1 ? 'Assign 1 Parcel' : `Assign ${selected.size} Parcels`}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {outcome && (
          <BulkResultSummary
            result={outcome.result}
            courierName={courier.fullName}
            trackingOf={(id) => outcome.tracking.get(id)}
          />
        )}
        {error && <Alert tone="error">{error}</Alert>}

        <div className="relative">
          <label
            htmlFor="assign-parcels-search"
            className="mb-1 block text-sm font-medium text-slate-700"
          >
            Search parcels
          </label>
          <Search
            className="pointer-events-none absolute bottom-3 left-3.5 size-4 text-slate-400"
            aria-hidden="true"
          />
          <input
            id="assign-parcels-search"
            type="search"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Tracking number, recipient or merchant..."
            className={inputClasses(false, 'h-10 pl-10')}
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
          <p className="text-slate-600" aria-live="polite">
            {selected.size} selected
          </p>
          {selected.size > 0 && (
            <Button variant="ghost" size="sm" onClick={() => setSelected(new Map())}>
              Clear selection
            </Button>
          )}
        </div>
        {tooMany && (
          <p id="assign-parcels-limit" className="text-sm text-red-600">
            At most {MAX_BULK_PARCELS} parcels can be assigned at once. Deselect{' '}
            {selected.size - MAX_BULK_PARCELS} to continue.
          </p>
        )}

        {list.status === 'loading' && (
          <div className="space-y-2" aria-busy="true" aria-label="Loading parcels">
            {Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}
        {list.status === 'error' && (
          <Alert tone="error">
            {list.message}{' '}
            <button type="button" className="font-semibold underline" onClick={list.retry}>
              Try again
            </button>
          </Alert>
        )}
        {list.status === 'success' && rows.length === 0 && (
          <p className="rounded-lg border border-slate-200 px-3 py-4 text-center text-sm text-slate-500">
            {search
              ? 'No waiting parcel matches this search.'
              : 'No parcels are waiting for a courier.'}
          </p>
        )}
        {rows.length > 0 && (
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
            {rows.map((row) => (
              <li key={row.id}>
                <label className="flex cursor-pointer items-start gap-3 px-3 py-2.5 text-sm hover:bg-slate-50">
                  <input
                    type="checkbox"
                    checked={selected.has(row.id)}
                    disabled={saving}
                    onChange={() => toggle(row.id, row.trackingNumber)}
                    aria-label={`Select #${row.trackingNumber}`}
                    className="mt-1 accent-brand-700"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap justify-between gap-x-3">
                      <span className="font-semibold text-slate-900">#{row.trackingNumber}</span>
                      <span className="text-xs text-slate-500">
                        {formatShortDateTime(row.createdAt)}
                      </span>
                    </span>
                    <span className="block text-xs text-slate-600">
                      {row.destination} · {row.weightKg} kg
                      {row.merchantName ? ` · ${row.merchantName}` : ''}
                    </span>
                  </span>
                </label>
              </li>
            ))}
          </ul>
        )}
        {list.status === 'success' && list.data.page.totalPages > 1 && (
          <div className="flex justify-center">
            <Pagination
              page={list.data.page.number}
              totalPages={list.data.page.totalPages}
              onChange={setPage}
            />
          </div>
        )}
      </div>
    </Modal>
  );
}
