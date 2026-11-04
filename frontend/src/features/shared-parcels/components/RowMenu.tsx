import { EllipsisVertical, MoreHorizontal } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { cn } from '../../../utils/cn';

export interface RowMenuItem {
  label: string;
  onSelect: () => void;
  disabled?: boolean;
}

/** "…" button with a small menu of row actions. Closes on outside click and Escape. */
export default function RowMenu({
  label,
  items,
  vertical = false,
}: {
  /** Accessible name, e.g. "More actions for SD123". */
  label: string;
  items: RowMenuItem[];
  vertical?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    function onPointer(event: PointerEvent) {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const Icon = vertical ? EllipsisVertical : MoreHorizontal;
  return (
    <div ref={ref} className="relative inline-block">
      <button
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => setOpen((value) => !value)}
        className="inline-flex size-9 items-center justify-center rounded-lg text-slate-600 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-brand-600"
      >
        <Icon className="size-5" aria-hidden="true" />
      </button>
      {open && (
        <ul
          id={menuId}
          role="menu"
          className="absolute right-0 z-20 mt-1 w-48 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 text-sm shadow-lg"
        >
          {items.map((item) => (
            <li key={item.label} role="none">
              <button
                type="button"
                role="menuitem"
                disabled={item.disabled}
                onClick={() => {
                  setOpen(false);
                  item.onSelect();
                }}
                className={cn(
                  'block w-full px-3 py-2 text-left text-slate-700 hover:bg-slate-50',
                  'disabled:cursor-not-allowed disabled:text-slate-400 disabled:hover:bg-white',
                )}
              >
                {item.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
