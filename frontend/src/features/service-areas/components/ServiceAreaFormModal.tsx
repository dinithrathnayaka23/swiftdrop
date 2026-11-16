import { Plus, Trash2 } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { getErrorMessage, toApiRequestError } from '../../../api/errors';
import { serviceAreasApi } from '../../../api/serviceAreas';
import LoadingState from '../../../components/feedback/LoadingState';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import TextAreaField from '../../../components/ui/TextAreaField';
import TextField from '../../../components/ui/TextField';
import type {
  CoverageArea,
  CoverageAreaInput,
  ServiceAreaDetail,
} from '../../../types/serviceArea';
import Modal from '../../shared-dispatch/components/Modal';

const POSTAL_CODE = /^[A-Za-z0-9 -]{3,15}$/;
const MAX_ROWS = 200;

interface NewRow {
  key: number;
  district: string;
  city: string;
  postalCode: string;
  error?: string;
}

let nextKey = 1;
function emptyRow(): NewRow {
  return { key: nextKey++, district: '', city: '', postalCode: '' };
}

/** The same checks as CoverageAreaRequest; null when the row is fine. */
function rowError(row: NewRow): string | null {
  if (!row.district.trim() || !row.city.trim()) return 'District and city are required';
  if (row.district.trim().length > 100 || row.city.trim().length > 100) {
    return 'District and city must be at most 100 characters';
  }
  if (row.postalCode.trim() && !POSTAL_CODE.test(row.postalCode.trim())) {
    return 'Postal code must be 3-15 letters, digits, spaces or hyphens';
  }
  return null;
}

function toInput(row: NewRow): CoverageAreaInput {
  const postalCode = row.postalCode.trim();
  return {
    district: row.district.trim(),
    city: row.city.trim(),
    ...(postalCode ? { postalCode } : {}),
  };
}

function describe(area: { district: string; city: string; postalCode?: string | null }): string {
  const where = `${area.city}, ${area.district}`;
  return area.postalCode ? `${where} (${area.postalCode})` : where;
}

function validName(name: string): string | undefined {
  const value = name.trim().replace(/\s+/g, ' ');
  if (!value) return 'Name is required';
  if (value.length < 2 || value.length > 100) return 'Name must be 2-100 characters';
  return undefined;
}

