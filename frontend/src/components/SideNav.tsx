import { NavLink } from 'react-router';
import { NAV_ITEMS } from '../routes/paths';
import type { Role } from '../types/auth';

export default function SideNav({ role }: { role: Role }) {
  return (
    <nav className="flex flex-col gap-1 p-3">
      {NAV_ITEMS[role].map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end
          className={({ isActive }) =>
            `rounded px-3 py-2 text-sm ${
              isActive
                ? 'bg-brand-50 font-medium text-brand-700'
                : 'text-slate-700 hover:bg-slate-100'
            }`
          }
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
