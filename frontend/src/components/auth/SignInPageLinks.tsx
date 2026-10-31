import { Link } from 'react-router';
import { PATHS } from '../../routes/paths';
import type { LoginPortal } from '../../types/auth';

const PAGES: { portal: LoginPortal; label: string; to: string }[] = [
  { portal: 'PUBLIC', label: 'Main sign-in', to: PATHS.login },
  { portal: 'COURIER', label: 'Courier app', to: PATHS.courierLogin },
  { portal: 'ADMIN', label: 'Admin portal', to: PATHS.adminLogin },
];

/** Links to the other sign-in pages, shown when an account signed in on the wrong one. */
export default function SignInPageLinks({ current }: { current: LoginPortal }) {
  return (
    <p className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
      {PAGES.filter((page) => page.portal !== current).map((page) => (
        <Link key={page.portal} to={page.to} className="font-semibold underline underline-offset-2">
          {page.label}
        </Link>
      ))}
    </p>
  );
}
