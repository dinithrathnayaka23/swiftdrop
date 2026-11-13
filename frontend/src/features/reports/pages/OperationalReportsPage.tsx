const stats = [
  { label: 'Completed deliveries', value: '3,480', change: '+9.4%' },
  { label: 'On-time rate', value: '94.8%', change: '+1.2%' },
  { label: 'Issue rate', value: '3.1%', change: '-0.6%' },
  { label: 'Gross revenue', value: 'LKR 2.4M', change: '+14.6%' },
];

const dailyPerformance = [
  { date: 'Mon 04', orders: 426, completed: 398, onTime: '96%', avgEta: '16 min' },
  { date: 'Tue 05', orders: 462, completed: 432, onTime: '95%', avgEta: '18 min' },
  { date: 'Wed 06', orders: 478, completed: 451, onTime: '97%', avgEta: '15 min' },
  { date: 'Thu 07', orders: 496, completed: 468, onTime: '94%', avgEta: '20 min' },
  { date: 'Fri 08', orders: 514, completed: 489, onTime: '96%', avgEta: '17 min' },
];

const alerts = [
  'Two courier zones in Colombo South are trending above average request volume.',
  'Merchant rate compliance improved for last 7 days across the Kandy metro cluster.',
  'Pickup delays are still elevated for same-day requests after 5:00 PM.',
];

export default function OperationalReportsPage() {
  return (
    <section>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Operational Reports
          </h1>
          <p className="mt-1 text-slate-500">
            Track delivery performance, operational health and merchant activity across the network.
          </p>
        </div>
        <button className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50">
          Download report
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
              <span className="rounded-full bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700">
                {stat.change}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-8 grid gap-6 xl:grid-cols-[1.5fr_0.9fr]">
        <div className="rounded-3xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-200 p-5">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Performance by day</h2>
              <p className="text-sm text-slate-500">
                Completed orders and delivery quality over the last 5 days.
              </p>
            </div>
            <div className="flex gap-2">
              {['Overview', 'Dispatch', 'Merchants', 'Customers'].map((tab, index) => (
                <button
                  key={tab}
                  className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                    index === 0
                      ? 'bg-slate-900 text-white'
                      : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-5 py-3 font-medium">Date</th>
                  <th className="px-5 py-3 font-medium">Orders</th>
                  <th className="px-5 py-3 font-medium">Completed</th>
                  <th className="px-5 py-3 font-medium">On-time</th>
                  <th className="px-5 py-3 font-medium">Avg ETA</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {dailyPerformance.map((entry) => (
                  <tr key={entry.date} className="hover:bg-slate-50">
                    <td className="px-5 py-4 font-medium text-slate-900">{entry.date}</td>
                    <td className="px-5 py-4">{entry.orders}</td>
                    <td className="px-5 py-4">{entry.completed}</td>
                    <td className="px-5 py-4">{entry.onTime}</td>
                    <td className="px-5 py-4">{entry.avgEta}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Operational alerts</h2>
          <p className="mt-1 text-sm text-slate-500">Issues requiring attention this week.</p>

          <ul className="mt-5 space-y-3">
            {alerts.map((alert) => (
              <li
                key={alert}
                className="rounded-2xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800"
              >
                {alert}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
