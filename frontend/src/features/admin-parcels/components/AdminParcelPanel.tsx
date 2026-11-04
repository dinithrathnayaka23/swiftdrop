import {
  ChevronDown,
  FileText,
  Mail,
  Map as MapIcon,
  MapPin,
  Package,
  Pencil,
  Phone,
  Truck,
  User,
  X,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { parcelsApi } from '../../../api/parcels';
import { useApiData } from '../../shared-parcels/useApiData';
import { PATHS } from '../../../routes/paths';
import type { AdminParcelDetail } from '../../../types/parcel';
import { cn } from '../../../utils/cn';
import { formatDateTime } from '../../../utils/date';
import { PARCEL_TYPE_LABELS } from '../../shared-parcels/parcelDisplay';
import LoadingState from '../../../components/feedback/LoadingState';
import StateView from '../../../components/feedback/StateView';
import Button from '../../../components/ui/Button';
import CopyButton from '../../shared-parcels/components/CopyButton';
import StatusHistoryList from '../../shared-parcels/components/StatusHistoryList';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { adminStatusVisual, isUnassigned } from '../../shared-parcels/components/statusVisuals';

type Tab = 'details' | 'history' | 'items' | 'notes';
const TABS: { id: Tab; label: string }[] = [
  { id: 'details', label: 'Details' },
  { id: 'history', label: 'History' },
  { id: 'items', label: 'Items' },
  { id: 'notes', label: 'Notes' },
];

function Block({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof User;
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-slate-200 p-4">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
        <Icon className="size-4 text-slate-600" aria-hidden="true" />
        {title}
      </h3>
      {children}
    </section>
  );
}

function DetailsTab({ parcel }: { parcel: AdminParcelDetail }) {
  const address = parcel.dropoffAddress;
  const mapQuery = encodeURIComponent(`${address.addressLine1}, ${address.city}, Sri Lanka`);
  const rows: [string, ReactNode][] = [
    [
      'Tracking Number',
      <span key="tn" className="inline-flex items-center gap-1">
        #{parcel.trackingNumber}
        <CopyButton value={parcel.trackingNumber} label="Copy tracking number" />
      </span>,
    ],
    ['Merchant', parcel.merchantName ?? 'Customer booking'],
    ['Service Type', parcel.serviceType ?? '—'],
    ['Weight', `${parcel.weightKg} kg`],
    [
      'Items',
      `${parcel.itemCount} parcel${parcel.itemCount === 1 ? '' : 's'} • ${parcel.contentDescription ?? PARCEL_TYPE_LABELS[parcel.parcelType]}`,
    ],
    ['Payment', parcel.paymentMethod ?? '—'],
  ];

  return (
    <div className="space-y-4">
      <Block icon={Package} title="Parcel Information">
        <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-2.5 text-sm">
          {rows.map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-slate-500">{label}</dt>
              <dd className="font-medium text-slate-900">{value}</dd>
            </div>
          ))}
        </dl>
      </Block>

      <Block icon={User} title="Recipient Information">
        <ul className="space-y-2.5 text-sm text-slate-800">
          <li className="flex items-center gap-3">
            <User className="size-4 text-slate-500" aria-hidden="true" />
            {parcel.recipientName}
          </li>
          <li className="flex items-center gap-3">
            <Phone className="size-4 text-slate-500" aria-hidden="true" />
            <a
              href={`tel:${parcel.recipientPhone.replaceAll(' ', '')}`}
              className="hover:underline"
            >
              {parcel.recipientPhone}
            </a>
          </li>
          {parcel.recipientEmail && (
            <li className="flex items-center gap-3">
              <Mail className="size-4 text-slate-500" aria-hidden="true" />
              <a href={`mailto:${parcel.recipientEmail}`} className="text-blue-700 hover:underline">
                {parcel.recipientEmail}
              </a>
            </li>
          )}
        </ul>
      </Block>

      <Block icon={MapPin} title="Delivery Address">
        <div className="flex items-end justify-between gap-3">
          <address className="text-sm leading-6 text-slate-800 not-italic">
            {address.addressLine1}
            <br />
            {address.city}
            <br />
            {address.district}
          </address>
          <a
            href={`https://www.google.com/maps/search/?api=1&query=${mapQuery}`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex shrink-0 items-center gap-2 rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-700 hover:bg-brand-100"
          >
            <MapIcon className="size-4" aria-hidden="true" />
            View on Map
          </a>
        </div>
      </Block>

      <Block icon={FileText} title="Special Instructions">
        <p className="text-sm leading-6 text-slate-700">{parcel.deliveryInstructions || 'None.'}</p>
      </Block>
    </div>
  );
}