function NewRows({
  rows,
  onChange,
  onRemove,
  disabled,
}: {
  rows: NewRow[];
  onChange: (key: number, patch: Partial<NewRow>) => void;
  onRemove: (key: number) => void;
  disabled: boolean;
}) {
  return (
    <ul className="space-y-2">
      {rows.map((row, index) => (
        <li key={row.key}>
          <div className="grid grid-cols-[1fr_1fr_0.8fr_auto] items-center gap-2">
            {(['district', 'city', 'postalCode'] as const).map((field) => (
              <input
                key={field}
                aria-label={`${field === 'postalCode' ? 'Postal code (optional)' : field === 'city' ? 'City' : 'District'}, new row ${index + 1}`}
                placeholder={
                  field === 'postalCode' ? 'Postal code' : field === 'city' ? 'City' : 'District'
                }
                value={row[field]}
                disabled={disabled}
                aria-invalid={row.error ? true : undefined}
                onChange={(e) => onChange(row.key, { [field]: e.target.value, error: undefined })}
                className={inputClasses(Boolean(row.error), 'h-10')}
              />
            ))}
            <button
              type="button"
              onClick={() => onRemove(row.key)}
              disabled={disabled}
              className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-red-600"
              aria-label={`Remove new row ${index + 1}`}
            >
              <Trash2 className="size-4" aria-hidden="true" />
            </button>
          </div>
          {row.error && (
            <p role="alert" className="mt-1 text-sm text-red-600">
              {row.error}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}

interface ServiceAreaFormModalProps {
  /** Edit this zone; leave out to add a new one. */
  zoneId?: string;
  onClose: () => void;
  onSaved: (zone: ServiceAreaDetail) => void;
}

/**
 * Add Service Area (one request with all coverage rows) or edit one: name and description in one
 * PATCH, then each removed and added coverage row in its own request (the API has no bulk replace).
 * If one of those fails the zone is reloaded, so the list shows what is saved, and the failed row is
 * marked.
 */
export default function ServiceAreaFormModal({
  zoneId,
  onClose,
  onSaved,
}: ServiceAreaFormModalProps) {
  const editing = zoneId !== undefined;
  const [zone, setZone] = useState<ServiceAreaDetail | null>(null);
  const [loadError, setLoadError] = useState<string>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [nameError, setNameError] = useState<string>();
  const [removed, setRemoved] = useState<Set<string>>(new Set());
  const [rows, setRows] = useState<NewRow[]>(() => (editing ? [] : [emptyRow()]));
  const [message, setMessage] = useState<string>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!zoneId) return;
    let active = true;
    serviceAreasApi.get(zoneId).then(
      (found) => {
        if (!active) return;
        setZone(found);
        setName(found.name);
        setDescription(found.description ?? '');
      },
      (err: unknown) => active && setLoadError(getErrorMessage(err)),
    );
    return () => {
      active = false;
    };
  }, [zoneId]);

  function changeRow(key: number, patch: Partial<NewRow>) {
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)));
  }

  function removeRow(key: number) {
    setRows((current) => current.filter((row) => row.key !== key));
  }

  /** Checks the name and the new rows; false if something must be fixed first. */
  function check(): boolean {
    const nameProblem = validName(name);
    setNameError(nameProblem);
    const checked = rows.map((row) => ({ ...row, error: rowError(row) ?? undefined }));
    setRows(checked);
    return !nameProblem && checked.every((row) => !row.error);
  }

  async function create() {
    try {
      onSaved(
        await serviceAreasApi.create({
          name: name.trim(),
          description: description.trim() || undefined,
          coverageAreas: rows.map(toInput),
        }),
      );
    } catch (err) {
      const error = toApiRequestError(err);
      const onName = error.fieldErrors.find((f) => f.field === 'name');
      if (onName) setNameError(onName.message);
      else setMessage(error.message);
    }
  }

  async function update(current: ServiceAreaDetail) {
    // 1. Name and description.
    const nameChanged = name.trim() !== current.name;
    const descriptionChanged = description.trim() !== (current.description ?? '');
    if (nameChanged || descriptionChanged) {
      try {
        await serviceAreasApi.update(current.id, {
          ...(nameChanged ? { name: name.trim() } : {}),
          ...(descriptionChanged ? { description: description.trim() } : {}),
        });
      } catch (err) {
        const error = toApiRequestError(err);
        const onName = error.fieldErrors.find((f) => f.field === 'name');
        if (onName) setNameError(onName.message);
        else setMessage(error.message);
        return;
      }
    }
    // 2. Coverage rows, one request each; stop at the first failure.
    let failure: string | undefined;
    for (const area of current.coverageAreas.filter((c) => removed.has(c.id))) {
      try {
        await serviceAreasApi.removeCoverage(current.id, area.id);
      } catch (err) {
        failure = `Couldn't remove ${describe(area)}: ${getErrorMessage(err)}`;
        break;
      }
    }
    if (!failure) {
      for (const row of rows) {
        try {
          await serviceAreasApi.addCoverage(current.id, toInput(row));
          setRows((pending) => pending.filter((r) => r.key !== row.key));
        } catch (err) {
          failure = `Couldn't add ${describe(toInput(row))}: ${getErrorMessage(err)}`;
          changeRow(row.key, { error: getErrorMessage(err) });
          break;
        }
      }
    }
    // 3. Reload from the server: on failure so the lists show what is really saved.
    try {
      const fresh = await serviceAreasApi.get(current.id);
      if (!failure) {
        onSaved(fresh);
        return;
      }
      setZone(fresh);
      setName(fresh.name);
      setDescription(fresh.description ?? '');
      setRemoved(
        (marked) =>
          new Set([...marked].filter((id) => fresh.coverageAreas.some((c) => c.id === id))),
      );
      setMessage(
        `${/[.!?]$/.test(failure) ? failure : `${failure}.`} The changes before it were saved; the list below shows the zone as it is now.`,
      );
    } catch (err) {
      setMessage(failure ?? getErrorMessage(err));
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage(undefined);
    if (!check()) return;
    setSaving(true);
    try {
      if (editing) {
        if (zone) await update(zone);
      } else {
        await create();
      }
    } finally {
      setSaving(false);
    }
  }

  const existing: CoverageArea[] = zone?.coverageAreas ?? [];
  const rowCount = existing.length - removed.size + rows.length;

  return (
    <Modal
      open
      title={editing ? 'Edit Service Area' : 'Add Service Area'}
      description="A place is a district and city, optionally narrowed to one postal code."
      onClose={onClose}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="service-area-form"
            loading={saving}
            disabled={editing && !zone}
          >
            {editing ? 'Save Changes' : 'Add Service Area'}
          </Button>
        </>
      }
    >
      {editing && !zone && !loadError && <LoadingState title="Loading service area…" message="" />}
      {loadError && <Alert tone="error">{loadError}</Alert>}
      {(!editing || zone) && (
        <form id="service-area-form" noValidate onSubmit={submit} className="space-y-4">
          {message && (
            <Alert tone="error" title="Not everything was saved">
              {message}
            </Alert>
          )}
          <TextField
            label="Zone Name"
            name="name"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setNameError(undefined);
            }}
            error={nameError}
          />
          <TextAreaField
            label="Description"
            name="description"
            optional
            rows={2}
            maxLength={1000}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <fieldset>
            <legend className="mb-2 text-sm font-medium text-slate-700">Coverage Areas</legend>
            {existing.length > 0 && (
              <ul className="mb-3 divide-y divide-slate-100 rounded-lg border border-slate-200">
                {existing.map((area) => {
                  const marked = removed.has(area.id);
                  return (
                    <li
                      key={area.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <span className={marked ? 'text-slate-400 line-through' : 'text-slate-700'}>
                        {describe(area)}
                        {!area.active && (
                          <span className="ml-2 text-xs text-slate-500">(inactive)</span>
                        )}
                      </span>
                      <button
                        type="button"
                        disabled={saving}
                        onClick={() =>
                          setRemoved((current) => {
                            const next = new Set(current);
                            if (marked) next.delete(area.id);
                            else next.add(area.id);
                            return next;
                          })
                        }
                        className="rounded px-2 py-1 text-xs font-medium text-slate-600 hover:bg-slate-100"
                        aria-label={`${marked ? 'Keep' : 'Remove'} ${describe(area)}`}
                      >
                        {marked ? 'Undo' : 'Remove'}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
            <NewRows rows={rows} onChange={changeRow} onRemove={removeRow} disabled={saving} />
            <Button
              variant="outline"
              size="sm"
              className="mt-3"
              disabled={saving || rowCount >= MAX_ROWS}
              onClick={() => setRows((current) => [...current, emptyRow()])}
            >
              <Plus className="size-4" aria-hidden="true" />
              Add Row
            </Button>
          </fieldset>
        </form>
      )}
    </Modal>
  );
}
