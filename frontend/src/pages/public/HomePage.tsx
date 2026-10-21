import { Link } from 'react-router-dom';
import { useApiHealth } from '../../hooks/useApiHealth';
import { PATHS } from '../../routes/paths';

const STATUS_STYLES = {
  checking: 'bg-slate-100 text-slate-600',
  up: 'bg-green-100 text-green-800',
  down: 'bg-red-100 text-red-800',
} as const;

export default function HomePage() {
  const apiHealth = useApiHealth();

  return (
    <section className="py-8 text-center">
      <h1 className="text-3xl font-semibold sm:text-4xl">SwiftDrop</h1>
      <p className="mt-2 text-slate-600">Parcel delivery &amp; courier management</p>

      <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
        <Link
          to={PATHS.track}
          className="rounded bg-brand-600 px-5 py-2.5 text-white hover:bg-brand-700"
        >
          Track a parcel
        </Link>
        <Link
          to={PATHS.login}
          className="rounded border border-slate-300 bg-white px-5 py-2.5 text-slate-700 hover:bg-slate-100"
        >
          Sign in
        </Link>
      </div>

      <p className="mt-10 text-xs">
        <span
          className={`rounded-full px-2.5 py-1 ${STATUS_STYLES[apiHealth]}`}
          data-testid="api-status"
        >
          API: {apiHealth}
        </span>
      </p>
    </section>
  );
}
