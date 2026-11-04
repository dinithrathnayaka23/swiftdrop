# Frontend features

Each folder here belongs to one team member (see the scope division). Every screen already has a
URL, a menu entry and a placeholder page, so you can start building straight away and only ever
edit files inside your own folders.

## Building your screen

1. Open your folder's `routes.ts`. Your screens are listed there, for example:

   ```ts
   customer: [{ path: PATHS.customerDeliveries, page: () => import('./pages/MyDeliveriesPage') }],
   ```

2. Replace the placeholder in `pages/<Screen>Page.tsx` with the real screen. Keep the default export.
3. Put components, hooks and API calls only your feature uses next to it (for example
   `components/`, `hooks/`, `api.ts` inside your folder). Tests go next to the file they test.

You don't need to touch the router, the layouts or the menus.

- **Links:** use `PATHS` from `src/routes/paths.ts`. For paths with a parameter, use
  `generatePath(PATHS.adminParcelDetail, { parcelId })`.
- **New screen or URL:** ask Member 1 to add the path and menu entry, then add the route to your own
  `routes.ts`.

## What the shell gives you

- **Layouts:** sidebar, top bar (tracking search, notification bell, account menu) and the courier
  bottom tabs, from `src/shared/layout/`.
- **Guards:** only the right role reaches your page, and signed-out users are sent to the right
  sign-in page.
- **Loading:** a loading state appears while your page's code loads.
- **Crashes:** a crash in your page shows "Something Went Wrong" with "Try Again", and the menu stays
  usable.
- **404:** unknown URLs show "Page Not Found" inside the layout.

Shared building blocks are in `src/components/`:

| Need                    | Use                                  |
| ----------------------- | ------------------------------------ |
| Loading                 | `feedback/LoadingState`              |
| Nothing to show         | `feedback/States` → `EmptyState`     |
| Saved / done            | `feedback/States` → `SuccessState`   |
| Action failed           | `feedback/States` → `FailureState`   |
| Couldn't load data      | `feedback/States` → `ErrorState`     |
| "Are you sure?"         | `feedback/ConfirmDialog`             |
| Buttons, inputs, alerts | `ui/`                                |
| Signed-in user          | `useAuth()` from `src/hooks/useAuth` |

To see them all, run the dev server and open `/dev/ui`.

### System settings

The admin's System Settings apply everywhere, so use them instead of hard-coding:

- **Dates and times:** `useDateFormat()` (`src/hooks/useDateFormat`) gives `formatDateTime`,
  `formatShortDateTime` and `formatDate` in the admin's time zone and formats.
- **Everything else public:** `useSettings().settings` has the support email and phone, items per page,
  default parcel type and the maximum parcel weight and size (for the booking form).
- **Backend:** inject `SystemSettingsService` and call `current()` for any setting (for example the
  default failed-delivery action, auto-assign, or the admin notification switches).

## Owners

| Folder                     | Member | Screens                                                                                                         |
| -------------------------- | ------ | --------------------------------------------------------------------------------------------------------------- |
| `auth`                     | 1      | Sign-in pages, customer sign-up, password reset, email confirmation, courier Account tab                        |
| `admin-settings`           | 1      | Admin: System Settings                                                                                          |
| `merchant-register`        | 2      | Merchant registration, Registration Submitted                                                                   |
| `admin-users`              | 2      | Admin: User Management                                                                                          |
| `admin-merchants`          | 2      | Admin: Merchant Management and merchant detail                                                                  |
| `reports`                  | 2      | Admin: Operational Reports                                                                                      |
| `profile`                  | 2      | Customer: Profile & Account                                                                                     |
| `booking`                  | 3      | Book a Delivery                                                                                                 |
| `admin-pricing`            | 3      | Admin: Pricing Rules                                                                                            |
| `customer-dashboard`       | 4      | Customer: Dashboard                                                                                             |
| `my-deliveries`            | 4      | Customer: My Deliveries, Delivery Tracking (logged in), receipt                                                 |
| `admin-parcels`            | 4      | Admin: Parcels list and detail                                                                                  |
| `landing`                  | 5      | Landing page                                                                                                    |
| `public-tracking`          | 5      | Public tracking search and results                                                                              |
| `admin-live-tracking`      | 5      | Admin: Deliveries Tracking (live)                                                                               |
| `shared-live`              | 5      | Live-update hook (create the folder when you start)                                                             |
| `dispatch`                 | 6      | Admin: Operations Overview, Unassigned Parcels, Failed Deliveries                                               |
| `couriers`                 | 6      | Admin: Couriers                                                                                                 |
| `service-areas`            | 6      | Admin: Service Areas                                                                                            |
| `courier-app`              | 7      | Today's Deliveries, Delivery Details, Update Status, Proof of Delivery, Failed Delivery, Scan Parcel, Completed |
| `merchant-portal`          | 8      | Merchant: Dashboard, Orders, Order Details, API Integration, Business Profile                                   |
| `merchant-webhooks`        | 9      | Merchant: Delivery Updates (Member 9 also adds the status/docs section of API Integration)                      |
| `notifications`            | 10     | Customer: Notifications                                                                                         |
| `shared-notification-bell` | 10     | The bell in every top bar (`NotificationBell.tsx` is a stub to replace)                                         |

Shared files owned by Member 1: `src/routes/` (router, paths, feature list), `src/shared/` (layouts,
guards, error pages) and the shared UI in `src/components/`.
