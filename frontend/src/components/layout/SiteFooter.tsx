import { ArrowRight, ArrowUp, Mail, MapPin, PackageSearch } from 'lucide-react';
import { Link } from 'react-router';
import { PATHS } from '../../routes/paths';
import Logo from '../brand/Logo';
import { buttonClasses } from '../ui/buttonStyles';

const SUPPORT_EMAIL = 'support@swiftdrop.lk';

const LINK_GROUPS: { title: string; links: { label: string; to: string }[] }[] = [
  {
    title: 'Services',
    links: [
      { label: 'Track a Parcel', to: PATHS.track },
      { label: 'Send a Parcel', to: PATHS.signup },
      { label: 'For Businesses', to: PATHS.merchantSignup },
      { label: 'Why SwiftDrop', to: '/#services' },
    ],
  },
  {
    title: 'Account',
    links: [
      { label: 'Sign In', to: PATHS.login },
      { label: 'Create Account', to: PATHS.signup },
      { label: 'Register a Business', to: PATHS.merchantSignup },
      { label: 'Forgot Password', to: PATHS.forgotPassword },
    ],
  },
  {
    title: 'Support',
    links: [
      { label: 'Help Centre', to: '/#help' },
      { label: 'Contact Support', to: `mailto:${SUPPORT_EMAIL}` },
    ],
  },
];

function FooterLink({ label, to }: { label: string; to: string }) {
  const className =
    'text-sm text-brand-100/80 transition-colors hover:text-white focus-visible:text-white focus-visible:outline-none';
  return to.startsWith('mailto:') ? (
    <a href={to} className={className}>
      {label}
    </a>
  ) : (
    <Link to={to} className={className}>
      {label}
    </Link>
  );
}

/** Site-wide footer for public pages: call to action, link columns, contact and copyright. */
export default function SiteFooter() {
  return (
    <footer className="relative mt-16 bg-brand-900 text-white">
      {/* Call to action card, overlapping the top edge of the footer */}
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="relative -mt-12 mb-12 flex flex-col gap-6 overflow-hidden rounded-2xl bg-gradient-to-br from-brand-600 to-brand-700 px-6 py-8 shadow-xl shadow-brand-900/20 sm:px-10 md:flex-row md:items-center md:justify-between">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(rgba(255,255,255,0.14)_1px,transparent_1px)] [background-size:20px_20px]"
          />
          <div className="relative">
            <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">
              Ready to send a parcel?
            </h2>
            <p className="mt-2 max-w-xl text-brand-50/90">
              Book a delivery in minutes, or follow a parcel that&apos;s already on its way.
            </p>
          </div>
          <div className="relative flex flex-col gap-3 sm:flex-row">
            <Link
              to={PATHS.track}
              className={buttonClasses({
                variant: 'outline',
                size: 'lg',
                className: 'border-white/70 bg-white/10 text-white hover:bg-white/20',
              })}
            >
              <PackageSearch className="size-5" aria-hidden="true" />
              Track Parcel
            </Link>
            <Link
              to={PATHS.signup}
              className={buttonClasses({
                variant: 'outline',
                size: 'lg',
                className: 'border-white text-brand-800 hover:bg-brand-50',
              })}
            >
              Create Account
              <ArrowRight className="size-5" aria-hidden="true" />
            </Link>
          </div>
        </div>
      </div>

      {/* Brand + link columns */}
      <div className="mx-auto grid max-w-7xl grid-cols-2 gap-x-6 gap-y-10 px-4 pb-12 sm:px-6 lg:grid-cols-5 lg:px-8">
        <div className="col-span-2">
          <Link
            to={PATHS.home}
            aria-label="SwiftDrop home"
            className="inline-flex rounded-xl bg-white px-3.5 py-2 shadow-sm"
          >
            <Logo />
          </Link>
          <p className="mt-5 max-w-sm text-sm leading-6 text-brand-100/80">
            SwiftDrop connects businesses and people with fast, secure and reliable parcel delivery,
            with live tracking from pickup to doorstep.
          </p>
          <ul className="mt-6 space-y-3 text-sm text-brand-100/90">
            <li>
              <a
                href={`mailto:${SUPPORT_EMAIL}`}
                className="inline-flex items-center gap-2.5 transition-colors hover:text-white"
              >
                <span className="flex size-8 items-center justify-center rounded-lg bg-white/10">
                  <Mail className="size-4" aria-hidden="true" />
                </span>
                {SUPPORT_EMAIL}
              </a>
            </li>
            <li className="inline-flex items-center gap-2.5">
              <span className="flex size-8 items-center justify-center rounded-lg bg-white/10">
                <MapPin className="size-4" aria-hidden="true" />
              </span>
              Delivering across Sri Lanka
            </li>
          </ul>
        </div>

        {LINK_GROUPS.map((group) => (
          <nav key={group.title} aria-label={group.title}>
            <h3 className="text-sm font-semibold tracking-wide text-white uppercase">
              {group.title}
            </h3>
            <ul className="mt-4 space-y-3">
              {group.links.map((link) => (
                <li key={link.label}>
                  <FooterLink {...link} />
                </li>
              ))}
            </ul>
          </nav>
        ))}
      </div>

      {/* Bottom bar */}
      <div className="border-t border-white/10">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-6 text-sm text-brand-100/70 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
          <p>© {new Date().getFullYear()} SwiftDrop. All rights reserved.</p>
          <a
            href="#top"
            onClick={(e) => {
              e.preventDefault();
              window.scrollTo({ top: 0, behavior: 'smooth' });
            }}
            className="inline-flex items-center gap-1.5 self-start transition-colors hover:text-white sm:self-auto"
          >
            Back to top
            <ArrowUp className="size-4" aria-hidden="true" />
          </a>
        </div>
      </div>
    </footer>
  );
}
