import { X } from 'lucide-react';
import { useEffect, useId, useRef, type ReactNode } from 'react';
import { cn } from '../../../utils/cn';

interface ModalProps {
  open: boolean;
  title: string;
  /** Optional line under the title. */
  description?: string;
  onClose: () => void;
  /** The form or content; put the action buttons in {@code footer}. */
  children: ReactNode;
  footer?: ReactNode;
  /** Wider for forms with tables (coverage rows). */
  size?: 'md' | 'lg';
}

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * An accessible dialog for forms (Add Courier, Add Service Area, ...): labelled by its title, closes
 * on Escape or a click outside, keeps Tab inside, and gives focus back to what opened it.
 * ConfirmDialog stays the choice for yes/no confirmations.
 */
export default function Modal({
  open,
  title,
  description,
  onClose,
  children,
  footer,
  size = 'md',
}: ModalProps) {
  const titleId = useId();
  const descriptionId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!open) return;
    const opener = document.activeElement as HTMLElement | null;
    const panel = panelRef.current;
    // The first form field if there is one, else the first control (the Close button).
    (
      panel?.querySelector<HTMLElement>(
        'input:not([disabled]), select:not([disabled]), textarea:not([disabled])',
      ) ?? panel?.querySelector<HTMLElement>(FOCUSABLE)
    )?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onCloseRef.current();
        return;
      }
      if (e.key !== 'Tab' || !panel) return;
      const items = [...panel.querySelectorAll<HTMLElement>(FOCUSABLE)];
      if (items.length === 0) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      opener?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-0 backdrop-blur-[2px] sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        className={cn(
          'flex max-h-[95vh] w-full flex-col rounded-t-2xl bg-white shadow-xl sm:rounded-2xl',
          size === 'lg' ? 'sm:max-w-2xl' : 'sm:max-w-lg',
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-4">
          <div>
            <h2 id={titleId} className="text-lg font-semibold text-slate-900">
              {title}
            </h2>
            {description && (
              <p id={descriptionId} className="mt-1 text-sm text-slate-500">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
            aria-label="Close"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && (
          <div className="flex flex-col-reverse gap-3 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
