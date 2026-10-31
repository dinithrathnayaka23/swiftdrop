import { cn } from '../../utils/cn';

/** Shared look for text inputs, selects and textareas. */
export function inputClasses(hasError: boolean, className?: string): string {
  return cn(
    'block w-full rounded-lg border bg-white px-3.5 text-sm text-slate-900 shadow-xs transition',
    'placeholder:text-slate-400 focus:ring-4 focus:outline-none disabled:bg-slate-50 disabled:text-slate-500',
    hasError
      ? 'border-red-400 focus:border-red-500 focus:ring-red-100'
      : 'border-slate-300 focus:border-brand-500 focus:ring-brand-100',
    className,
  );
}

/** aria-describedby value for a field's error or hint text. */
export function describedBy(id: string, error?: string, hint?: string): string | undefined {
  if (error) return `${id}-error`;
  if (hint) return `${id}-hint`;
  return undefined;
}
