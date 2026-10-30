import { Link } from 'react-router';
import { PATHS } from '../../routes/paths';

export default function UnauthorizedPage() {
  return (
    <section className="py-12 text-center">
      <h1 className="text-2xl font-semibold">Access denied</h1>
      <p className="mt-2 text-slate-600">
        Your account does not have permission to view this page.
      </p>
      <Link to={PATHS.home} className="mt-6 inline-block text-brand-600">
        Go home
      </Link>
    </section>
  );
}
