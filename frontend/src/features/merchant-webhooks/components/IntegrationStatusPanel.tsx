import { CircleCheck, CircleX, Clock, KeyRound, Webhook } from 'lucide-react';
import SectionCard from '../../shared-parcels/components/SectionCard';

export interface SuccessfulWebhookDelivery {
  eventType: string;
  trackingNumber?: string | null;
  deliveredAt: string;
}

interface IntegrationStatusPanelProps {
  apiKeyActive: boolean;
  webhookConfigured: boolean;
  lastSuccessfulDelivery?: SuccessfulWebhookDelivery | null;
}

function Status({ active }: { active: boolean }) {
  const Icon = active ? CircleCheck : CircleX;
  return (
    <span
      className={`inline-flex items-center gap-1.5 text-sm font-medium ${
        active ? 'text-brand-700' : 'text-red-700'
      }`}
    >
      <Icon className="size-4" aria-hidden="true" />
      {active ? 'Active' : 'Not configured'}
    </span>
  );
}

export default function IntegrationStatusPanel({
  apiKeyActive,
  webhookConfigured,
  lastSuccessfulDelivery,
}: IntegrationStatusPanelProps) {
  return (
    <SectionCard
      title="Integration status"
      subtitle="A quick view of your merchant API connection."
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 p-4">
          <KeyRound className="size-5 text-slate-500" aria-hidden="true" />
          <div>
            <p className="text-sm font-medium text-slate-900">API key</p>
            <Status active={apiKeyActive} />
          </div>
        </div>
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 p-4">
          <Webhook className="size-5 text-slate-500" aria-hidden="true" />
          <div>
            <p className="text-sm font-medium text-slate-900">Webhook</p>
            <Status active={webhookConfigured} />
          </div>
        </div>
      </div>

      <div className="mt-4 rounded-lg bg-slate-50 p-4">
        <div className="flex items-start gap-3">
          <Clock className="mt-0.5 size-5 text-slate-500" aria-hidden="true" />
          <div className="min-w-0">
            <p className="text-sm font-medium text-slate-900">Last successful delivery</p>
            {lastSuccessfulDelivery ? (
              <p className="mt-1 text-sm text-slate-600">
                {lastSuccessfulDelivery.eventType}
                {lastSuccessfulDelivery.trackingNumber &&
                  ` · ${lastSuccessfulDelivery.trackingNumber}`}
                <span className="block text-xs text-slate-500">
                  {lastSuccessfulDelivery.deliveredAt}
                </span>
              </p>
            ) : (
              <p className="mt-1 text-sm text-slate-500">No successful deliveries yet.</p>
            )}
          </div>
        </div>
      </div>
    </SectionCard>
  );
}
