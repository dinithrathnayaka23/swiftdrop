import { CircleCheck, CircleMinus, CircleX, MapPinned, Plus, Power, Search } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { getErrorMessage } from '../../../api/errors';
import { serviceAreasApi } from '../../../api/serviceAreas';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import StateView from '../../../components/feedback/StateView';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { useSettings } from '../../../hooks/useSettings';
import type { ServiceAreaFilters, ServiceAreaRow } from '../../../types/serviceArea';
import Pagination from '../../shared-parcels/components/Pagination';
import RowMenu from '../../shared-parcels/components/RowMenu';
import StatusPill from '../../shared-parcels/components/StatusPill';
import type { Visual } from '../../shared-parcels/components/statusVisuals';
import { useApiData } from '../../shared-parcels/useApiData';
import { useDebouncedValue } from '../../shared-dispatch/useDebouncedValue';
import ServiceAreaFormModal from '../components/ServiceAreaFormModal';

const MAX_PAGE_SIZE = 100;
const ACTIVE: Visual = { label: 'Active', icon: CircleCheck, tone: 'success' };
const INACTIVE: Visual = { label: 'Inactive', icon: CircleMinus, tone: 'danger' };

type StatusFilter = '' | 'active' | 'inactive';

function FilterSelect({
  id,
  label,
  value,
  onChange,
  children,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-xs font-medium text-slate-600">
        {label}
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={inputClasses(false, 'h-10')}
      >
        {children}
      </select>
    </div>
  );
}

