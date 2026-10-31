import { Navigate, Outlet } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import { ROLE_HOME } from './paths';
import SessionGate from './SessionGate';

/** Sign-in/sign-up pages: signed-in users are sent to their dashboard instead. */
export default function GuestRoute() {
  const { user, status } = useAuth();
  // Wait for a session being restored, so a signed-in user doesn't see the sign-in form flash.
  if (status === 'restoring') return <SessionGate />;
  if (user) return <Navigate to={ROLE_HOME[user.role]} replace />;
  return <Outlet />;
}
