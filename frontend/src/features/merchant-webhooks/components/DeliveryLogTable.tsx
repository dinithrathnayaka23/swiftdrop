import { CircleCheck, CircleX, Clock, RotateCcw, Send } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import SelectField from '../../../components/ui/SelectField';
import { useDateFormat } from '../../../hooks/useDateFormat';
import Pagination from '../../shared-parcels/components/Pagination';
import StatusPill from '../../shared-parcels/components/StatusPill';
import type { WebhookDelivery, WebhookDeliveryStatus, WebhookTestResult } from '../api';
import { merchantWebhooksApi } from '../api';

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'FAILED', label: 'Failed' },
];

function statusVisual(status: WebhookDeliveryStatus) {
  switch (status) {
    case 'DELIVERED':
      return { label: 'Delivered', icon: CircleCheck, tone: 'success' as const };
    case 'FAILED':
      return { label: 'Failed', icon: CircleX, tone: 'danger' as const };
    default:
      return { label: 'Pending', icon: Clock, tone: 'warning' as const };
  }
}

interface DeliveryLogTableProps {
  refreshKey?: number;
}

export default function DeliveryLogTable({ refreshKey = 0 }: DeliveryLogTableProps) {
  const { formatShortDateTime } = useDateFormat();
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [status, setStatus] = useState<WebhookDeliveryStatus | ''>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState<string | null>(null);
  const [testLoading, setTestLoading] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(
    null,
  );

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    merchantWebhooksApi.listDeliveries(page, status || undefined).then(
      (result) => {
        setDeliveries(result.content);
        setTotalPages(result.page.totalPages);
        setLoading(false);
      },
      (requestError: unknown) => {
        setError(getErrorMessage(requestError));
        setLoading(false);
      },
    );
  }, [page, status]);

  useEffect(() => {
    merchantWebhooksApi.listDeliveries(page, status || undefined).then(
      (result) => {
        setDeliveries(result.content);
        setTotalPages(result.page.totalPages);
        setLoading(false);
        setError(null);
      },
      (requestError: unknown) => {
        setError(getErrorMessage(requestError));
        setLoading(false);
      },
    );
  }, [page, refreshKey, status]);

  function changeStatus(value: string) {
    setStatus(value as WebhookDeliveryStatus | '');
    setPage(0);
  }

  async function retry(delivery: WebhookDelivery) {
    setRetrying(delivery.deliveryId);
    setFeedback(null);
    try {
      await merchantWebhooksApi.retryDelivery(delivery.deliveryId);
      setFeedback({ tone: 'success', message: 'Webhook retry queued.' });
      load();
    } catch (requestError) {
      setFeedback({ tone: 'error', message: getErrorMessage(requestError) });
    } finally {
      setRetrying(null);
    }
  }

  async function sendTest() {
    setTestLoading(true);
    setFeedback(null);
    try {
      const result: WebhookTestResult = await merchantWebhooksApi.sendTest();
      setFeedback({
        tone: result.delivered ? 'success' : 'error',
        message: result.delivered
          ? 'Test event delivered successfully.'
          : (result.errorMessage ?? 'The test event could not be delivered.'),
      });
      load();
    } catch (requestError) {
      setFeedback({ tone: 'error', message: getErrorMessage(requestError) });
    } finally {
      setTestLoading(false);
    }
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold text-slate-900">Delivery log</h2>
          <p className="mt-1 text-sm text-slate-500">
            Review webhook attempts and retry failed deliveries.
          </p>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <SelectField
            label="Status"
            options={STATUS_OPTIONS}
            value={status}
            onChange={(event) => changeStatus(event.target.value)}
          />
          <Button type="button" onClick={sendTest} loading={testLoading}>
            {!testLoading && <Send className="size-4" aria-hidden="true" />}
            Send test event
          </Button>
        </div>
      </div>

      {feedback && <Alert tone={feedback.tone}>{feedback.message}</Alert>}
      {error && <ErrorState message={error} onAction={load} />}
      {!error && loading && <p className="text-sm text-slate-500">Loading delivery log...</p>}
      {!error && !loading && deliveries.length === 0 && (
        <EmptyState
          title="No webhook deliveries"
          message="Delivery attempts will appear here after a parcel status event or test event."
        />
      )}
      {!error && !loading && deliveries.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-4 py-3 font-medium">Tracking number</th>
                  <th className="px-4 py-3 font-medium">Event</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Attempts</th>
                  <th className="px-4 py-3 font-medium">HTTP status</th>
                  <th className="px-4 py-3 font-medium">Time</th>
                  <th className="px-4 py-3 font-medium">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {deliveries.map((delivery) => (
                  <DeliveryRow
                    key={delivery.deliveryId}
                    delivery={delivery}
                    formatTime={formatShortDateTime}
                    retrying={retrying === delivery.deliveryId}
                    onRetry={retry}
                  />
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div className="flex justify-end">
              <Pagination page={page} totalPages={totalPages} onChange={setPage} />
            </div>
          )}
        </>
      )}
    </section>
  );
}

function DeliveryRow({
  delivery,
  formatTime,
  retrying,
  onRetry,
}: {
  delivery: WebhookDelivery;
  formatTime: (iso: string) => string;
  retrying: boolean;
  onRetry: (delivery: WebhookDelivery) => void;
}) {
  return (
    <tr>
      <td className="px-4 py-3 font-medium whitespace-nowrap text-slate-900">
        {delivery.trackingNumber ?? delivery.parcelId}
      </td>
      <td className="px-4 py-3 whitespace-nowrap text-slate-600">{delivery.eventType}</td>
      <td className="px-4 py-3">
        <StatusPill visual={statusVisual(delivery.status)} />
      </td>
      <td className="px-4 py-3 text-slate-600">{delivery.retryCount + 1}</td>
      <td className="px-4 py-3 text-slate-600">{delivery.lastHttpStatus ?? '—'}</td>
      <td className="px-4 py-3 whitespace-nowrap text-slate-600">
        {formatTime(delivery.lastAttemptAt ?? delivery.createdAt)}
      </td>
      <td className="px-4 py-3">
        {delivery.status === 'FAILED' && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            loading={retrying}
            onClick={() => onRetry(delivery)}
          >
            {!retrying && <RotateCcw className="size-3.5" aria-hidden="true" />}
            Retry
          </Button>
        )}
      </td>
    </tr>
  );
}
