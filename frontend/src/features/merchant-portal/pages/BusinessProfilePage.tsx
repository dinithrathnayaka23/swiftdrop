import {
  BadgeCheck,
  Building2,
  CalendarDays,
  FileText,
  Mail,
  MapPin,
  Pencil,
  Phone,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import LoadingState from '../../../components/feedback/LoadingState';
import { ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { formatPhone } from '../../profile/phoneParts';
import { useApiData } from '../../shared-parcels/useApiData';
import { merchantPortalApi } from '../api';
import EditBusinessProfileForm from '../components/EditBusinessProfileForm';
import type { MerchantApprovalStatus, MerchantProfile } from '../types';

const STATUS_LABELS: Record<MerchantApprovalStatus, string> = {
  PENDING: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

function Detail({
  icon: Icon,
  label,
  children,
}: {
  icon: typeof Mail;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex gap-3">
      <Icon className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
      <div className="min-w-0">
        <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</dt>
        <dd className="mt-0.5 text-sm break-words whitespace-pre-line text-slate-900">
          {children}
        </dd>
      </div>
    </div>
  );
}

/** Merchant: the verified business details and the contact details they may change. */
export default function BusinessProfilePage() {
  const { formatDate } = useDateFormat();
  const loaded = useApiData('merchant-profile', () => merchantPortalApi.getProfile());
  // The profile the last save returned; it replaces the loaded one without reloading.
  const [saved, setSaved] = useState<MerchantProfile | null>(null);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  if (loaded.status === 'loading') return <LoadingState title="Loading your business profile..." />;
  if (loaded.status === 'error') {
    return <ErrorState message={loaded.message} onAction={loaded.retry} actionLabel="Try again" />;
  }
  const profile = saved ?? loaded.data;

  function onSaved(next: MerchantProfile) {
    setSaved(next);
    setEditing(false);
    setNotice('Your contact details were saved.');
  }

  return (
    <section className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
        Business Profile
      </h1>
      <p className="mt-1 text-slate-500">Your business details on SwiftDrop.</p>

      {notice && (
        <Alert tone="success" className="mt-6">
          {notice}
        </Alert>
      )}

      <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Business</h2>
        <p className="text-sm text-slate-500">
          Verified when your business was approved. Contact SwiftDrop support to change these.
        </p>
        <dl className="mt-5 grid gap-5 sm:grid-cols-2">
          <Detail icon={Building2} label="Business name">
            {profile.businessName}
          </Detail>
          <Detail icon={FileText} label="Registration number">
            {profile.registrationNo}
          </Detail>
          <Detail icon={BadgeCheck} label="Status">
            {STATUS_LABELS[profile.approvalStatus]}
          </Detail>
          {profile.approvedAt && (
            <Detail icon={CalendarDays} label="Approved on">
              {formatDate(profile.approvedAt)}
            </Detail>
          )}
        </dl>
      </div>

      <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Contact details</h2>
            <p className="text-sm text-slate-500">
              Where SwiftDrop reaches you about your orders and account.
            </p>
          </div>
          {!editing && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setNotice(null);
                setEditing(true);
              }}
            >
              <Pencil className="size-4" aria-hidden="true" />
              Edit
            </Button>
          )}
        </div>

        <div className="mt-5">
          {editing ? (
            <EditBusinessProfileForm
              profile={profile}
              onSaved={onSaved}
              onCancel={() => setEditing(false)}
            />
          ) : (
            <dl className="grid gap-5 sm:grid-cols-2">
              <Detail icon={Mail} label="Contact email">
                {profile.contactEmail}
              </Detail>
              <Detail icon={Phone} label="Contact phone">
                {formatPhone(profile.contactPhone)}
              </Detail>
              <div className="sm:col-span-2">
                <Detail icon={MapPin} label="Business address">
                  {profile.businessAddress}
                </Detail>
              </div>
            </dl>
          )}
        </div>
      </div>
    </section>
  );
}
