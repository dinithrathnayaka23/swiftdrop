import { cn } from '../../../utils/cn';
import { TONE_PILL, type Visual } from './statusVisuals';

/** Status badge with an icon, as in the customer and admin tables. */
export default function StatusPill({ visual, className }: { visual: Visual; className?: string }) {
  const Icon = visual.icon;
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-semibold whitespace-nowrap',
        TONE_PILL[visual.tone],
        className,
      )}
    >
      <Icon className="size-3.5" aria-hidden="true" />
      {visual.label}
    </span>
  );
}
