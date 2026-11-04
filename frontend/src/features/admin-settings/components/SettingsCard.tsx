import type { ReactNode } from 'react';

interface SettingsCardProps {
  title: string;
  description: string;
  children: ReactNode;
}

/** One white card of related settings, as in the System Settings design. */
export default function SettingsCard({ title, description, children }: SettingsCardProps) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs sm:p-6">
      <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
      <p className="mt-0.5 text-sm text-slate-500">{description}</p>
      <div className="mt-5">{children}</div>
    </section>
  );
}
