import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import adminLiveTracking from '../features/admin-live-tracking/routes';
import adminMerchants from '../features/admin-merchants/routes';
import adminParcels from '../features/admin-parcels/routes';
import adminPricing from '../features/admin-pricing/routes';
import adminSettings from '../features/admin-settings/routes';
import adminUsers from '../features/admin-users/routes';
import auth from '../features/auth/routes';
import booking from '../features/booking/routes';
import courierApp from '../features/courier-app/routes';
import couriers from '../features/couriers/routes';
import customerDashboard from '../features/customer-dashboard/routes';
import dispatch from '../features/dispatch/routes';
import landing from '../features/landing/routes';
import merchantPortal from '../features/merchant-portal/routes';
import merchantRegister from '../features/merchant-register/routes';
import merchantWebhooks from '../features/merchant-webhooks/routes';
import myDeliveries from '../features/my-deliveries/routes';
import notifications from '../features/notifications/routes';
import profile from '../features/profile/routes';
import publicTracking from '../features/public-tracking/routes';
import reports from '../features/reports/routes';
import serviceAreas from '../features/service-areas/routes';
import shared from '../shared/routes';
import type { AppRoute, FeatureRoutes, RouteArea } from './types';

/** Every feature's routes file. Pre-created for the whole team; the list itself rarely changes. */
export const FEATURES: FeatureRoutes[] = [
  shared,
  auth,
  adminSettings,
  merchantRegister,
  adminUsers,
  adminMerchants,
  reports,
  profile,
  booking,
  adminPricing,
  customerDashboard,
  myDeliveries,
  adminParcels,
  landing,
  publicTracking,
  adminLiveTracking,
  dispatch,
  couriers,
  serviceAreas,
  courierApp,
  merchantPortal,
  merchantWebhooks,
  notifications,
];

/** All routes for one area, from every feature. */
export function routesFor(area: RouteArea, features: FeatureRoutes[] = FEATURES): AppRoute[] {
  return features.flatMap((feature) => feature[area] ?? []);
}

const lazyPages = new Map<AppRoute['page'], LazyExoticComponent<ComponentType>>();

/** The screen component for a route, loaded on first visit. Created once per page loader. */
export function pageComponent(route: AppRoute): LazyExoticComponent<ComponentType> {
  let component = lazyPages.get(route.page);
  if (!component) {
    component = lazy(route.page);
    lazyPages.set(route.page, component);
  }
  return component;
}
