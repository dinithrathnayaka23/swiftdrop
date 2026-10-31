import { Outlet, useLocation } from 'react-router';
import AuthShowcase, { type ShowcaseContent } from '../components/auth/AuthShowcase';
import { PATHS } from '../routes/paths';

const CUSTOMER_FEATURES = [
  'Live tracking from pickup to doorstep',
  'Instant delivery fee estimates',
  'All your deliveries in one place',
];

const MERCHANT_FEATURES = [
  'Order API secured with API keys',
  'Real-time status updates by webhook',
  'A dashboard for every shipment',
];

const MERCHANT_SHOWCASE: ShowcaseContent = {
  eyebrow: 'For businesses',
  title: 'Grow your business with reliable delivery.',
  description:
    'Connect your store, push orders through our API and keep your customers updated automatically.',
  features: MERCHANT_FEATURES,
};

const SHOWCASE: Record<string, ShowcaseContent> = {
  [PATHS.adminLogin]: {
    eyebrow: 'Admin portal',
    title: 'Run dispatch for the whole network.',
    description:
      'Assign couriers, approve merchants and keep an eye on every parcel from pickup to doorstep.',
    features: [
      'Assign pickups and deliveries to couriers',
      'Review and approve merchant applications',
      'Reports across every delivery',
    ],
  },
  [PATHS.login]: {
    eyebrow: 'Welcome back',
    title: 'Track, manage and deliver, all in one place.',
    description:
      'Sign in to follow your parcels in real time, manage deliveries and stay updated at every step.',
    features: CUSTOMER_FEATURES,
  },
  [PATHS.signup]: {
    eyebrow: 'Join SwiftDrop',
    title: 'Send parcels anywhere in Sri Lanka.',
    description:
      'Create a free account to book deliveries, compare fees instantly and follow every parcel live.',
    features: CUSTOMER_FEATURES,
  },
  [PATHS.merchantSignup]: MERCHANT_SHOWCASE,
  [PATHS.merchantSubmitted]: MERCHANT_SHOWCASE,
  [PATHS.checkEmail]: {
    eyebrow: 'Almost there',
    title: 'One last step: confirm your email.',
    description: 'Confirming your address keeps your account and deliveries safe.',
    features: CUSTOMER_FEATURES,
  },
  [PATHS.verifyEmail]: {
    eyebrow: 'Account help',
    title: 'Confirming your email.',
    description: 'Confirming your address keeps your account and deliveries safe.',
    features: CUSTOMER_FEATURES,
  },
  [PATHS.resetPassword]: {
    eyebrow: 'Account help',
    title: 'Choose a new password.',
    description: 'Set a strong new password to keep your deliveries and account secure.',
    features: CUSTOMER_FEATURES,
  },
  [PATHS.forgotPassword]: {
    eyebrow: 'Account help',
    title: "Locked out? We've got you covered.",
    description: 'Reset your password in a few steps and get straight back to your deliveries.',
    features: CUSTOMER_FEATURES,
  },
};

/**
 * Split layout for sign-in and registration: brand panel on the left (large screens only),
 * form on the right. On phones and tablets the form fills the screen.
 */
export default function AuthLayout() {
  const { pathname } = useLocation();
  const showcase = SHOWCASE[pathname] ?? SHOWCASE[PATHS.login];

  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-2 lg:bg-white">
      <AuthShowcase {...showcase} />

      <main className="relative flex min-h-screen flex-col justify-center overflow-hidden px-4 py-10 sm:px-6 sm:py-14 lg:px-12 xl:px-20">
        {/* soft brand glow behind the card on small screens */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 top-0 h-72 bg-gradient-to-b from-brand-50 to-transparent lg:hidden"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-24 -right-24 size-72 rounded-full bg-brand-100/50 blur-3xl lg:hidden"
        />
        <div className="relative mx-auto w-full max-w-md">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
