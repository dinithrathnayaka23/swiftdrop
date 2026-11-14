import { UserCog } from 'lucide-react';
import { useEffect, useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import { ROLES, type Role } from '../../../types/auth';
import { useApiData } from '../../shared-parcels/useApiData';
import { adminUsersApi } from '../api';
import { ACCOUNT_STATUSES, type AccountStatus, type UserSummary } from '../types';

const PAGE_SIZE = 10;
const SEARCH_DELAY_MS = 300;

const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Customer',
  MERCHANT: 'Merchant',
  COURIER: 'Courier',
  DISPATCHER_ADMIN: 'Admin',
};

const STATUS_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Active',
  PENDING_APPROVAL: 'Pending approval',
  SUSPENDED: 'Suspended',
  DEACTIVATED: 'Deactivated',
};

const STATUS_CLASSES: Record<AccountStatus, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  PENDING_APPROVAL: 'bg-amber-50 text-amber-700 ring-amber-200',
  SUSPENDED: 'bg-rose-50 text-rose-700 ring-rose-200',
  DEACTIVATED: 'bg-slate-100 text-slate-600 ring-slate-200',
};

const selectClasses =
  'rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 outline-none transition focus:border-sky-400 focus:bg-white';

/** A change waiting for the admin to confirm it. */
type PendingChange =
  | { kind: 'role'; user: UserSummary; role: Role }
  | { kind: 'status'; user: UserSummary; status: AccountStatus };

/** The other role a user may be switched to: only customer ↔ courier is allowed. */
function otherRole(user: UserSummary): Role | null {
  if (user.role === 'CUSTOMER') return 'COURIER';
  if (user.role === 'COURIER') return 'CUSTOMER';
  return null;
}

/** Status changes offered for this user; none for the admin or a merchant awaiting approval. */
function statusActions(user: UserSummary): AccountStatus[] {
  if (user.role === 'DISPATCHER_ADMIN' || user.accountStatus === 'PENDING_APPROVAL') return [];
  if (user.accountStatus === 'ACTIVE') return ['SUSPENDED', 'DEACTIVATED'];
  return ['ACTIVE'];
}

const STATUS_ACTION_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Reactivate',
  PENDING_APPROVAL: '',
  SUSPENDED: 'Suspend',
  DEACTIVATED: 'Deactivate',
};

function describe(change: PendingChange): { title: string; message: string; confirmLabel: string } {
  const name = change.user.fullName;
  if (change.kind === 'role') {
    return {
      title: `Make ${name} a ${ROLE_LABELS[change.role].toLowerCase()}?`,
      message: `${name} will be signed out and must sign in again on the ${
        change.role === 'COURIER' ? 'courier app' : 'main sign-in page'
      }.`,
      confirmLabel: `Make ${ROLE_LABELS[change.role].toLowerCase()}`,
    };
  }
  const action = STATUS_ACTION_LABELS[change.status];
  return {
    title: `${action} ${name}?`,
    message:
      change.status === 'ACTIVE'
        ? `${name} will be able to sign in again.`
        : `${name} will be signed out and can't sign in until reactivated.`,
    confirmLabel: action,
  };
}

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

