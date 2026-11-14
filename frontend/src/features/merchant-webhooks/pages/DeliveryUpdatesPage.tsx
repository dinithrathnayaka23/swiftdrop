import { Check, Copy, RotateCcw, Save } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { getErrorMessage } from '../../../api/errors';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import LoadingState from '../../../components/feedback/LoadingState';
import { ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import Checkbox from '../../../components/ui/Checkbox';
import Switch from '../../../components/ui/Switch';
import TextField from '../../../components/ui/TextField';
import DeliveryLogTable from '../components/DeliveryLogTable';
import { merchantWebhooksApi, type WebhookSettings } from '../api';

const STATUS_EVENT = 'parcel.status.changed';

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; settings: WebhookSettings };

export default function DeliveryUpdatesPage() {
  const [load, setLoad] = useState<LoadState>({ status: 'loading' });
  const [endpointUrl, setEndpointUrl] = useState('');
  const [active, setActive] = useState(false);
  const [eventTypes, setEventTypes] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [rotating, setRotating] = useState(false);
  const [confirmRotate, setConfirmRotate] = useState(false);
  const [secret, setSecret] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deliveryRefreshKey, setDeliveryRefreshKey] = useState(0);

  const applySettings = useCallback((settings: WebhookSettings) => {
    setEndpointUrl(settings.endpointUrl);
    setActive(settings.active);
    setEventTypes(settings.eventTypes);
    setSecret(settings.signingSecret);
  }, []);

  const loadSettings = useCallback(() => {
    setLoad({ status: 'loading' });
    merchantWebhooksApi.get().then(
      (settings) => {
        applySettings(settings);
        setLoad({ status: 'ready', settings });
      },
      (error: unknown) => setLoad({ status: 'error', message: getErrorMessage(error) }),
    );
  }, [applySettings]);

  useEffect(() => {
    let active = true;
    merchantWebhooksApi.get().then(
      (settings) => {
        if (!active) return;
        applySettings(settings);
        setLoad({ status: 'ready', settings });
      },
      (error: unknown) => {
        if (active) setLoad({ status: 'error', message: getErrorMessage(error) });
      },
    );
    return () => {
      active = false;
    };
  }, [applySettings]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setActionError(null);
    setNotice(null);
    setSecret(null);
    try {
      const settings = await merchantWebhooksApi.update({
        endpointUrl: endpointUrl.trim(),
        eventTypes,
        active,
      });
      applySettings(settings);
      setLoad({ status: 'ready', settings });
      setNotice('Webhook settings saved.');
      setDeliveryRefreshKey((key) => key + 1);
    } catch (error) {
      setActionError(getErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function rotateSecret() {
    setRotating(true);
    setActionError(null);
    setNotice(null);
    setCopied(false);
    try {
      const settings = await merchantWebhooksApi.rotateSecret();
      applySettings(settings);
      setLoad({ status: 'ready', settings });
      setNotice('Signing secret rotated. Copy it now; it will not be shown again.');
    } catch (error) {
      setActionError(getErrorMessage(error));
    } finally {
      setRotating(false);
      setConfirmRotate(false);
    }
  }

  async function copySecret() {
    if (!secret) return;
    await navigator.clipboard.writeText(secret);
    setCopied(true);
  }

  if (load.status === 'loading') return <LoadingState title="Loading webhook settings..." />;
  if (load.status === 'error') {
    return <ErrorState message={load.message} onAction={loadSettings} />;
  }

  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-slate-900">Delivery Updates</h1>
        <p className="mt-1 text-slate-600">
          Configure where SwiftDrop sends signed parcel status updates.
        </p>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {actionError && <Alert tone="error">{actionError}</Alert>}
      {secret && (
        <Alert tone="warning" title="Copy your signing secret now">
          <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-center">
            <code className="min-w-0 flex-1 rounded bg-white/70 px-2 py-1 text-xs break-all">
              {secret}
            </code>
            <Button type="button" variant="outline" size="sm" onClick={copySecret}>
              {copied ? (
                <Check className="size-4" aria-hidden="true" />
              ) : (
                <Copy className="size-4" aria-hidden="true" />
              )}
              {copied ? 'Copied' : 'Copy secret'}
            </Button>
          </div>
        </Alert>
      )}

      <form
        onSubmit={save}
        noValidate
        className="space-y-6 rounded-xl border border-slate-200 bg-white p-6 shadow-xs"
      >
        <TextField
          label="Webhook URL"
          type="url"
          placeholder="https://merchant.example.com/webhooks/swiftdrop"
          value={endpointUrl}
          onChange={(event) => setEndpointUrl(event.target.value)}
          required
          maxLength={2048}
          hint="Use an HTTPS endpoint that can receive POST requests."
        />

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Events</legend>
          <p className="mt-1 text-sm text-slate-500">
            Select the parcel events your integration should receive.
          </p>
          <div className="mt-3">
            <Checkbox
              label="Parcel status changes"
              checked={eventTypes.includes(STATUS_EVENT)}
              onChange={(event) => setEventTypes(event.target.checked ? [STATUS_EVENT] : [])}
            />
          </div>
        </fieldset>

        <Switch
          label="Webhook delivery"
          description="When disabled, status events will not be sent to this endpoint."
          checked={active}
          onChange={setActive}
        />

        <div className="flex flex-col-reverse gap-3 border-t border-slate-200 pt-5 sm:flex-row sm:justify-end">
          <Button
            type="button"
            variant="outline"
            onClick={() => setConfirmRotate(true)}
            disabled={!load.settings.webhookId || rotating}
          >
            <RotateCcw className="size-4" aria-hidden="true" />
            Rotate secret
          </Button>
          <Button type="submit" loading={saving} disabled={eventTypes.length === 0}>
            {!saving && <Save className="size-4" aria-hidden="true" />}
            {saving ? 'Saving...' : 'Save settings'}
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={confirmRotate}
        title="Rotate the signing secret?"
        message="The current secret will stop working immediately. You will only see the new secret once."
        confirmLabel="Rotate secret"
        icon={RotateCcw}
        loading={rotating}
        onConfirm={rotateSecret}
        onCancel={() => setConfirmRotate(false)}
      />
      <DeliveryLogTable refreshKey={deliveryRefreshKey} />
    </section>
  );
}
