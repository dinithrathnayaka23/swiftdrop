const stats = [
  { label: 'Pending review', value: '14', change: '+3 today' },
  { label: 'Approved', value: '436', change: '+18 this month' },
  { label: 'Rejected', value: '21', change: '2 escalated' },
  { label: 'Live merchants', value: '78', change: '91% active' },
];

const merchants = [
  { business: 'CityMart Stores', owner: 'Nimal Silva', city: 'Colombo', applied: '2026-11-09', status: 'Pending' },
  { business: 'Green Basket', owner: 'Ishara Dias', city: 'Kandy', applied: '2026-11-08', status: 'Approved' },
  { business: 'Lanka Fresh', owner: 'Madhavi Jayasinghe', city: 'Galle', applied: '2026-11-07', status: 'Under review' },
  { business: 'QuickCart', owner: 'Sajith Raman', city: 'Negombo', applied: '2026-11-05', status: 'Rejected' },
];

const statusClasses: Record<string, string> = {
  Pending: 'bg-amber-50 text-amber-700 ring-amber-200',
  Approved: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  'Under review': 'bg-sky-50 text-sky-700 ring-sky-200',
  Rejected: 'bg-rose-50 text-rose-700 ring-rose-200',
};

export default function MerchantManagementPage() {
  return (
    <section>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Merchant Management
          </h1>
          <p className="mt-1 text-slate-500">
            Review applications, confirm compliance and keep approved merchants ready for live orders.
          </p>
        </div>
        <button className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50">
          Export list
        </button>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.label} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-sm text-slate-500">{stat.label}</p>
            <div className="mt-3 flex items-end justify-between gap-3">
              <p className="text-3xl font-bold tracking-tight text-slate-900">{stat.value}</p>
              <span className="rounded-full bg-violet-50 px-2 py-1 text-xs font-medium text-violet-700">
                {stat.change}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-8 rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-200 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Merchant approvals</h2>
            <p className="text-sm text-slate-500">Latest business applications and their current status.</p>
          </div>
          <button className="rounded-xl bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800">
            Review queue
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="px-5 py-3 font-medium">Business</th>
                <th className="px-5 py-3 font-medium">Owner</th>
                <th className="px-5 py-3 font-medium">City</th>
                <th className="px-5 py-3 font-medium">Applied</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 font-medium text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {merchants.map((merchant) => (
                <tr key={merchant.business} className="hover:bg-slate-50">
                  <td className="px-5 py-4 font-medium text-slate-900">{merchant.business}</td>
                  <td className="px-5 py-4">{merchant.owner}</td>
                  <td className="px-5 py-4">{merchant.city}</td>
                  <td className="px-5 py-4">{merchant.applied}</td>
                  <td className="px-5 py-4">
                    <span
                      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ring-1 ${statusClasses[merchant.status]}`}
                    >
                      {merchant.status}
                    </span>
                  </td>
                  <td className="px-5 py-4 text-right">
                    <button className="rounded-lg border border-slate-200 px-3 py-1.5 font-medium text-slate-700 hover:border-slate-300 hover:bg-slate-50">
                      Review
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
