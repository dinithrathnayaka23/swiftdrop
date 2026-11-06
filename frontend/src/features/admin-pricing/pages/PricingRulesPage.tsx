import { Pencil, Plus, Power } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import { pricingApi } from '../../../api/pricing';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import SelectField from '../../../components/ui/SelectField';
import Spinner from '../../../components/ui/Spinner';
import TextField from '../../../components/ui/TextField';
import type { PricingRule } from '../../../types/pricing';
import PricingRuleForm from '../components/PricingRuleForm';

type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
];

type Panel = { mode: 'new' } | { mode: 'edit'; rule: PricingRule } | null;

function formatAmount(amount: number): string {
  return new Intl.NumberFormat('en-LK', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export default function PricingRulesPage() {
  const [rules, setRules] = useState<PricingRule[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [panel, setPanel] = useState<Panel>(null);
  const [toDeactivate, setToDeactivate] = useState<PricingRule | null>(null);
  const [deactivating, setDeactivating] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(() => {
    pricingApi
      .listRules()
      .then((list) => {
        setLoadError(null);
        setRules(list);
      })
      .catch((err: unknown) => setLoadError(getErrorMessage(err)));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (rules ?? []).filter((rule) => {
      const matchesSearch = rule.ruleName.toLowerCase().includes(term);
      const matchesStatus = status === 'ALL' || (status === 'ACTIVE') === rule.active;
      return matchesSearch && matchesStatus;
    });
  }, [rules, search, status]);

  function upsert(saved: PricingRule) {
    setRules((current) => {
      const list = current ?? [];
      return list.some((r) => r.id === saved.id)
        ? list.map((r) => (r.id === saved.id ? saved : r))
        : [saved, ...list];
    });
  }

  function handleSaved(saved: PricingRule) {
    upsert(saved);
    setPanel(null);
    setNotice(`Rule "${saved.ruleName}" saved.`);
  }

  async function deactivate() {
    if (!toDeactivate) return;
    setDeactivating(true);
    setActionError(null);
    try {
      upsert(await pricingApi.deactivateRule(toDeactivate.id));
      setNotice(`Rule "${toDeactivate.ruleName}" deactivated.`);
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setDeactivating(false);
      setToDeactivate(null);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Pricing Rules</h1>
          <p className="mt-1 text-slate-600">
            Manage the delivery prices used for fee estimates and bookings.
          </p>
        </div>
        <Button onClick={() => setPanel({ mode: 'new' })}>
          <Plus className="size-4" aria-hidden="true" />
          Add Pricing Rule
        </Button>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {actionError && <Alert tone="error">{actionError}</Alert>}

      {panel && (
        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
          <h2 className="mb-4 text-lg font-semibold text-slate-900">
            {panel.mode === 'new' ? 'Add pricing rule' : 'Edit pricing rule'}
          </h2>
          <PricingRuleForm
            key={panel.mode === 'edit' ? panel.rule.id : 'new'}
            rule={panel.mode === 'edit' ? panel.rule : null}
            onSaved={handleSaved}
            onCancel={() => setPanel(null)}
          />
        </section>
      )}

      <div className="grid gap-4 md:grid-cols-[1fr_16rem]">
        <TextField
          label="Search"
          placeholder="Search by rule name"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <SelectField
          label="Status"
          options={STATUS_OPTIONS}
          value={status}
          onChange={(e) => setStatus(e.target.value as StatusFilter)}
        />
      </div>

      {rules === null && !loadError && (
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <Spinner className="size-4" />
          Loading pricing rules...
        </div>
      )}

      {loadError && <ErrorState message={loadError} actionLabel="Try Again" onAction={load} />}

      {rules !== null && !loadError && visible.length === 0 && (
        <EmptyState
          title="No pricing rules found"
          message="Try a different search or status, or add a new rule."
        />
      )}

      {rules !== null && !loadError && visible.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-600">
              <tr>
                <th className="px-4 py-3 font-medium">Rule name</th>
                <th className="px-4 py-3 font-medium">Base fee (LKR)</th>
                <th className="px-4 py-3 font-medium">Included weight</th>
                <th className="px-4 py-3 font-medium">Extra per kg (LKR)</th>
                <th className="px-4 py-3 font-medium">Oversize fee (LKR)</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {visible.map((rule) => (
                <tr key={rule.id}>
                  <td className="px-4 py-3 font-medium text-slate-900">{rule.ruleName}</td>
                  <td className="px-4 py-3">{formatAmount(rule.baseFee)}</td>
                  <td className="px-4 py-3">{rule.includedWeightKg} kg</td>
                  <td className="px-4 py-3">{formatAmount(rule.additionalFeePerKg)}</td>
                  <td className="px-4 py-3">{formatAmount(rule.oversizeFee)}</td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        rule.active
                          ? 'rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700'
                          : 'rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600'
                      }
                    >
                      {rule.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setPanel({ mode: 'edit', rule })}
                      >
                        <Pencil className="size-3.5" aria-hidden="true" />
                        Edit
                      </Button>
                      {rule.active && (
                        <Button variant="ghost" size="sm" onClick={() => setToDeactivate(rule)}>
                          <Power className="size-3.5" aria-hidden="true" />
                          Deactivate
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={toDeactivate !== null}
        title="Deactivate this rule?"
        message={`"${toDeactivate?.ruleName ?? ''}" will no longer be used for new fee estimates. Parcels already booked keep their price.`}
        confirmLabel="Deactivate"
        icon={Power}
        loading={deactivating}
        onConfirm={deactivate}
        onCancel={() => setToDeactivate(null)}
      />
    </div>
  );
}
