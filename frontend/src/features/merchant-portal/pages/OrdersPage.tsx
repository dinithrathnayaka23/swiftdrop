import { CircleX, PackageOpen } from 'lucide-react';
import { generatePath, Link, useSearchParams } from 'react-router';
import StateView from '../../../components/feedback/StateView';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { PATHS } from '../../../routes/paths';
import CopyButton from '../../shared-parcels/components/CopyButton';
import Pagination from '../../shared-parcels/components/Pagination';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import {
  formatDate,
  formatFee,
  formatTime,
  PARCEL_TYPE_LABELS,
} from '../../shared-parcels/parcelDisplay';
import { useApiData } from '../../shared-parcels/useApiData';
import { merchantPortalApi } from '../api';
import type { MerchantOrderSummary } from '../types';

const PAGE_SIZE = 10;

/** Zero-based page from "?page=" (one-based in the URL, as people read it). */
function pageFromParams(params: URLSearchParams): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 1 ? page - 1 : 0;
}

function OrdersTable({ rows }: { rows: MerchantOrderSummary[] }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200">
      <table className="w-full min-w-[820px] text-left text-sm">
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
              Type
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Fee
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
                <span className="inline-flex items-center gap-1">
                  <Link
                    to={generatePath(PATHS.merchantOrderDetail, {
                      trackingNumber: row.trackingNumber,
                    })}
                    className="text-brand-700 hover:text-brand-800 hover:underline"
                  >
                    {row.trackingNumber}
                  </Link>
                  <CopyButton
                    value={row.trackingNumber}
                    label={`Copy tracking number ${row.trackingNumber}`}
                  />
                </span>
              </td>
              <td className="px-4 py-3 text-slate-600">{row.externalOrderReference ?? '—'}</td>
              <td className="px-4 py-3 text-slate-700">
                {row.recipientName ?? '—'}
                {row.deliveryCity && (
                  <span className="block text-xs text-slate-500">{row.deliveryCity}</span>
                )}
              </td>
              <td className="px-4 py-3 text-slate-600">{PARCEL_TYPE_LABELS[row.parcelType]}</td>
              <td className="px-4 py-3 text-slate-700">
                {row.quotedFee != null ? formatFee(row.quotedFee, row.feeCurrency ?? 'LKR') : '—'}
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

/** Every order the merchant sent to SwiftDrop, newest first, ten per page. */
export default function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = pageFromParams(params);
  const orders = useApiData(`orders-${page}`, () => merchantPortalApi.listOrders(page, PAGE_SIZE));

  function goTo(next: number) {
    setParams(next > 0 ? { page: String(next + 1) } : {});
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Orders</h1>
        <p className="mt-1 text-slate-500">Every order you sent to SwiftDrop, newest first.</p>
      </header>

      <SectionCard
        title="All Orders"
        subtitle={
          orders.status === 'success'
            ? `${orders.data.page.totalElements} order${orders.data.page.totalElements === 1 ? '' : 's'}`
            : undefined
        }
      >
        {orders.status === 'loading' && (
          <div className="space-y-2" aria-busy="true" aria-label="Loading orders">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        )}
        {orders.status === 'error' && (
          <StateView
            icon={CircleX}
            tone="danger"
            title="Could not load your orders"
            message={orders.message}
            actions={
              <Button variant="outline" size="sm" onClick={orders.retry}>
                Try again
              </Button>
            }
          />
        )}
        {orders.status === 'success' && orders.data.content.length === 0 && (
          <StateView
            icon={PackageOpen}
            title={page === 0 ? 'No orders yet' : 'No orders on this page'}
            message={
              page === 0
                ? 'Orders you send through the SwiftDrop API will show up here.'
                : 'This page is past the end of your orders.'
            }
            actions={
              page === 0 ? (
                <ButtonLink to={PATHS.merchantIntegration} size="sm">
                  Set up the API
                </ButtonLink>
              ) : (
                <Button variant="outline" size="sm" onClick={() => goTo(0)}>
                  Go to the first page
                </Button>
              )
            }
          />
        )}
        {orders.status === 'success' && orders.data.content.length > 0 && (
          <div className="space-y-4">
            <OrdersTable rows={orders.data.content} />
            {orders.data.page.totalPages > 1 && (
              <div className="flex justify-end">
                <Pagination page={page} totalPages={orders.data.page.totalPages} onChange={goTo} />
              </div>
            )}
          </div>
        )}
      </SectionCard>
    </div>
  );
}
