const stats = [
  { label: 'Total users', value: '2,846', change: '+12.4%' },
  { label: 'Active admins', value: '18', change: '+2 this week' },
  { label: 'Pending approvals', value: '9', change: '3 require review' },
  { label: 'Suspended', value: '6', change: '2 under review' },
];

const users = [
  {
    name: 'Nimali Perera',
    role: 'Dispatcher',
    team: 'Operations',
    lastLogin: '2h ago',
    status: 'Active',
  },
  {
    name: 'Rashmi Fernando',
    role: 'Customer Support',
    team: 'Support',
    lastLogin: '4h ago',
    status: 'Active',
  },
  { name: 'Shan Alwis', role: 'Finance', team: 'Admin', lastLogin: '1 day ago', status: 'Pending' },
  {
    name: 'Kasun Jayawardena',
    role: 'Courier manager',
    team: 'Dispatch',
    lastLogin: '3 days ago',
    status: 'Suspended',
  },
  {
    name: 'Anika Nethmini',
    role: 'Merchant admin',
    team: 'Commercial',
    lastLogin: 'Today',
    status: 'Active',
  },
];

const statusClasses: Record<string, string> = {
  Active: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  Pending: 'bg-amber-50 text-amber-700 ring-amber-200',
  Suspended: 'bg-rose-50 text-rose-700 ring-rose-200',
};

export default function UserManagementPage() {
  return (
    <section>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            User Management
          </h1>
          <p className="mt-1 text-slate-500">
            Manage staff roles, access and account status across the SwiftDrop admin workspace.
          </p>
        </div>
        <button className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50">
          + Invite user
        </button>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => (
          <div
            key={stat.label}
            className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
          >
            <p className="text-sm text-slate-500">{stat.label}</p>
            <div className="mt-3 flex items-end justify-between gap-3">
              <p className="text-3xl font-bold tracking-tight text-slate-900">{stat.value}</p>
              <span className="rounded-full bg-sky-50 px-2 py-1 text-xs font-medium text-sky-700">
                {stat.change}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-8 rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-200 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Team access</h2>
            <p className="text-sm text-slate-500">
              Review recent account activity and request changes.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <input
              aria-label="Search users"
              placeholder="Search users"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 transition outline-none focus:border-sky-400 focus:bg-white sm:w-56"
            />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="px-5 py-3 font-medium">User</th>
                <th className="px-5 py-3 font-medium">Role</th>
                <th className="px-5 py-3 font-medium">Team</th>
                <th className="px-5 py-3 font-medium">Last login</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 text-right font-medium">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {users.map((user) => (
                <tr key={user.name} className="hover:bg-slate-50">
                  <td className="px-5 py-4">
                    <div className="font-medium text-slate-900">{user.name}</div>
                  </td>
                  <td className="px-5 py-4">{user.role}</td>
                  <td className="px-5 py-4">{user.team}</td>
                  <td className="px-5 py-4">{user.lastLogin}</td>
                  <td className="px-5 py-4">
                    <span
                      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ring-1 ${statusClasses[user.status]}`}
                    >
                      {user.status}
                    </span>
                  </td>
                  <td className="px-5 py-4 text-right">
                    <button className="rounded-lg border border-slate-200 px-3 py-1.5 font-medium text-slate-700 hover:border-slate-300 hover:bg-slate-50">
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