function PanelBody({ parcel, onClose }: { parcel: AdminParcelDetail; onClose: () => void }) {
  const [tab, setTab] = useState<Tab>('details');
  const navigate = useNavigate();
  const canAssign = isUnassigned(parcel.status, parcel.dispatchState);

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-start gap-4 border-b border-slate-100 p-5">
        <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-orange-50 text-orange-500">
          <Package className="size-7" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-lg font-bold break-all text-slate-900">#{parcel.trackingNumber}</h2>
            <StatusPill visual={adminStatusVisual(parcel.status, parcel.dispatchState)} />
          </div>
          <p className="mt-1 text-sm text-slate-500">Created {formatDateTime(parcel.createdAt)}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close parcel details"
          className="rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-800"
        >
          <X className="size-5" aria-hidden="true" />
        </button>
      </div>

      <div
        role="tablist"
        aria-label="Parcel sections"
        className="flex border-b border-slate-200 px-3"
      >
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            id={`parcel-tab-${item.id}`}
            aria-selected={tab === item.id}
            aria-controls={`parcel-tabpanel-${item.id}`}
            onClick={() => setTab(item.id)}
            className={cn(
              'flex-1 border-b-2 px-2 py-3 text-sm font-medium',
              tab === item.id
                ? 'border-brand-700 text-brand-700'
                : 'border-transparent text-slate-600 hover:text-slate-900',
            )}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div
        role="tabpanel"
        id={`parcel-tabpanel-${tab}`}
        aria-labelledby={`parcel-tab-${tab}`}
        className="min-h-0 flex-1 overflow-y-auto p-4"
      >
        {tab === 'details' && <DetailsTab parcel={parcel} />}
        {tab === 'history' && <StatusHistoryList history={parcel.history} />}
        {tab === 'items' && (
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 text-sm">
            <li className="flex items-center justify-between p-4">
              <span className="font-medium text-slate-900">
                {parcel.contentDescription ?? PARCEL_TYPE_LABELS[parcel.parcelType]}
              </span>
              <span className="text-slate-500">
                {parcel.itemCount} × {PARCEL_TYPE_LABELS[parcel.parcelType]} · {parcel.weightKg} kg
              </span>
            </li>
          </ul>
        )}
        {tab === 'notes' &&
          (parcel.notes.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">No notes for this parcel.</p>
          ) : (
            <ul className="space-y-3">
              {parcel.notes.map((note) => (
                <li key={note.id} className="rounded-xl border border-slate-200 p-4 text-sm">
                  <p className="text-slate-800">{note.text}</p>
                  <p className="mt-2 text-xs text-slate-500">
                    {note.author} · {formatDateTime(note.createdAt)}
                  </p>
                </li>
              ))}
            </ul>
          ))}
      </div>

      <div className="space-y-3 border-t border-slate-100 p-4">
        <Button
          size="lg"
          fullWidth
          className="bg-brand-800 hover:bg-brand-900"
          disabled={!canAssign}
          title={canAssign ? undefined : 'Only unassigned parcels can be assigned'}
          onClick={() => navigate(PATHS.adminUnassigned)}
        >
          <Truck className="size-5" aria-hidden="true" />
          Assign to Courier
        </Button>
        <div className="grid grid-cols-2 gap-3">
          <Button variant="outline" size="lg" disabled title="Editing parcels is not available yet">
            <Pencil className="size-4" aria-hidden="true" />
            Edit Parcel
          </Button>
          <Button variant="outline" size="lg" disabled title="More actions are not available yet">
            More Actions
            <ChevronDown className="size-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
    </div>
  );
}

/** Right-hand panel of the admin Parcels page. */
export default function AdminParcelPanel({
  parcelId,
  onClose,
}: {
  parcelId: string;
  onClose: () => void;
}) {
  const parcel = useApiData(parcelId, () => parcelsApi.getAdminParcel(parcelId));

  if (parcel.status === 'loading') return <LoadingState title="Loading parcel..." />;
  if (parcel.status === 'error' || !parcel.data) {
    return (
      <StateView
        icon={Package}
        title={parcel.status === 'error' ? 'Could not load the parcel' : 'Parcel not found'}
        message={parcel.status === 'error' ? parcel.message : undefined}
        actions={
          <Button variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
        }
      />
    );
  }
  return <PanelBody parcel={parcel.data} onClose={onClose} />;
}
