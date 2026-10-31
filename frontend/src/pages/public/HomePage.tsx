import {
  ArrowRight,
  ChevronRight,
  Headphones,
  MapPin,
  Radar,
  ShieldCheck,
  Store,
  Truck,
  UserRound,
  type LucideIcon,
} from 'lucide-react';
import { useEffect } from 'react';
import { useLocation } from 'react-router';
import HeroIllustration from '../../components/illustrations/HeroIllustration';
import Reveal from '../../components/motion/Reveal';
import TrackingSearchForm from '../../components/tracking/TrackingSearchForm';
import ButtonLink from '../../components/ui/ButtonLink';
import { buttonClasses } from '../../components/ui/buttonStyles';
import { PATHS } from '../../routes/paths';

const FEATURES: { icon: LucideIcon; title: string; text: string }[] = [
  {
    icon: Truck,
    title: 'Fast & Reliable Deliveries',
    text: 'Same-day and next-day options island-wide.',
  },
  {
    icon: Radar,
    title: 'Real-time Tracking',
    text: 'Follow every parcel from pickup to doorstep.',
  },
  {
    icon: MapPin,
    title: 'Wide Coverage Across Sri Lanka',
    text: 'Delivery zones covering every district.',
  },
  {
    icon: ShieldCheck,
    title: 'Trusted by Businesses',
    text: 'Secure handling with proof of delivery.',
  },
];

const SUPPORT_EMAIL = 'support@swiftdrop.lk';

export default function HomePage() {
  const { hash } = useLocation();

  // Header links like "/#services" jump to the matching section.
  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [hash]);

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-b from-brand-50/70 via-white to-white">
        <div className="mx-auto grid max-w-7xl items-center gap-10 px-4 py-12 sm:px-6 sm:py-16 lg:grid-cols-2 lg:gap-12 lg:px-8 lg:py-20">
          <div>
            <h1 className="animate-fade-up text-4xl leading-[1.1] font-extrabold tracking-tight text-slate-900 sm:text-5xl lg:text-6xl">
              Reliable Deliveries
              <br />
              Across <span className="text-brand-600">Sri Lanka</span>
            </h1>
            <p
              className="mt-5 max-w-xl animate-fade-up text-base leading-7 text-slate-600 sm:text-lg"
              style={{ animationDelay: '120ms' }}
            >
              SwiftDrop connects businesses and people with fast, secure and reliable delivery
              services.
            </p>
            <div className="animate-fade-up" style={{ animationDelay: '240ms' }}>
              <TrackingSearchForm className="mt-8 max-w-xl" />
            </div>
          </div>
          <div className="animate-fade-in" style={{ animationDelay: '200ms' }}>
            <HeroIllustration className="mx-auto w-full max-w-xl drop-shadow-sm lg:max-w-none" />
          </div>
        </div>
      </section>

      {/* Services / features */}
      <section id="services" className="scroll-mt-20 border-y border-slate-100 bg-white">
        <div className="mx-auto grid max-w-7xl gap-6 px-4 py-10 sm:grid-cols-2 sm:px-6 lg:grid-cols-4 lg:px-8">
          {FEATURES.map(({ icon: Icon, title, text }, index) => (
            <Reveal key={title} delay={index * 90}>
              <div className="group flex items-start gap-4">
                <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-600 transition duration-300 group-hover:-translate-y-0.5 group-hover:bg-brand-600 group-hover:text-white group-hover:shadow-md group-hover:shadow-brand-600/20">
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <div>
                  <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
                  <p className="mt-1 text-sm text-slate-500">{text}</p>
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* Get started */}
      <section className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
        <div className="grid gap-5 md:grid-cols-2">
          <Reveal>
            <div className="group h-full rounded-2xl border border-slate-200 bg-white p-6 transition duration-300 hover:-translate-y-1 hover:border-brand-200 hover:shadow-xl hover:shadow-slate-200/70 sm:p-8">
              <span className="flex size-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                <UserRound className="size-5" aria-hidden="true" />
              </span>
              <h2 className="mt-5 text-xl font-bold text-slate-900">Send a parcel</h2>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Create a free account to book deliveries, get instant fee estimates and manage
                everything you send.
              </p>
              <ButtonLink to={PATHS.signup} className="mt-6">
                Create Account
                <ArrowRight
                  className="size-4 transition-transform group-hover:translate-x-1"
                  aria-hidden="true"
                />
              </ButtonLink>
            </div>
          </Reveal>
          <Reveal delay={120}>
            <div className="group h-full rounded-2xl bg-brand-700 p-6 text-white transition duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-brand-900/25 sm:p-8">
              <span className="flex size-11 items-center justify-center rounded-xl bg-white/15">
                <Store className="size-5" aria-hidden="true" />
              </span>
              <h2 className="mt-5 text-xl font-bold">For businesses</h2>
              <p className="mt-2 text-sm leading-6 text-brand-50/90">
                Connect your store through our order API, receive live status updates by webhook and
                ship at scale.
              </p>
              <ButtonLink to={PATHS.merchantSignup} variant="outline" className="mt-6 border-white">
                Register Your Business
                <ArrowRight
                  className="size-4 transition-transform group-hover:translate-x-1"
                  aria-hidden="true"
                />
              </ButtonLink>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Help */}
      <section id="help" className="mx-auto max-w-7xl scroll-mt-20 px-4 pb-16 sm:px-6 lg:px-8">
        <Reveal>
          <div className="flex flex-col gap-5 rounded-2xl bg-brand-50 p-6 sm:flex-row sm:items-center sm:p-8">
            <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-white text-brand-600 shadow-sm">
              <Headphones className="size-6" aria-hidden="true" />
            </span>
            <div className="flex-1">
              <h2 className="text-lg font-semibold text-slate-900">Need Help?</h2>
              <p className="mt-1 text-sm text-slate-600">
                We&apos;re here to assist you with tracking, deliveries or general enquiries.
              </p>
            </div>
            <a href={`mailto:${SUPPORT_EMAIL}`} className={buttonClasses({ variant: 'outline' })}>
              Contact Support
              <ChevronRight className="size-4" aria-hidden="true" />
            </a>
          </div>
        </Reveal>
      </section>
    </>
  );
}
