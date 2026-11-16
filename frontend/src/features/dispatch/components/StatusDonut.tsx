export interface DonutSegment {
  label: string;
  value: number;
  /** CSS colour of the ring segment and the legend dot. */
  color: string;
}

const RADIUS = 40;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function percent(value: number, total: number): string {
  return total === 0 ? '0%' : `${Math.round((value / total) * 100)}%`;
}

/**
 * A ring chart drawn with SVG (no chart library). The ring is decoration only; the legend beside it
 * carries every number and share as text, so screen readers and copy-paste get the data.
 */
export default function StatusDonut({
  segments,
  totalLabel,
}: {
  segments: DonutSegment[];
  totalLabel: string;
}) {
  const total = segments.reduce((sum, s) => sum + s.value, 0);
  let offset = 0;
  return (
    <div className="flex flex-col items-center gap-6 sm:flex-row">
      <div className="relative size-40 shrink-0">
        <svg viewBox="0 0 100 100" className="size-full -rotate-90" aria-hidden="true">
          <circle cx="50" cy="50" r={RADIUS} fill="none" stroke="#e2e8f0" strokeWidth="12" />
          {total > 0 &&
            segments.map((segment) => {
              const length = (segment.value / total) * CIRCUMFERENCE;
              const circle = (
                <circle
                  key={segment.label}
                  cx="50"
                  cy="50"
                  r={RADIUS}
                  fill="none"
                  stroke={segment.color}
                  strokeWidth="12"
                  strokeDasharray={`${length} ${CIRCUMFERENCE - length}`}
                  strokeDashoffset={-offset}
                />
              );
              offset += length;
              return circle;
            })}
        </svg>
        <div
          className="absolute inset-0 flex flex-col items-center justify-center"
          aria-hidden="true"
        >
          <span className="text-2xl font-bold text-slate-900">{total}</span>
          <span className="text-xs text-slate-500">{totalLabel}</span>
        </div>
      </div>
      <dl className="w-full space-y-2 text-sm">
        {segments.map((segment) => (
          <div key={segment.label} className="flex items-center justify-between gap-4">
            <dt className="flex items-center gap-2 text-slate-700">
              <span
                className="size-2.5 rounded-full"
                style={{ backgroundColor: segment.color }}
                aria-hidden="true"
              />
              {segment.label}
            </dt>
            <dd className="font-medium text-slate-900">
              {segment.value}{' '}
              <span className="font-normal text-slate-500">({percent(segment.value, total)})</span>
            </dd>
          </div>
        ))}
        <div className="flex justify-between gap-4 border-t border-slate-100 pt-2">
          <dt className="text-slate-500">Total</dt>
          <dd className="font-semibold text-slate-900">{total}</dd>
        </div>
      </dl>
    </div>
  );
}
