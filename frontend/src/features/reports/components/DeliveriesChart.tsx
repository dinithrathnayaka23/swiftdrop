import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from 'react';
import { parseIsoDate } from '../dateRange';
import type { DailyDeliveryStat } from '../types';

/**
 * Categorical slots 1-3 of the data-viz reference palette (validated together in light mode:
 * all-pairs CVD ΔE >= 9.2). The teal sits below 3:1 on white, so every line also carries a direct
 * label and the page has a table view.
 */
const SERIES = [
  { key: 'total', label: 'Created', color: '#2a78d6' },
  { key: 'delivered', label: 'Delivered', color: '#1baf7a' },
  { key: 'failed', label: 'Failed', color: '#eb6834' },
] as const;

type SeriesKey = (typeof SERIES)[number]['key'];

const HEIGHT = 280;
const MARGIN = { top: 16, right: 88, bottom: 32, left: 44 };
const DEFAULT_WIDTH = 720;
/** Draw a marker on every point only while they stay readable. */
const MAX_POINTS_WITH_MARKERS = 31;

const shortDate = (iso: string) =>
  parseIsoDate(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
const longDate = (iso: string) =>
  parseIsoDate(iso).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });

/** A round axis maximum (1, 2 or 5 × 10ⁿ steps) and its ticks, so gridlines land on whole numbers. */
function niceTicks(max: number, count = 4): number[] {
  if (max <= 0) return [0, 1];
  const rough = max / count;
  const power = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 5, 10].map((m) => m * power).find((s) => s >= rough) ?? rough;
  const wholeStep = Math.max(1, Math.round(step));
  const ticks: number[] = [];
  for (let t = 0; t < max + wholeStep; t += wholeStep) ticks.push(t);
  return ticks;
}

/** Spreads end-of-line labels apart so they never overlap (at least {@code gap} px). */
function spreadLabels(ys: number[], gap: number): number[] {
  const order = ys.map((y, i) => ({ y, i })).sort((a, b) => a.y - b.y);
  for (let k = 1; k < order.length; k++) {
    if (order[k].y - order[k - 1].y < gap) order[k].y = order[k - 1].y + gap;
  }
  const out = new Array<number>(ys.length);
  order.forEach(({ y, i }) => (out[i] = y));
  return out;
}

interface DeliveriesChartProps {
  days: DailyDeliveryStat[];
}

