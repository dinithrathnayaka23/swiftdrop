import type { ComponentType } from 'react';

export interface AppRoute {
  /** A value from PATHS. */
  path: string;
  /** Loads the screen on first visit, e.g. () => import('./pages/OrdersPage'). */
  page: () => Promise<{ default: ComponentType }>;
}

/**
 * What each feature's routes.ts exports: its screens, grouped by the area (layout and sign-in
 * check) they appear in. The router (AppRoutes) puts each group behind the right guard.
 */
export interface FeatureRoutes {
  /** Public site with the header and footer (no sign-in). */
  public?: AppRoute[];
  /** Sign-in and registration pages; signed-in users are sent to their dashboard. */
  guest?: AppRoute[];
  /** Like `guest`, but the page brings its own full-screen design (e.g. the courier sign-in). */
  guestFullPage?: AppRoute[];
  /** Pages in the sign-in layout that signed-in users may still open (e.g. email confirmation). */
  authPages?: AppRoute[];
  customer?: AppRoute[];
  merchant?: AppRoute[];
  courier?: AppRoute[];
  admin?: AppRoute[];
}

export type RouteArea = keyof FeatureRoutes;
