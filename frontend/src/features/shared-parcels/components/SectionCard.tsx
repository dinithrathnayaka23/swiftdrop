import type { ReactNode } from 'react';
import { cn } from '../../../utils/cn';

interface SectionCardProps {
  title?: string;
  subtitle?: string;
  /** Shown at the right of the title (a link, a badge, a button). */
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

/** White rounded card used for every section of the parcel pages. */
export default function SectionCard({
  title,
  subtitle,
  action,
  children,
  className,
}: SectionCardProps) {
  return (
    <section
      className={cn('rounded-2xl border border-slate-200 bg-white p-5 shadow-xs sm:p-6', className)}
    >
      {(title || action) && (
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            {title && <h2 className="text-lg font-semibold text-slate-900">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
          </div>
          {action}
        </div>
      )}
      {children}
    </section>
  );
}
