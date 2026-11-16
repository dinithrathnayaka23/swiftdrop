import { useState, type FormEvent } from 'react';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Button from '../../../components/ui/Button';
import { useApiData } from '../../shared-parcels/useApiData';
import { reportsApi } from '../api';
import DeliveriesChart from '../components/DeliveriesChart';
import { lastDays, parseIsoDate, rangeError, thisMonth, toIsoDate } from '../dateRange';
import type { DailyDeliveryStat, DateRange } from '../types';

type Preset = 'last7' | 'last30' | 'month' | 'custom';

const PRESETS: { id: Exclude<Preset, 'custom'>; label: string; range: () => DateRange }[] = [
  { id: 'last7', label: 'Last 7 days', range: () => lastDays(7) },
  { id: 'last30', label: 'Last 30 days', range: () => lastDays(30) },
  { id: 'month', label: 'This month', range: () => thisMonth() },
];

const inputClasses =
  'rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none transition focus:border-sky-400';

const formatDay = (iso: string) =>
  parseIsoDate(iso).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
const formatRangeDay = (iso: string) =>
  parseIsoDate(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
const percent = (value: number) => `${value.toFixed(2)}%`;

/** Success rate of one day's finished deliveries, or "–" when none finished. */
function daySuccessRate(day: DailyDeliveryStat): string {
  const finished = day.delivered + day.failed;
  return finished === 0 ? '–' : percent((day.delivered / finished) * 100);
}

export default function OperationalReportsPage() {
  const [preset, setPreset] = useState<Preset>('last7');
  const [range, setRange] = useState<DateRange>(() => lastDays(7));
  const [draft, setDraft] = useState<DateRange>(range);
  const [draftError, setDraftError] = useState<string | null>(null);

  const key = `${range.from}..${range.to}`;
  const summary = useApiData(`summary#${key}`, () => reportsApi.summary(range));
  const daily = useApiData(`daily#${key}`, () => reportsApi.deliveriesPerDay(range));

  function choosePreset(id: Exclude<Preset, 'custom'>, next: DateRange) {
    setPreset(id);
    setRange(next);
    setDraft(next);
    setDraftError(null);
  }

  function applyCustom(e: FormEvent) {
    e.preventDefault();
    const problem = rangeError(draft);
    setDraftError(problem);
    if (problem) return;
    setPreset('custom');
    setRange(draft);
  }

  const data = summary.status === 'success' ? summary.data : null;
  const tiles = [
    { label: 'Parcels created', value: data?.total.toLocaleString() },
    { label: 'Delivered', value: data?.delivered.toLocaleString() },
    { label: 'Failed', value: data?.failed.toLocaleString() },
    {
      label: 'Success rate',
      value: data ? percent(data.successRate) : undefined,
      note: data ? `${percent(data.failureRate)} failure rate` : undefined,
    },
  ];
  const finished = data ? data.delivered + data.failed : 0;

  return (
    <section>
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
          Operational Reports
        </h1>
        <p className="mt-1 text-slate-500">Deliveries per day and how many succeeded or failed.</p>
      </div>

      {/* Filters: one row above everything they control. */}
      <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:flex-row lg:items-end lg:justify-between">
        <div role="group" aria-label="Date range" className="flex flex-wrap gap-2">
          {PRESETS.map((p) => (
            <button
              key={p.id}
              type="button"
              aria-pressed={preset === p.id}
              onClick={() => choosePreset(p.id, p.range())}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                preset === p.id
                  ? 'bg-slate-900 text-white'
                  : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
        <form onSubmit={applyCustom} className="flex flex-wrap items-end gap-2" noValidate>
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-500">
            From
            <input
              type="date"
              value={draft.from}
              max={toIsoDate(new Date())}
              onChange={(e) => setDraft({ ...draft, from: e.target.value })}
              className={inputClasses}
            />
          </label>
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-500">
            To
            <input
              type="date"
              value={draft.to}
              onChange={(e) => setDraft({ ...draft, to: e.target.value })}
              className={inputClasses}
            />
          </label>
          <Button type="submit" variant={preset === 'custom' ? 'primary' : 'outline'}>
            Apply
          </Button>
        </form>
      </div>
      {draftError && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {draftError}
        </p>
      )}

      <p className="mt-4 text-sm text-slate-500">
        Showing {formatRangeDay(range.from)} – {formatRangeDay(range.to)}
      </p>

      {summary.status === 'error' ? (
        <div className="mt-4 rounded-2xl border border-slate-200 bg-white shadow-sm">
          <ErrorState message={summary.message} onAction={summary.retry} actionLabel="Try again" />
        </div>
      ) : (
        <div
          className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4"
          aria-busy={summary.status === 'loading'}
        >
          {tiles.map((tile) => (
            <div
              key={tile.label}
              className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
            >
              <p className="text-sm text-slate-500">{tile.label}</p>
              {tile.value === undefined ? (
                // Placeholder while the numbers load, so the tiles don't read as "no data".
                <span
                  data-testid="tile-loading"
                  className="mt-3 block h-9 w-24 animate-pulse rounded-lg bg-slate-100"
                  aria-hidden="true"
                />
              ) : (
                <p className="mt-3 text-3xl font-bold tracking-tight text-slate-900 tabular-nums">
                  {tile.value}
                </p>
              )}
              {tile.note && <p className="mt-1 text-xs text-slate-500">{tile.note}</p>}
            </div>
          ))}
          {summary.status === 'loading' && <span className="sr-only">Loading totals...</span>}
        </div>
      )}
      {data && (
        <p className="mt-2 text-xs text-slate-500">
          Rates count finished deliveries only ({finished.toLocaleString()} delivered or failed);
          parcels still on the way aren&apos;t counted against them.
        </p>
      )}

      <div className="mt-8 rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 p-5">
          <h2 className="text-lg font-semibold text-slate-900">Deliveries per day</h2>
          <p className="text-sm text-slate-500">
            Parcels created each day, and parcels delivered or failed that day (Sri Lankan time).
          </p>
        </div>

        {daily.status === 'error' ? (
          <ErrorState message={daily.message} onAction={daily.retry} actionLabel="Try again" />
        ) : daily.status === 'loading' ? (
          <LoadingState title="Loading report..." />
        ) : daily.data.length === 0 ? (
          <EmptyState title="No days in this range" message="Choose another date range." />
        ) : daily.data.every((d) => d.total === 0 && d.delivered === 0 && d.failed === 0) ? (
          // A chart of flat zero lines looks like a broken chart; say what's going on instead.
          <EmptyState
            title="No deliveries in this range"
            message="No parcels were created, delivered or failed on these days. Try a longer range."
            actionLabel="Show last 30 days"
            onAction={() => choosePreset('last30', lastDays(30))}
          />
        ) : (
          <>
            <div className="p-5">
              <DeliveriesChart days={daily.data} />
            </div>
            <div className="overflow-x-auto border-t border-slate-200">
              <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                <caption className="sr-only">Deliveries per day</caption>
                <thead className="bg-slate-50 text-slate-600">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-medium">
                      Day
                    </th>
                    <th scope="col" className="px-5 py-3 text-right font-medium">
                      Created
                    </th>
                    <th scope="col" className="px-5 py-3 text-right font-medium">
                      Delivered
                    </th>
                    <th scope="col" className="px-5 py-3 text-right font-medium">
                      Failed
                    </th>
                    <th scope="col" className="px-5 py-3 text-right font-medium">
                      Success rate
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-slate-700 tabular-nums">
                  {[...daily.data].reverse().map((day) => (
                    <tr key={day.date} className="hover:bg-slate-50">
                      <th scope="row" className="px-5 py-3 text-left font-medium text-slate-900">
                        {formatDay(day.date)}
                      </th>
                      <td className="px-5 py-3 text-right">{day.total.toLocaleString()}</td>
                      <td className="px-5 py-3 text-right">{day.delivered.toLocaleString()}</td>
                      <td className="px-5 py-3 text-right">{day.failed.toLocaleString()}</td>
                      <td className="px-5 py-3 text-right">{daySuccessRate(day)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </section>
  );
}
