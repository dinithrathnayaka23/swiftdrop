import { ArrowRight, ChevronDown, Headset } from 'lucide-react';
import { useState } from 'react';
import { Link, NavLink } from 'react-router';
import Logo from '../../components/brand/Logo';
import { cn } from '../../utils/cn';
import { SUPPORT_EMAIL } from '../support';
import type { NavItem, NavSection } from './navigation';

interface SidebarProps {
  sections: NavSection[];
  homePath: string;
  /** Show the "Need help?" card (customers and merchants). */
  showHelp?: boolean;
  /** Called after a link is chosen, so the mobile drawer can close. */
  onNavigate?: () => void;
}

function NavEntry({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const Icon = item.icon;
  return (
    <NavLink
      to={item.to}
      end
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
          isActive
            ? 'bg-brand-50 text-brand-700'
            : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
        )
      }
    >
      <Icon className="size-5 shrink-0" strokeWidth={1.8} aria-hidden="true" />
      {item.label}
    </NavLink>
  );
}

function Group({ section, onNavigate }: { section: NavSection; onNavigate?: () => void }) {
  const [open, setOpen] = useState(true);
  const Icon = section.icon;
  const listId = `nav-${section.title?.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <div>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={listId}
        onClick={() => setOpen((value) => !value)}
        className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
      >
        {Icon && <Icon className="size-5 text-slate-500" strokeWidth={1.8} aria-hidden="true" />}
        <span className="flex-1 text-left">{section.title}</span>
        <ChevronDown
          className={cn('size-4 text-slate-400 transition-transform', !open && '-rotate-90')}
          aria-hidden="true"
        />
      </button>
      {open && (
        <ul id={listId} className="mt-1 space-y-0.5">
          {section.items.map((item) => (
            <li key={item.to}>
              <NavEntry item={item} onNavigate={onNavigate} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Logo, role menu and (for customers and merchants) a support card. */
export default function Sidebar({
  sections,
  homePath,
  showHelp = false,
  onNavigate,
}: SidebarProps) {
  return (
    <div className="flex h-full flex-col bg-white">
      <div className="flex h-16 shrink-0 items-center border-b border-slate-200 px-5">
        <Link to={homePath} onClick={onNavigate} aria-label="SwiftDrop dashboard">
          <Logo />
        </Link>
      </div>

      <nav aria-label="Main navigation" className="flex-1 overflow-y-auto px-3 py-4">
        <div className="space-y-4">
          {sections.map((section, index) =>
            section.title ? (
              <div
                key={section.title}
                className={cn(index > 0 && 'border-t border-slate-100 pt-4')}
              >
                <Group section={section} onNavigate={onNavigate} />
              </div>
            ) : (
              <ul key={index} className="space-y-1">
                {section.items.map((item) => (
                  <li key={item.to}>
                    <NavEntry item={item} onNavigate={onNavigate} />
                  </li>
                ))}
              </ul>
            ),
          )}
        </div>
      </nav>

      {showHelp && (
        <div className="m-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="flex items-start gap-3">
            <Headset className="mt-0.5 size-5 shrink-0 text-slate-700" aria-hidden="true" />
            <div>
              <p className="text-sm font-semibold text-slate-900">Need help?</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">
                Contact our support team for any assistance.
              </p>
            </div>
          </div>
          <a
            href={`mailto:${SUPPORT_EMAIL}`}
            className="mt-3 flex items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50"
          >
            Contact Support
            <ArrowRight className="size-4" aria-hidden="true" />
          </a>
        </div>
      )}
    </div>
  );
}
