import { Construction } from 'lucide-react';
import type { ReactNode } from 'react';
import StateView from '../components/feedback/StateView';

interface ScreenPlaceholderProps {
  title: string;
  description: string;
  /** Extra content, e.g. the URL parameter the screen received. */
  children?: ReactNode;
}

/**
 * Pre-created screen: page heading plus a "being built" note. Each feature owner replaces their
 * page's body with the real screen; the route and menu entry already work.
 */
export default function ScreenPlaceholder({
  title,
  description,
  children,
}: ScreenPlaceholderProps) {
  return (
    <section>
      <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">{title}</h1>
      <p className="mt-1 text-slate-500">{description}</p>
      <div className="mt-6 rounded-2xl border border-dashed border-slate-300 bg-white">
        <StateView
          icon={Construction}
          tone="brand"
          title="Coming soon"
          message={children ?? 'This screen is being built.'}
        />
      </div>
    </section>
  );
}
