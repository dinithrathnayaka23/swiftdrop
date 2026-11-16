import { CalendarDays, KeyRound, Mail, Pencil, Phone, ShieldCheck, UserRound } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { refreshSession } from '../../../api/client';
import { getErrorMessage } from '../../../api/errors';
import LoadingState from '../../../components/feedback/LoadingState';
import { ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { useDateFormat } from '../../../hooks/useDateFormat';
import { useApiData } from '../../shared-parcels/useApiData';
import { profileApi } from '../api';
import EditProfileForm from '../components/EditProfileForm';
import { formatPhone } from '../phoneParts';
import type { UserProfile } from '../types';

const STATUS_LABELS: Record<UserProfile['accountStatus'], string> = {
  ACTIVE: 'Active',
  PENDING_APPROVAL: 'Pending approval',
  SUSPENDED: 'Suspended',
  DEACTIVATED: 'Deactivated',
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
      <div>
        <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</dt>
        <dd className="mt-0.5 text-sm text-slate-900">{children}</dd>
      </div>
    </div>
  );
}

/** Customer: view and edit your own details, and reset your password. */
export default function ProfilePage() {
  const { formatDate } = useDateFormat();
  const loaded = useApiData('me', () => profileApi.get());
  // The profile the last save returned; it replaces the loaded one without reloading.
  const [saved, setSaved] = useState<UserProfile | null>(null);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const [sendingReset, setSendingReset] = useState(false);
  const [resetResult, setResetResult] = useState<{
    tone: 'success' | 'error';
    text: string;
  } | null>(null);

  if (loaded.status === 'loading') return <LoadingState title="Loading your profile..." />;
  if (loaded.status === 'error') {
    return <ErrorState message={loaded.message} onAction={loaded.retry} actionLabel="Try again" />;
  }
  const profile = saved ?? loaded.data;

  function onSaved(next: UserProfile) {
    setSaved(next);
    setEditing(false);
    setNotice('Your details were saved.');
    // Renews the session so the name in the top bar shows the new one.
    void refreshSession();
  }

  async function sendResetLink() {
    setSendingReset(true);
    setResetResult(null);
    try {
      await profileApi.sendPasswordResetLink(profile.email);
      setResetResult({
        tone: 'success',
        text: `We've sent a link to ${profile.email}. Open it to choose a new password.`,
      });
    } catch (err) {
      setResetResult({ tone: 'error', text: getErrorMessage(err) });
    } finally {
      setSendingReset(false);
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
        Profile &amp; Account
      </h1>
      <p className="mt-1 text-slate-500">Your personal details and account settings.</p>

      {notice && (
        <Alert tone="success" className="mt-6">
          {notice}
        </Alert>
      )}

      <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Personal details</h2>
            <p className="text-sm text-slate-500">Used on your deliveries and receipts.</p>
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
            <EditProfileForm
              profile={profile}
              onSaved={onSaved}
              onCancel={() => setEditing(false)}
            />
          ) : (
            <dl className="grid gap-5 sm:grid-cols-2">
              <Detail icon={UserRound} label="Full name">
                {profile.fullName}
              </Detail>
              <Detail icon={Phone} label="Phone">
                {formatPhone(profile.phone)}
              </Detail>
            </dl>
          )}
        </div>
      </div>

      <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Account</h2>
        <dl className="mt-5 grid gap-5 sm:grid-cols-2">
          <Detail icon={Mail} label="Email">
            {profile.email}
            <span className="mt-0.5 block text-xs text-slate-500">
              Your sign-in email can't be changed here.
            </span>
          </Detail>
          <Detail icon={ShieldCheck} label="Account status">
            {STATUS_LABELS[profile.accountStatus]}
          </Detail>
          <Detail icon={CalendarDays} label="Member since">
            {formatDate(profile.createdAt)}
          </Detail>
        </dl>

        <div className="mt-6 border-t border-slate-200 pt-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex gap-3">
              <KeyRound className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
              <div>
                <p className="text-sm font-medium text-slate-900">Password</p>
                <p className="text-sm text-slate-500">
                  We'll email you a secure link to set a new password.
                </p>
              </div>
            </div>
            <Button variant="outline" onClick={() => void sendResetLink()} loading={sendingReset}>
              Change Password
            </Button>
          </div>
          {resetResult && (
            <Alert tone={resetResult.tone} className="mt-4">
              {resetResult.text}
            </Alert>
          )}
        </div>
      </div>
    </section>
  );
}
