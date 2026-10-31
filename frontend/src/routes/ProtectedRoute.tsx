import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import type { Role } from '../types/auth';
import { PATHS } from './paths';
import SessionGate from './SessionGate';

interface ProtectedRouteProps {
  /** Roles allowed to see the nested routes. Omit to allow any signed-in user. */
  allowedRoles?: Role[];
  /** Where signed-out visitors are sent, e.g. the courier sign-in page. Defaults to /login. */
  loginPath?: string;
}

/**
 * Client-side guard for UX only. The backend still enforces every permission;
 * never rely on this for security.
 */
export default function ProtectedRoute({
  allowedRoles,
  loginPath = PATHS.login,
}: ProtectedRouteProps) {
  const { user, status } = useAuth();
  const location = useLocation();

  // After a reload the session is restored first, so signed-in users aren't sent to the login page.
  if (status !== 'ready') return <SessionGate />;

  if (!user) {
    return <Navigate to={loginPath} replace state={{ from: location.pathname }} />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={PATHS.unauthorized} replace />;
  }

  return <Outlet />;
}
