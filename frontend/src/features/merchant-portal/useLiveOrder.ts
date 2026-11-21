import { useRef, useState } from 'react';
import type { LiveConnectionState, LiveStatusUpdate } from '../shared-live/types';
import { useLiveUpdates } from '../shared-live/useLiveUpdates';
import { merchantPortalApi } from './api';
import type { MerchantOrderDetail } from './types';

/**
 * The order with a live status change applied. The status version counts the status changes, so
 * it equals the length of the timeline; an update that isn't newer than that is ignored.
 */
export function withUpdate(
  order: MerchantOrderDetail,
  update: LiveStatusUpdate,
): MerchantOrderDetail {
  if (update.trackingNumber !== order.trackingNumber) return order;
  if (update.statusVersion <= order.timeline.length) return order;
  return {
    ...order,
    status: update.status,
    updatedAt: update.changedAt,
    timeline: [
      ...order.timeline,
      {
        status: update.status,
        changedAt: update.changedAt,
        note: null,
        failureReason: null,
        recipientName: null,
      },
    ],
  };
}

/**
 * Keeps an order up to date from its public live status stream (Member 5's useLiveUpdates). A
 * pushed change shows at once; the order is then reloaded quietly so details the stream doesn't
 * carry (failure reason, recipient) come from the API, which stays the true record.
 */
export function useLiveOrder(initial: MerchantOrderDetail): {
  order: MerchantOrderDetail;
  connection: LiveConnectionState;
} {
  const [order, setOrder] = useState(initial);
  const latestRequest = useRef(0);

  function reload() {
    const request = ++latestRequest.current;
    merchantPortalApi.getOrder(initial.trackingNumber).then(
      (fresh) => {
        // Only the newest reload counts, and never go back to an older state.
        if (!fresh || request !== latestRequest.current) return;
        setOrder((current) => (fresh.timeline.length >= current.timeline.length ? fresh : current));
      },
      () => {
        // The pushed status is already shown; the next update or reconnect tries again.
      },
    );
  }

  const connection = useLiveUpdates({
    path: `/public/tracking/${encodeURIComponent(initial.trackingNumber)}/stream`,
    knownVersions: { [order.trackingNumber]: order.timeline.length },
    onUpdate: (update) => {
      setOrder((current) => withUpdate(current, update));
      reload();
    },
    // Changes may have been missed while disconnected.
    onReconnect: reload,
  });

  return { order, connection };
}
