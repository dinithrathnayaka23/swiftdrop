import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '../../../utils/cn';
import { pageItems } from '../pagination';

interface PaginationProps {
  /** Zero-based. */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export default function Pagination({ page, totalPages, onChange }: PaginationProps) {
  const button =
    'inline-flex size-9 items-center justify-center rounded-lg border text-sm font-medium focus-visible:outline-2 focus-visible:outline-brand-600 disabled:pointer-events-none disabled:opacity-40';
  return (
    <nav aria-label="Pagination" className="flex items-center gap-2">
      <button
        type="button"
        className={cn(button, 'border-slate-200 text-slate-600 hover:bg-slate-50')}
        onClick={() => onChange(page - 1)}
        disabled={page <= 0}
        aria-label="Previous page"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
      </button>
      {pageItems(page, totalPages).map((item, i) =>
        item === 'gap' ? (
          <span key={`gap-${i}`} className="px-1 text-slate-400" aria-hidden="true">
            …
          </span>
        ) : (
          <button
            key={item}
            type="button"
            onClick={() => onChange(item)}
            aria-current={item === page ? 'page' : undefined}
            className={cn(
              button,
              item === page
                ? 'border-brand-700 bg-brand-700 text-white'
                : 'border-transparent text-slate-600 hover:bg-slate-50',
            )}
          >
            {item + 1}
          </button>
        ),
      )}
      <button
        type="button"
        className={cn(button, 'border-slate-200 text-slate-600 hover:bg-slate-50')}
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="Next page"
      >
        <ChevronRight className="size-4" aria-hidden="true" />
      </button>
    </nav>
  );
}
