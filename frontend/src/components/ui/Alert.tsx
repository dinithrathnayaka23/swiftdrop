import { CircleAlert, CircleCheck, Info, TriangleAlert, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../../utils/cn';

type AlertTone = 'error' | 'success' | 'info' | 'warning';

const TONES: Record<AlertTone, { box: string; icon: LucideIcon }> = {
  error: { box: 'border-red-200 bg-red-50 text-red-800', icon: CircleAlert },
  success: { box: 'border-brand-200 bg-brand-50 text-brand-800', icon: CircleCheck },
  info: { box: 'border-sky-200 bg-sky-50 text-sky-800', icon: Info },
  warning: { box: 'border-amber-200 bg-amber-50 text-amber-800', icon: TriangleAlert },
};

interface AlertProps {
  tone?: AlertTone;
  title?: string;
  children: ReactNode;
  className?: string;
}

export default function Alert({ tone = 'info', title, children, className }: AlertProps) {
  const { box, icon: Icon } = TONES[tone];
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-lg border px-4 py-3 text-sm', box, className)}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <div className={cn(title && 'mt-0.5')}>{children}</div>
      </div>
    </div>
  );
}