export default function UserManagementPage() {
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [page, setPage] = useState(0);

  // Bumped after a change so the list and the cards load again.
  const [reloads, setReloads] = useState(0);

  const [pending, setPending] = useState<PendingChange | null>(null);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);

  // Wait until the admin stops typing before searching; a new search starts on page 1.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const query = { search, role, status, page, size: PAGE_SIZE };
  const list = useApiData(`${JSON.stringify(query)}#${reloads}`, () => adminUsersApi.list(query));
  // If the stats fail, the cards show "–"; the list still works.
  const statsData = useApiData(`stats#${reloads}`, () => adminUsersApi.stats());
  const users = list.status === 'success' ? list.data : null;
  const stats = statsData.status === 'success' ? statsData.data : null;

  async function confirmChange() {
    if (!pending) return;
    setSaving(true);
    try {
      if (pending.kind === 'role') {
        await adminUsersApi.changeRole(pending.user.id, pending.role);
        setNotice({
          tone: 'success',
          text: `${pending.user.fullName} is now a ${ROLE_LABELS[pending.role].toLowerCase()}.`,
        });
      } else {
        await adminUsersApi.changeStatus(pending.user.id, pending.status);
        setNotice({
          tone: 'success',
          text: `${pending.user.fullName} is now ${STATUS_LABELS[pending.status].toLowerCase()}.`,
        });
      }
      setReloads((n) => n + 1);
    } catch (err) {
      setNotice({ tone: 'error', text: getErrorMessage(err) });
    } finally {
      setSaving(false);
      setPending(null);
    }
  }

  const cards = [
    { label: 'Total users', value: stats?.total },
    { label: 'Customers', value: stats?.byRole.CUSTOMER },
    { label: 'Couriers', value: stats?.byRole.COURIER },
    { label: 'Pending approval', value: stats?.byStatus.PENDING_APPROVAL },
  ];

  const pageInfo = users?.page;
  const dialog = pending ? describe(pending) : null;

  return (
    <section>
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          User Management
        </h1>
        <p className="mt-1 text-slate-500">
          Find accounts, switch customers and couriers, and suspend or reactivate access.
        </p>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <div
            key={card.label}
            className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
          >
            <p className="text-sm text-slate-500">{card.label}</p>
            <p className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
              {card.value === undefined ? '–' : card.value.toLocaleString()}
            </p>
          </div>
        ))}
      </div>

      {notice && (
        <Alert tone={notice.tone} className="mt-6">
          {notice.text}
        </Alert>
      )}

      <div className="mt-8 rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-200 p-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Accounts</h2>
            <p className="text-sm text-slate-500">
              {pageInfo
                ? `${pageInfo.totalElements.toLocaleString()} matching, newest first`
                : 'Newest first'}
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <input
              type="search"
              aria-label="Search users"
              placeholder="Search name, email or phone"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className={`${selectClasses} w-full sm:w-64`}
            />
            <select
              aria-label="Filter by role"
              value={role}
              onChange={(e) => {
                setRole(e.target.value as Role | '');
                setPage(0);
              }}
              className={selectClasses}
            >
              <option value="">All roles</option>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABELS[r]}
                </option>
              ))}
            </select>
            <select
              aria-label="Filter by status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as AccountStatus | '');
                setPage(0);
              }}
              className={selectClasses}
            >
              <option value="">All statuses</option>
              {ACCOUNT_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </div>
        </div>

        {list.status === 'error' ? (
          <ErrorState message={list.message} onAction={list.retry} actionLabel="Try again" />
        ) : !users ? (
          <LoadingState title="Loading users..." />
        ) : users.content.length === 0 ? (
          <EmptyState title="No users found" message="Try a different search or filter." />
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-5 py-3 font-medium">User</th>
                  <th className="px-5 py-3 font-medium">Phone</th>
                  <th className="px-5 py-3 font-medium">Role</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                  <th className="px-5 py-3 font-medium">Joined</th>
                  <th className="px-5 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {users.content.map((user) => {
                  const newRole = otherRole(user);
                  return (
                    <tr key={user.id} className="hover:bg-slate-50">
                      <td className="px-5 py-4">
                        <div className="font-medium text-slate-900">{user.fullName}</div>
                        <div className="text-xs text-slate-500">
                          {user.email}
                          {!user.emailVerified && (
                            <span className="ml-1 text-amber-600">(unverified)</span>
                          )}
                        </div>
                      </td>
                      <td className="px-5 py-4">{user.phone ?? '–'}</td>
                      <td className="px-5 py-4">{ROLE_LABELS[user.role]}</td>
                      <td className="px-5 py-4">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ring-1 ${STATUS_CLASSES[user.accountStatus]}`}
                        >
                          {STATUS_LABELS[user.accountStatus]}
                        </span>
                      </td>
                      <td className="px-5 py-4 whitespace-nowrap">{formatDate(user.createdAt)}</td>
                      <td className="px-5 py-4">
                        <div className="flex flex-wrap justify-end gap-2">
                          {newRole && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setPending({ kind: 'role', user, role: newRole })}
                            >
                              Make {ROLE_LABELS[newRole].toLowerCase()}
                            </Button>
                          )}
                          {statusActions(user).map((next) => (
                            <Button
                              key={next}
                              size="sm"
                              variant={next === 'ACTIVE' ? 'outline' : 'danger'}
                              onClick={() => setPending({ kind: 'status', user, status: next })}
                            >
                              {STATUS_ACTION_LABELS[next]}
                            </Button>
                          ))}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {pageInfo && pageInfo.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-5 py-3 text-sm text-slate-600">
            <span>
              Page {pageInfo.number + 1} of {pageInfo.totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={page + 1 >= pageInfo.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={pending !== null}
        title={dialog?.title ?? ''}
        message={dialog?.message ?? ''}
        confirmLabel={dialog?.confirmLabel}
        icon={UserCog}
        loading={saving}
        onConfirm={() => void confirmChange()}
        onCancel={() => setPending(null)}
      />
    </section>
  );
}
