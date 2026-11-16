import { ArrowRight, Radio } from 'lucide-react';
import type { ReactNode } from 'react';
import { generatePath, Link } from 'react-router';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { PATHS } from '../../../routes/paths';
import type { TrackingResult } from '../../../types/tracking';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import type { AssistantCard, FeeCard as Fee, ParcelSummary } from '../types';

const money = (amount: number, currency: string) =>
  `${currency} ${amount.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const trackPath = (trackingNumber: string) => generatePath(PATHS.trackResult, { trackingNumber });

function Card({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3 text-sm shadow-xs">
      {children}
    </div>
  );
}

function Route({ from, to }: { from: string; to: string }) {
  return (
    <p className="flex items-center gap-1.5 text-slate-600">
      {from}
      <ArrowRight className="size-3.5 text-slate-400" aria-label="to" />
      {to}
    </p>
  );
}

function TrackingCard({ tracking }: { tracking: TrackingResult }) {
  const { formatShortDateTime } = useDateFormat();
  return (
    <Card>
      <div className="flex items-start justify-between gap-2">
        <p className="font-semibold text-slate-900">{tracking.trackingNumber}</p>
        <StatusPill visual={statusVisual(tracking.currentStatus)} />
      </div>
      <div className="mt-2 space-y-1 text-xs">
        <Route from={tracking.fromCity} to={tracking.toCity} />
        <p className="text-slate-500">Updated {formatShortDateTime(tracking.lastUpdatedAt)}</p>
      </div>
      <Link
        to={trackPath(tracking.trackingNumber)}
        className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-brand-700 hover:underline"
      >
        <Radio className="size-3.5" aria-hidden="true" />
        Get live updates
      </Link>
    </Card>
  );
}

function FeeCard({ fee }: { fee: Fee }) {
  return (
    <Card>
      <p className="text-xs text-slate-500">Estimated delivery fee</p>
      <p className="text-lg font-bold text-slate-900">{money(fee.totalFee, fee.currency)}</p>
      {fee.lines.length > 0 && (
        <ul className="mt-2 space-y-1 border-t border-slate-100 pt-2 text-xs text-slate-600">
          {fee.lines.map((line) => (
            <li key={line.label} className="flex justify-between gap-3">
              <span>{line.label}</span>
              <span className="tabular-nums">{money(line.amount, fee.currency)}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function ParcelsCard({ parcels }: { parcels: ParcelSummary[] }) {
  const { formatShortDateTime } = useDateFormat();
  return (
    <Card>
      <p className="mb-2 text-xs font-semibold text-slate-500">Your recent parcels</p>
      <ul className="divide-y divide-slate-100">
        {parcels.map((parcel) => (
          <li key={parcel.trackingNumber} className="py-2 first:pt-0 last:pb-0">
            <div className="flex items-start justify-between gap-2">
              <Link
                to={trackPath(parcel.trackingNumber)}
                className="font-semibold text-slate-900 hover:text-brand-700 hover:underline"
              >
                {parcel.trackingNumber}
              </Link>
              <StatusPill visual={statusVisual(parcel.status)} />
            </div>
            <div className="mt-1 flex items-center justify-between gap-2 text-xs">
              <Route from={parcel.fromCity} to={parcel.toCity} />
              <span className="text-slate-500">{formatShortDateTime(parcel.lastUpdatedAt)}</span>
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** The facts under a reply: built by the server from tool results, never from the model's text. */
export default function AssistantCards({ cards }: { cards: AssistantCard[] }) {
  if (cards.length === 0) return null;
  return (
    <div className="space-y-2">
      {cards.map((card, index) => {
        switch (card.type) {
          case 'TRACKING':
            return <TrackingCard key={index} tracking={card.tracking} />;
          case 'FEE':
            return <FeeCard key={index} fee={card.fee} />;
          case 'PARCELS':
            return <ParcelsCard key={index} parcels={card.parcels} />;
        }
      })}
    </div>
  );
}
