import { ArrowRight, CircleCheck, CircleX, ClipboardList, PackageOpen, Truck } from 'lucide-react';
import { generatePath, Link } from 'react-router';
import StateView from '../../../components/feedback/StateView';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { PATHS } from '../../../routes/paths';
import IntegrationStatusPanel from '../../merchant-webhooks/components/IntegrationStatusPanel';
import { merchantWebhooksApi } from '../../merchant-webhooks/api';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatCard from '../../shared-parcels/components/StatCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { countActive, countAll, formatDate, formatTime } from '../../shared-parcels/parcelDisplay';
import { useApiData } from '../../shared-parcels/useApiData';
import { merchantPortalApi } from '../api';
import type { MerchantOrderSummary } from '../types';

const RECENT_ORDERS = 5;

/** API key, webhook and last successful webhook delivery, loaded together for the status panel. */
async function loadIntegration() {
  const [keys, webhook, delivered] = await Promise.all([
    merchantPortalApi.listApiKeys(),
    merchantWebhooksApi.get(),
    merchantWebhooksApi.listDeliveries(0, 'DELIVERED'),
  ]);
  const last = delivered.content[0];
  return {
    apiKeyActive: keys.some((key) => key.status === 'ACTIVE'),
    webhookConfigured: webhook.active && webhook.endpointUrl !== '',
    lastSuccessfulDelivery:
      last?.deliveredAt != null
        ? {
            eventType: last.eventType,
            trackingNumber: last.trackingNumber,
            deliveredAt: `${formatDate(last.deliveredAt)}, ${formatTime(last.deliveredAt)}`,
          }
        : null,
  };
}

function orderLink(trackingNumber: string) {
  return generatePath(PATHS.merchantOrderDetail, { trackingNumber });
}

function RecentOrders({ rows }: { rows: MerchantOrderSummary[] }) {
  if (rows.length === 0) {
    return (
      <StateView
        icon={PackageOpen}
        title="No orders yet"
        message="Orders you send through the SwiftDrop API will show up here."
        actions={
          <ButtonLink to={PATHS.merchantIntegration} size="sm">
            Set up the API
          </ButtonLink>
        }
      />
    );
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium text-slate-600">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">
              Tracking Number
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Order Ref
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Recipient
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Created
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Status
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row) => (
            <tr key={row.parcelId}>
              <td className="px-4 py-3 font-medium">
                <Link
                  to={orderLink(row.trackingNumber)}
                  className="text-brand-700 hover:text-brand-800 hover:underline"
                >
                  {row.trackingNumber}
                </Link>
              </td>
              <td className="px-4 py-3 text-slate-600">{row.externalOrderReference ?? '—'}</td>
              <td className="px-4 py-3 text-slate-700">
                {row.recipientName ?? '—'}
                {row.deliveryCity && (
                  <span className="block text-xs text-slate-500">{row.deliveryCity}</span>
                )}
              </td>
              <td className="px-4 py-3 text-slate-700">
                {formatDate(row.createdAt)}
                <span className="block text-xs text-slate-500">{formatTime(row.createdAt)}</span>
              </td>
              <td className="px-4 py-3">
                <StatusPill visual={statusVisual(row.status)} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Merchant home: order counts, recent orders and the API integration status. */
export default function MerchantDashboardPage() {
  const profile = useApiData('profile', () => merchantPortalApi.getProfile());
  const counts = useApiData('counts', () => merchantPortalApi.getOrderCounts());
  const recent = useApiData('recent', () => merchantPortalApi.listOrders(0, RECENT_ORDERS));
  const integration = useApiData('integration', loadIntegration);
  const countsData = counts.status === 'success' ? counts.data : null;
  const countsLoading = counts.status === 'loading';

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
          {profile.status === 'success' ? profile.data.businessName : 'Dashboard'}
        </h1>
        <p className="mt-1 text-slate-500">
          An overview of your orders and your SwiftDrop API integration.
        </p>
      </header>

      {counts.status === 'error' && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
          Could not load your order counts. {counts.message}{' '}
          <button type="button" className="font-semibold underline" onClick={counts.retry}>
            Try again
          </button>
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Total Orders"
          value={countsData ? countAll(countsData) : 0}
          caption="All orders sent to SwiftDrop"
          icon={ClipboardList}
          tone="neutral"
          to={PATHS.merchantOrders}
          loading={countsLoading}
        />
        <StatCard
          label="Active"
          value={countsData ? countActive(countsData) : 0}
          caption="Waiting for pickup or on the way"
          icon={Truck}
          tone="info"
          loading={countsLoading}
        />
        <StatCard
          label="Delivered"
          value={countsData?.DELIVERED ?? 0}
          caption="Successfully delivered"
          icon={CircleCheck}
          tone="success"
          loading={countsLoading}
        />
        <StatCard
          label="Failed"
          value={countsData?.FAILED ?? 0}
          caption="Could not be delivered"
          icon={CircleX}
          tone="danger"
          loading={countsLoading}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <SectionCard
          title="Recent Orders"
          subtitle="Your latest orders"
          action={
            <Link
              to={PATHS.merchantOrders}
              className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800"
            >
              View all orders
              <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          }
        >
          {recent.status === 'loading' && (
            <div className="space-y-2" aria-busy="true" aria-label="Loading recent orders">
              {Array.from({ length: 4 }, (_, i) => (
                <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
              ))}
            </div>
          )}
          {recent.status === 'error' && (
            <StateView
              icon={CircleX}
              tone="danger"
              title="Could not load your orders"
              message={recent.message}
              actions={
                <Button variant="outline" size="sm" onClick={recent.retry}>
                  Try again
                </Button>
              }
            />
          )}
          {recent.status === 'success' && <RecentOrders rows={recent.data.content} />}
        </SectionCard>

        <div className="min-w-0">
          {integration.status === 'loading' && (
            <div
              className="h-64 animate-pulse rounded-2xl bg-slate-100"
              aria-busy="true"
              aria-label="Loading integration status"
            />
          )}
          {integration.status === 'error' && (
            <SectionCard title="Integration status">
              <StateView
                icon={CircleX}
                tone="danger"
                title="Could not load your integration status"
                message={integration.message}
                actions={
                  <Button variant="outline" size="sm" onClick={integration.retry}>
                    Try again
                  </Button>
                }
              />
            </SectionCard>
          )}
          {integration.status === 'success' && <IntegrationStatusPanel {...integration.data} />}
        </div>
      </div>
    </div>
  );
}
