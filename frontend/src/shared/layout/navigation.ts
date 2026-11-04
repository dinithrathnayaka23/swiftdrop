import {
  BarChart3,
  Bell,
  Bike,
  Building2,
  CircleCheck,
  CircleUserRound,
  ClipboardList,
  Cog,
  House,
  Inbox,
  Link2,
  MapPin,
  Package,
  PackagePlus,
  PackageX,
  ScanLine,
  Store,
  Tags,
  Truck,
  UserRound,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { PATHS } from '../../routes/paths';
import type { Role } from '../../types/auth';

export interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
}

export interface NavSection {
  /** Shown as a collapsible group heading; sections without a title are a plain list. */
  title?: string;
  icon?: LucideIcon;
  items: NavItem[];
}

/**
 * Sidebar menu for every role, one entry per screen (pre-created for the whole team). Matches the
 * UI designs: customers and merchants get a flat list, the admin gets grouped sections.
 */
export const SIDEBAR: Record<Exclude<Role, 'COURIER'>, NavSection[]> = {
  CUSTOMER: [
    {
      items: [
        { label: 'Dashboard', to: PATHS.customerDashboard, icon: House },
        { label: 'Book a Delivery', to: PATHS.customerBook, icon: PackagePlus },
        { label: 'My Deliveries', to: PATHS.customerDeliveries, icon: ClipboardList },
        { label: 'Notifications', to: PATHS.customerNotifications, icon: Bell },
        { label: 'Profile', to: PATHS.customerProfile, icon: UserRound },
      ],
    },
  ],
  MERCHANT: [
    {
      items: [
        { label: 'Dashboard', to: PATHS.merchantDashboard, icon: House },
        { label: 'Orders', to: PATHS.merchantOrders, icon: ClipboardList },
        { label: 'API Integration', to: PATHS.merchantIntegration, icon: Link2 },
        { label: 'Delivery Updates', to: PATHS.merchantDeliveryUpdates, icon: Bell },
        { label: 'Business Profile', to: PATHS.merchantProfile, icon: Building2 },
      ],
    },
  ],
  DISPATCHER_ADMIN: [
    {
      title: 'Operations',
      icon: Package,
      items: [
        { label: 'Overview', to: PATHS.adminOverview, icon: House },
        { label: 'Unassigned Parcels', to: PATHS.adminUnassigned, icon: Inbox },
        { label: 'Parcels', to: PATHS.adminParcels, icon: Package },
        { label: 'Deliveries', to: PATHS.adminDeliveries, icon: Truck },
        { label: 'Failed Deliveries', to: PATHS.adminFailedDeliveries, icon: PackageX },
        { label: 'Couriers', to: PATHS.adminCouriers, icon: Bike },
      ],
    },
    {
      title: 'Management',
      icon: Users,
      items: [
        { label: 'Merchants', to: PATHS.adminMerchants, icon: Store },
        { label: 'Pricing', to: PATHS.adminPricing, icon: Tags },
        { label: 'Service Areas', to: PATHS.adminServiceAreas, icon: MapPin },
      ],
    },
    {
      title: 'Reports',
      icon: BarChart3,
      items: [{ label: 'Operational Reports', to: PATHS.adminReports, icon: BarChart3 }],
    },
    {
      title: 'System',
      icon: Cog,
      items: [
        { label: 'Users', to: PATHS.adminUsers, icon: Users },
        { label: 'Settings', to: PATHS.adminSettings, icon: Cog },
      ],
    },
  ],
};

/** Bottom tab bar of the courier app (phones). */
export const COURIER_TABS: NavItem[] = [
  { label: "Today's Deliveries", to: PATHS.courierHome, icon: House },
  { label: 'Scan Parcel', to: PATHS.courierScan, icon: ScanLine },
  { label: 'Completed', to: PATHS.courierCompleted, icon: CircleCheck },
  { label: 'Account', to: PATHS.courierAccount, icon: CircleUserRound },
];