/** Admin Service Areas: delivery zones and the places they cover. */
export default function ServiceAreasPage() {
  const { settings } = useSettings();
  const size = Math.min(Math.max(settings.itemsPerPage, 1), MAX_PAGE_SIZE);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput.trim(), 300);
  const [status, setStatus] = useState<StatusFilter>('');
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const [form, setForm] = useState<{ zoneId?: string } | null>(null);
  const [confirming, setConfirming] = useState<ServiceAreaRow | null>(null);
  const [switching, setSwitching] = useState(false);
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string }>();

  const filters: ServiceAreaFilters = {
    search: search || undefined,
    active: status === '' ? undefined : status === 'active',
    page,
    size,
  };
  const list = useApiData(`${JSON.stringify(filters)}#${version}`, () =>
    serviceAreasApi.list(filters),
  );
  const rows = list.status === 'success' ? list.data.content : [];

  const [appliedSearch, setAppliedSearch] = useState(search);
  if (appliedSearch !== search) {
    setAppliedSearch(search);
    setPage(0);
  }

  async function toggleActive(zone: ServiceAreaRow) {
    setSwitching(true);
    setNotice(undefined);
    try {
      if (zone.active) await serviceAreasApi.deactivate(zone.id);
      else await serviceAreasApi.activate(zone.id);
      setNotice({
        tone: 'success',
        text: `${zone.name} is now ${zone.active ? 'inactive' : 'active'}.`,
      });
      setVersion((n) => n + 1);
    } catch (err) {
      // e.g. 409: another active zone already covers one of its places (the message names it).
      setNotice({ tone: 'error', text: getErrorMessage(err) });
    } finally {
      setSwitching(false);
      setConfirming(null);
    }
  }

  return (
    <div className="mx-auto max-w-[1600px] space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Service Areas</h1>
          <p className="mt-1 text-slate-500">Manage delivery zones and the places they cover.</p>
        </div>
        <Button className="bg-brand-800 hover:bg-brand-900" onClick={() => setForm({})}>
          <Plus className="size-4" aria-hidden="true" />
          Add Service Area
        </Button>
      </header>

      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-xs">
        <div className="grid gap-3 p-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] sm:items-end">
          <div className="relative">
            <label htmlFor="areas-search" className="mb-1 block text-xs font-medium text-slate-600">
              Search
            </label>
            <Search
              className="pointer-events-none absolute bottom-3 left-3.5 size-4 text-slate-400"
              aria-hidden="true"
            />
            <input
              id="areas-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Zone name, city, district or postal code..."
              className={inputClasses(false, 'h-10 pl-10')}
            />
          </div>
          <FilterSelect
            id="areas-status"
            label="Status"
            value={status}
            onChange={(value) => {
              setStatus(value as StatusFilter);
              setPage(0);
            }}
          >
            <option value="">All Statuses</option>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </FilterSelect>
        </div>

        {list.status === 'loading' && (
          <div className="space-y-2 px-4 pb-4" aria-busy="true" aria-label="Loading service areas">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}

        {list.status === 'error' && (
          <StateView
            icon={CircleX}
            tone="danger"
            title="Could not load service areas"
            message={list.message}
            actions={
              <Button variant="outline" size="sm" onClick={list.retry}>
                Try again
              </Button>
            }
          />
        )}

        {list.status === 'success' && rows.length === 0 && (
          <StateView
            icon={MapPinned}
            title="No Service Areas Found"
            message="No zones match these filters."
          />
        )}

        {list.status === 'success' && rows.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-xs text-slate-600">
                  <tr>
                    {[
                      'Zone Name',
                      'Cities / Areas',
                      'Postal Code Range',
                      'Coverage',
                      'Status',
                      'Actions',
                    ].map((h) => (
                      <th key={h} scope="col" className="px-4 py-3 font-semibold">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((zone) => (
                    <tr key={zone.id} className="hover:bg-slate-50/60">
                      <td className="px-4 py-3">
                        <p className="font-semibold text-slate-900">{zone.name}</p>
                        {zone.description && (
                          <p className="text-xs text-slate-500">{zone.description}</p>
                        )}
                      </td>
                      <td className="max-w-[320px] px-4 py-3 text-slate-700">
                        {zone.cities.length === 0 ? '—' : zone.cities.join(', ')}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {zone.postalCodeRange?.replace('-', ' – ') ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {zone.coverageCount} {zone.coverageCount === 1 ? 'place' : 'places'}
                      </td>
                      <td className="px-4 py-3">
                        <StatusPill visual={zone.active ? ACTIVE : INACTIVE} />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setForm({ zoneId: zone.id })}
                            aria-label={`Edit ${zone.name}`}
                          >
                            Edit
                          </Button>
                          <RowMenu
                            label={`More actions for ${zone.name}`}
                            vertical
                            items={[
                              {
                                label: zone.active ? 'Deactivate' : 'Activate',
                                onSelect: () => setConfirming(zone),
                              },
                            ]}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 p-4">
              <p className="text-sm text-slate-500">
                Showing {list.data.page.number * list.data.page.size + 1}–
                {list.data.page.number * list.data.page.size + rows.length} of{' '}
                {list.data.page.totalElements} service areas
              </p>
              <Pagination
                page={list.data.page.number}
                totalPages={list.data.page.totalPages}
                onChange={setPage}
              />
            </div>
          </>
        )}
      </section>

      {form && (
        <ServiceAreaFormModal
          zoneId={form.zoneId}
          onClose={() => setForm(null)}
          onSaved={(zone) => {
            setForm(null);
            setNotice({ tone: 'success', text: `${zone.name} was saved.` });
            setVersion((n) => n + 1);
          }}
        />
      )}

      <ConfirmDialog
        open={confirming !== null}
        icon={Power}
        title={
          confirming?.active
            ? `Deactivate ${confirming.name}?`
            : `Activate ${confirming?.name ?? ''}?`
        }
        message={
          confirming?.active
            ? 'Fee estimates and courier areas stop using this zone. You can activate it again later.'
            : 'The zone is checked first: no other active zone may cover the same places.'
        }
        confirmLabel={confirming?.active ? 'Deactivate' : 'Activate'}
        loading={switching}
        onConfirm={() => confirming && toggleActive(confirming)}
        onCancel={() => setConfirming(null)}
      />
    </div>
  );
}
