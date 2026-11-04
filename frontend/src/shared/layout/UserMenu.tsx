import { ChevronDown, LogOut, UserRound } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { PATHS, ROLE_ACCOUNT, ROLE_SIGN_IN } from '../../routes/paths';
import type { Role } from '../../types/auth';
import { cn } from '../../utils/cn';
import { formatRole, initials } from '../../utils/format';

/** After signing out: staff go back to their own sign-in page, others to the website. */
function afterSignOut(role: Role): string {
  return role === 'COURIER' || role === 'DISPATCHER_ADMIN' ? ROLE_SIGN_IN[role] : PATHS.home;
}

interface UserMenuProps {
  /** Only the avatar (courier app on phones); otherwise the name and role are shown too. */
  compact?: boolean;
}

/** Avatar button with the user's name and role; opens a menu with the account page and sign out. */
export default function UserMenu({ compact = false }: UserMenuProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!user) return null;

  const signOut = () => {
    setOpen(false);
    void logout();
    navigate(afterSignOut(user.role), { replace: true });
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={`Account menu for ${user.displayName}`}
        onClick={() => setOpen((value) => !value)}
        className="flex items-center gap-3 rounded-full p-0.5 text-left hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-brand-600 sm:rounded-xl sm:pr-2"
      >
        <span
          aria-hidden="true"
          className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-sm font-semibold text-brand-700"
        >
          {initials(user.displayName)}
        </span>
        {!compact && (
          <>
            <span className="hidden min-w-0 sm:block">
              <span className="block max-w-40 truncate text-sm font-semibold text-slate-900">
                {user.displayName}
              </span>
              <span className="block text-xs text-slate-500">{formatRole(user.role)}</span>
            </span>
            <ChevronDown className="hidden size-4 text-slate-500 sm:block" aria-hidden="true" />
          </>
        )}
      </button>

      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label="Account"
          className="absolute right-0 z-40 mt-2 w-56 overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-lg"
        >
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="truncate text-sm font-semibold text-slate-900">{user.displayName}</p>
            <p className="text-xs text-slate-500">{formatRole(user.role)}</p>
          </div>
          <Link
            role="menuitem"
            to={ROLE_ACCOUNT[user.role]}
            onClick={() => setOpen(false)}
            className={cn(menuItem, 'text-slate-700')}
          >
            <UserRound className="size-4" aria-hidden="true" />
            {user.role === 'DISPATCHER_ADMIN' ? 'System Settings' : 'My Account'}
          </Link>
          <button
            type="button"
            role="menuitem"
            onClick={signOut}
            className={cn(menuItem, 'w-full text-red-600')}
          >
            <LogOut className="size-4" aria-hidden="true" />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

const menuItem = 'flex items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-slate-50';