/** Created, delivered and failed parcels per day, with a hover/keyboard crosshair and tooltip. */
export default function DeliveriesChart({ days }: DeliveriesChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(DEFAULT_WIDTH);
  const [active, setActive] = useState<number | null>(null);

  // Follow the card's width so text stays its real size on phones and wide screens alike.
  useEffect(() => {
    const element = containerRef.current;
    if (!element || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(([entry]) => {
      setWidth(Math.max(320, Math.round(entry.contentRect.width)));
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const plotWidth = width - MARGIN.left - MARGIN.right;
  const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
  const maxValue = Math.max(0, ...days.flatMap((d) => [d.total, d.delivered, d.failed]));
  const ticks = niceTicks(maxValue);
  const yMax = ticks[ticks.length - 1];

  const x = (i: number) =>
    MARGIN.left + (days.length === 1 ? plotWidth / 2 : (i / (days.length - 1)) * plotWidth);
  const y = (value: number) => MARGIN.top + plotHeight - (value / yMax) * plotHeight;

  // Label every day when there's room, otherwise every n-th one (always including the last).
  const labelEvery = Math.max(1, Math.ceil(days.length / Math.max(2, Math.floor(plotWidth / 64))));
  const showMarkers = days.length <= MAX_POINTS_WITH_MARKERS;

  const last = days.length - 1;
  const labelYs = spreadLabels(
    SERIES.map((s) => y(days[last][s.key])),
    14,
  );

  function indexAt(clientX: number): number {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect || days.length === 1) return 0;
    const px = clientX - rect.left - MARGIN.left;
    return Math.min(last, Math.max(0, Math.round((px / plotWidth) * last)));
  }

  function onKeyDown(e: KeyboardEvent) {
    if (e.key === 'ArrowRight') setActive((i) => Math.min(last, (i ?? -1) + 1));
    else if (e.key === 'ArrowLeft') setActive((i) => Math.max(0, (i ?? last + 1) - 1));
    else if (e.key === 'Home') setActive(0);
    else if (e.key === 'End') setActive(last);
    else if (e.key === 'Escape') setActive(null);
    else return;
    e.preventDefault();
  }

  const day = active !== null ? days[active] : null;
  const tooltipLeft = active !== null ? x(active) : 0;
  const tooltipOnLeft = tooltipLeft > width - 180;

  return (
    <div>
      {/* Legend: always present for several series, so identity never relies on colour alone. */}
      <ul className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-slate-600" aria-label="Legend">
        {SERIES.map((s) => (
          <li key={s.key} className="flex items-center gap-2">
            <span
              className="h-0.5 w-4 rounded-full"
              style={{ backgroundColor: s.color }}
              aria-hidden="true"
            />
            {s.label}
          </li>
        ))}
      </ul>

      <div
        ref={containerRef}
        className="relative mt-3 rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-sky-400"
        tabIndex={0}
        role="group"
        aria-roledescription="chart"
        aria-label={`Deliveries per day, ${days.length} days. Use the arrow keys to read each day.`}
        onMouseMove={(e: MouseEvent) => setActive(indexAt(e.clientX))}
        onMouseLeave={() => setActive(null)}
        onKeyDown={onKeyDown}
        onBlur={() => setActive(null)}
      >
        <svg width={width} height={HEIGHT} className="block max-w-full" aria-hidden="true">
          {/* Recessive grid and y-axis */}
          {ticks.map((t) => (
            <g key={t}>
              <line
                x1={MARGIN.left}
                x2={MARGIN.left + plotWidth}
                y1={y(t)}
                y2={y(t)}
                stroke={t === 0 ? '#cbd5e1' : '#eef2f6'}
                strokeWidth={1}
              />
              <text
                x={MARGIN.left - 8}
                y={y(t)}
                dy="0.32em"
                textAnchor="end"
                className="fill-slate-500 text-[11px]"
              >
                {t.toLocaleString()}
              </text>
            </g>
          ))}

          {/* X-axis labels */}
          {days.map((d, i) =>
            i % labelEvery === 0 || i === last ? (
              <text
                key={d.date}
                x={x(i)}
                y={HEIGHT - MARGIN.bottom + 18}
                textAnchor={
                  days.length === 1 ? 'middle' : i === 0 ? 'start' : i === last ? 'end' : 'middle'
                }
                className="fill-slate-500 text-[11px]"
              >
                {shortDate(d.date)}
              </text>
            ) : null,
          )}

          {/* Crosshair */}
          {active !== null && (
            <line
              x1={x(active)}
              x2={x(active)}
              y1={MARGIN.top}
              y2={MARGIN.top + plotHeight}
              stroke="#94a3b8"
              strokeWidth={1}
              strokeDasharray="3 3"
            />
          )}

          {/* Lines, markers and direct end labels */}
          {SERIES.map((s, si) => {
            const points = days.map((d, i) => `${x(i)},${y(d[s.key as SeriesKey])}`).join(' ');
            return (
              <g key={s.key}>
                <polyline
                  points={points}
                  fill="none"
                  stroke={s.color}
                  strokeWidth={2}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                />
                {days.map((d, i) =>
                  showMarkers || i === active ? (
                    <circle
                      key={d.date}
                      cx={x(i)}
                      cy={y(d[s.key])}
                      r={i === active ? 5 : 4}
                      fill={s.color}
                      stroke="#ffffff"
                      strokeWidth={2}
                    />
                  ) : null,
                )}
                <text
                  x={x(last) + 10}
                  y={labelYs[si]}
                  dy="0.32em"
                  className="fill-slate-600 text-[11px] font-medium"
                >
                  {s.label} {days[last][s.key].toLocaleString()}
                </text>
              </g>
            );
          })}
        </svg>

        {day && (
          <div
            role="tooltip"
            className="pointer-events-none absolute top-2 z-10 w-40 rounded-lg border border-slate-200 bg-white p-3 text-xs shadow-lg"
            style={tooltipOnLeft ? { left: tooltipLeft - 172 } : { left: tooltipLeft + 12 }}
          >
            <p className="font-semibold text-slate-900">{longDate(day.date)}</p>
            <dl className="mt-2 space-y-1">
              {SERIES.map((s) => (
                <div key={s.key} className="flex items-center justify-between gap-3">
                  <dt className="flex items-center gap-1.5 text-slate-600">
                    <span
                      className="size-2 rounded-full"
                      style={{ backgroundColor: s.color }}
                      aria-hidden="true"
                    />
                    {s.label}
                  </dt>
                  <dd className="font-medium text-slate-900 tabular-nums">
                    {day[s.key].toLocaleString()}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        )}
        {/* Screen readers hear the focused day as it changes. */}
        <p className="sr-only" aria-live="polite">
          {day
            ? `${longDate(day.date)}: ${day.total} created, ${day.delivered} delivered, ${day.failed} failed`
            : ''}
        </p>
      </div>
    </div>
  );
}
