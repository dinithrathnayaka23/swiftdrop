import type { ReactNode } from 'react';
import { useLocation } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { useSettings } from '../../hooks/useSettings';
import { PATHS } from '../../routes/paths';
import MaintenancePage from '../errors/MaintenancePage';

/**
 * While maintenance mode is on, everyone but the admin sees the maintenance page. The admin
 * sign-in and admin area stay open so the admin can switch it off. (The backend enforces this
 * too; this is the friendly face of it.)
 */
export default function MaintenanceGate({ children }: { children: ReactNode }) {
  const { settings } = useSettings();
  const { user } = useAuth();
  const { pathname } = useLocation();

  const adminArea =
    pathname === PATHS.adminOverview || pathname.startsWith(`${PATHS.adminOverview}/`);
  if (settings.maintenanceMode && user?.role !== 'DISPATCHER_ADMIN' && !adminArea) {
    return <MaintenancePage />;
  }
  return <>{children}</>;
}
