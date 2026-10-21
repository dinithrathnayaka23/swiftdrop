import { Link } from 'react-router-dom';
import { PATHS } from '../../routes/paths';

export default function NotFoundPage() {
  return (
    <section className="flex min-h-screen flex-col items-center justify-center px-4 text-center">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-slate-600">The page you are looking for does not exist.</p>
      <Link to={PATHS.home} className="mt-6 text-brand-600">
        Go home
      </Link>
    </section>
  );
}
