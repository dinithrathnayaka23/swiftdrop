import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import type { Role } from '../types/auth';
import { PATHS } from './paths';

interface ProtectedRouteProps {
  /** Roles allowed to see the nested routes. Omit to allow any signed-in user. */
  allowedRoles?: Role[];
}

/**
 * Client-side guard for UX only. The backend still enforces every permission;
 * never rely on this for security.
 */
export default function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to={PATHS.login} replace state={{ from: location.pathname }} />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={PATHS.unauthorized} replace />;
  }

  return <Outlet />;
}
