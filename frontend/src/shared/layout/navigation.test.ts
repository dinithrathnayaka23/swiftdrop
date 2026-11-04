import { matchPath } from 'react-router';
import { routesFor } from '../../routes/features';
import { PATHS, ROLE_ACCOUNT, ROLE_HOME } from '../../routes/paths';
import type { RouteArea } from '../../routes/types';
import { ROLES, type Role } from '../../types/auth';
import { COURIER_TABS, SIDEBAR } from './navigation';

const AREA: Record<Role, RouteArea> = {
  CUSTOMER: 'customer',
  MERCHANT: 'merchant',
  COURIER: 'courier',
  DISPATCHER_ADMIN: 'admin',
};

const ALL_AREAS: RouteArea[] = [
  'public',
  'guest',
  'guestFullPage',
  'authPages',
  'customer',
  'merchant',
  'courier',
  'admin',
];

function hasRoute(role: Role, to: string): boolean {
  return routesFor(AREA[role]).some((route) => matchPath(route.path, to));
}

describe('navigation', () => {
  it.each(['CUSTOMER', 'MERCHANT', 'DISPATCHER_ADMIN'] as const)(
    'every %s menu entry opens a screen of that role',
    (role) => {
      const links = SIDEBAR[role].flatMap((section) => section.items.map((item) => item.to));
      expect(links.length).toBeGreaterThan(0);
      for (const to of links) expect(hasRoute(role, to)).toBe(true);
    },
  );

  it('every courier tab opens a courier screen', () => {
    for (const tab of COURIER_TABS) expect(hasRoute('COURIER', tab.to)).toBe(true);
  });

  it.each(ROLES)('the %s home and account pages exist', (role) => {
    expect(hasRoute(role, ROLE_HOME[role])).toBe(true);
    expect(hasRoute(role, ROLE_ACCOUNT[role])).toBe(true);
  });

  it('registers each path only once', () => {
    const paths = ALL_AREAS.flatMap((area) => routesFor(area).map((route) => route.path));
    expect(new Set(paths).size).toBe(paths.length);
  });

  it('gives every screen in PATHS a route (except the development UI kit in tests)', () => {
    const registered = new Set(ALL_AREAS.flatMap((area) => routesFor(area)).map((r) => r.path));
    const missing = Object.values(PATHS).filter(
      (path) => path !== PATHS.uiKit && !registered.has(path),
    );
    expect(missing).toEqual([]);
  });
});
