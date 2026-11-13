import { RefreshCw } from 'lucide-react';
import { useLocation } from 'react-router';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { courierApi } from '../api';
import DeliveryCard from '../components/DeliveryCard';
import { useAutoRefresh } from '../hooks/useAutoRefresh';
import type { CourierDeliveryCounts, CourierNotice } from '../types';

function CountTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-center">
      <p className="text-2xl font-bold text-slate-900">{value}</p>
      <p className="mt-0.5 text-xs font-medium text-slate-500">{label}</p>
    </div>
  );
}

function Counts({ counts }: { counts: CourierDeliveryCounts }) {
  return (
    <div className="grid grid-cols-3 gap-2" aria-label="Delivery counts">
      <CountTile label="To pick up" value={counts.assigned} />
      <CountTile label="In progress" value={counts.inProgress} />
      <CountTile label="Total" value={counts.total} />
    </div>
  );
}

/** The courier's open deliveries, oldest first. Refreshes every minute and when the app regains focus. */
export default function TodaysDeliveriesPage() {
  const { state, refresh, retry } = useAutoRefresh(() => courierApi.getToday());
  const notice = (useLocation().state as Partial<CourierNotice> | null)?.notice;

  return (
    <section>
      {notice && (
        <Alert tone="success" className="mb-4">
          {notice}
        </Alert>
      )}
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Today's Deliveries</h1>
          <p className="mt-1 text-sm text-slate-500">Your assigned deliveries, oldest first.</p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={refresh}
          aria-label="Refresh deliveries"
          disabled={state.status === 'loading'}
        >
          <RefreshCw className="size-4" aria-hidden="true" />
        </Button>
      </div>

      <div className="mt-5">
        {state.status === 'loading' && <LoadingState message="Loading your deliveries." />}

        {state.status === 'error' && (
          <ErrorState
            title="Couldn't load your deliveries"
            message={state.message}
            onAction={retry}
          />
        )}

        {state.status === 'success' && (
          <div className="flex flex-col gap-4">
            {state.stale && (
              <Alert tone="warning">Couldn't refresh. You're seeing the last loaded list.</Alert>
            )}
            <Counts counts={state.data.counts} />
            {state.data.deliveries.length === 0 ? (
              <EmptyState
                title="No deliveries assigned"
                message="New deliveries appear here as soon as the dispatcher assigns them to you."
                onAction={refresh}
                className="rounded-2xl border border-slate-200 bg-white"
              />
            ) : (
              <ul className="flex flex-col gap-3">
                {state.data.deliveries.map((delivery) => (
                  <li key={delivery.parcelId}>
                    <DeliveryCard delivery={delivery} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
