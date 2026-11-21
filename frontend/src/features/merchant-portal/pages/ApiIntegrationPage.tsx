import { Ban, CircleX, KeyRound, Plus, RefreshCw, TriangleAlert } from 'lucide-react';
import { useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import StateView from '../../../components/feedback/StateView';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { cn } from '../../../utils/cn';
import SetupStepsPanel from '../../merchant-webhooks/components/SetupStepsPanel';
import CopyButton from '../../shared-parcels/components/CopyButton';
import SectionCard from '../../shared-parcels/components/SectionCard';
import { formatDate, formatRelative, formatTime } from '../../shared-parcels/parcelDisplay';
import { useApiData } from '../../shared-parcels/useApiData';
import { merchantPortalApi } from '../api';
import type { ApiKey, ApiKeyStatus, GeneratedApiKey } from '../types';

const STATUS_STYLES: Record<ApiKeyStatus, { label: string; className: string }> = {
  ACTIVE: { label: 'Active', className: 'bg-brand-50 text-brand-700' },
  REVOKED: { label: 'Revoked', className: 'bg-slate-100 text-slate-600' },
  EXPIRED: { label: 'Expired', className: 'bg-amber-50 text-amber-700' },
};

/** What the merchant asked to do to a key, waiting for confirmation. */
type PendingAction = { kind: 'revoke' | 'rotate'; key: ApiKey };

/** The one-time view of a key that was just created: it can't be shown again. */
function NewKeyBanner({ apiKey, onDone }: { apiKey: GeneratedApiKey; onDone: () => void }) {
  return (
    <div role="status" className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
      <div className="flex gap-3">
        <TriangleAlert className="mt-0.5 size-5 shrink-0 text-amber-600" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-slate-900">Copy your new API key now</p>
          <p className="mt-1 text-sm text-slate-600">
            For your security we only show it once. Store it on your server; if you lose it, rotate
            the key to get a new one.
          </p>
          <div className="mt-3 flex items-center gap-2 rounded-lg border border-amber-200 bg-white px-3 py-2">
            <code className="min-w-0 flex-1 font-mono text-sm break-all text-slate-900">
              {apiKey.apiKey}
            </code>
            <CopyButton value={apiKey.apiKey} label="Copy API key" />
          </div>
          <Button variant="outline" size="sm" className="mt-3" onClick={onDone}>
            I've saved it
          </Button>
        </div>
      </div>
    </div>
  );
}

function KeysTable({
  keys,
  busy,
  onAction,
}: {
  keys: ApiKey[];
  busy: boolean;
  onAction: (action: PendingAction) => void;
}) {
  return (
    // relative: keeps the table's sr-only labels (position: absolute) inside this scroll box
    <div className="relative overflow-x-auto rounded-xl border border-slate-200">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium whitespace-nowrap text-slate-600">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">
              Key
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Created
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Last Used
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {keys.map((key) => {
            const status = STATUS_STYLES[key.status];
            return (
              <tr key={key.apiCredentialId}>
                <td className="px-4 py-3 font-mono whitespace-nowrap text-slate-900">
                  sd_{key.keyId}_••••
                </td>
                <td className="px-4 py-3">
                  <span
                    className={cn(
                      'inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold',
                      status.className,
                    )}
                  >
                    {status.label}
                  </span>
                </td>
                <td className="px-4 py-3 whitespace-nowrap text-slate-700">
                  {formatDate(key.createdAt)}
                  <span className="block text-xs text-slate-500">{formatTime(key.createdAt)}</span>
                </td>
                <td className="px-4 py-3 whitespace-nowrap text-slate-700">
                  {key.lastUsedAt ? formatRelative(key.lastUsedAt) : 'Never'}
                </td>
                <td className="px-4 py-3">
                  {key.status === 'ACTIVE' && (
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={busy}
                        onClick={() => onAction({ kind: 'rotate', key })}
                      >
                        <RefreshCw className="size-4" aria-hidden="true" />
                        Rotate
                        <span className="sr-only"> key {key.keyId}</span>
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={busy}
                        className="text-red-700 hover:bg-red-50"
                        onClick={() => onAction({ kind: 'revoke', key })}
                      >
                        <Ban className="size-4" aria-hidden="true" />
                        Revoke
                        <span className="sr-only"> key {key.keyId}</span>
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/** Merchant: create, revoke and rotate API keys, and the steps to connect a store. */
export default function ApiIntegrationPage() {
  const keys = useApiData('api-keys', () => merchantPortalApi.listApiKeys());
  const [newKey, setNewKey] = useState<GeneratedApiKey | null>(null);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Runs a key change, then reloads the list so statuses and dates come from the server. */
  async function change(run: () => Promise<GeneratedApiKey | ApiKey>) {
    setBusy(true);
    setError(null);
    try {
      const result = await run();
      if ('apiKey' in result) setNewKey(result);
      keys.retry();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
      setPending(null);
    }
  }

  function confirmPending() {
    if (!pending) return;
    const id = pending.key.apiCredentialId;
    void change(() =>
      pending.kind === 'revoke'
        ? merchantPortalApi.revokeApiKey(id)
        : merchantPortalApi.rotateApiKey(id),
    );
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">API Integration</h1>
        <p className="mt-1 text-slate-500">
          Manage the API keys your store uses to send orders to SwiftDrop.
        </p>
      </header>

      {newKey && <NewKeyBanner apiKey={newKey} onDone={() => setNewKey(null)} />}
      {error && <Alert tone="error">{error}</Alert>}

      <div className="grid grid-cols-1 gap-6 2xl:grid-cols-[minmax(0,1fr)_420px] 2xl:items-start">
        <SectionCard
          title="API Keys"
          subtitle="Send a key in the X-API-Key header of every order API request."
          action={
            <Button
              size="sm"
              loading={busy && !pending}
              disabled={busy}
              onClick={() => void change(() => merchantPortalApi.createApiKey())}
            >
              <Plus className="size-4" aria-hidden="true" />
              Create API key
            </Button>
          }
        >
          {keys.status === 'loading' && (
            <div className="space-y-2" aria-busy="true" aria-label="Loading API keys">
              {Array.from({ length: 3 }, (_, i) => (
                <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
              ))}
            </div>
          )}
          {keys.status === 'error' && (
            <StateView
              icon={CircleX}
              tone="danger"
              title="Could not load your API keys"
              message={keys.message}
              actions={
                <Button variant="outline" size="sm" onClick={keys.retry}>
                  Try again
                </Button>
              }
            />
          )}
          {keys.status === 'success' && keys.data.length === 0 && (
            <StateView
              icon={KeyRound}
              title="No API keys yet"
              message="Create a key to start sending orders from your store."
            />
          )}
          {keys.status === 'success' && keys.data.length > 0 && (
            <KeysTable keys={keys.data} busy={busy} onAction={setPending} />
          )}
        </SectionCard>

        <SetupStepsPanel />
      </div>

      <ConfirmDialog
        open={pending?.kind === 'revoke'}
        title="Revoke API key?"
        message={`Key sd_${pending?.key.keyId ?? ''} stops working immediately. Orders sent with it will be rejected.`}
        confirmLabel="Revoke key"
        icon={Ban}
        loading={busy}
        onConfirm={confirmPending}
        onCancel={() => setPending(null)}
      />
      <ConfirmDialog
        open={pending?.kind === 'rotate'}
        title="Rotate API key?"
        message={`Key sd_${pending?.key.keyId ?? ''} stops working immediately and a new key is created. Update your store with the new key straight away.`}
        confirmLabel="Rotate key"
        icon={RefreshCw}
        loading={busy}
        onConfirm={confirmPending}
        onCancel={() => setPending(null)}
      />
    </div>
  );
}
