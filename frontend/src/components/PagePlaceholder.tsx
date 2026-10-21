import type { ReactNode } from 'react';

interface PagePlaceholderProps {
  title: string;
  description: string;
  children?: ReactNode;
}

/** Temporary page body used until each screen is designed and built. */
export default function PagePlaceholder({ title, description, children }: PagePlaceholderProps) {
  return (
    <section>
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="mt-1 text-slate-600">{description}</p>
      <div className="mt-6 rounded-lg border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-500">
        {children ?? 'Placeholder. This screen has not been built yet.'}
      </div>
    </section>
  );
}
