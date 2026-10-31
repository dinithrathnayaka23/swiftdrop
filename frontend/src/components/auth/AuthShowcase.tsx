import { CircleCheck } from 'lucide-react';
import { Link } from 'react-router';
import { PATHS } from '../../routes/paths';
import Logo from '../brand/Logo';

export interface ShowcaseContent {
  eyebrow: string;
  title: string;
  description: string;
  features: string[];
}

/** Green brand panel shown beside the auth forms on large screens. */
export default function AuthShowcase({ eyebrow, title, description, features }: ShowcaseContent) {
  return (
    <aside className="relative hidden overflow-hidden bg-gradient-to-br from-brand-700 via-brand-600 to-brand-800 text-white lg:sticky lg:top-0 lg:flex lg:h-screen lg:flex-col lg:self-start">
      {/* texture + glows */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(rgba(255,255,255,0.12)_1px,transparent_1px)] [background-size:22px_22px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-32 -left-24 size-96 rounded-full bg-brand-400/30 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-24 -bottom-32 size-96 rounded-full bg-brand-900/50 blur-3xl"
      />

      <div className="relative flex h-full flex-col px-10 py-10 xl:px-16 xl:py-12">
        <Link
          to={PATHS.home}
          aria-label="SwiftDrop home"
          className="inline-flex self-start rounded-xl bg-white px-3.5 py-2 shadow-sm"
        >
          <Logo />
        </Link>

        <div className="my-auto max-w-lg py-10">
          <p className="text-sm font-semibold tracking-wide text-brand-100 uppercase">{eyebrow}</p>
          <h2 className="mt-3 text-3xl leading-tight font-bold tracking-tight xl:text-4xl">
            {title}
          </h2>
          <p className="mt-4 text-base leading-7 text-brand-50/85">{description}</p>
          <ul className="mt-8 space-y-3.5">
            {features.map((feature) => (
              <li key={feature} className="flex items-center gap-3 text-base text-brand-50">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-white/15">
                  <CircleCheck className="size-4 text-white" aria-hidden="true" />
                </span>
                {feature}
              </li>
            ))}
          </ul>
        </div>

        <p className="text-xs text-brand-100/70">
          © {new Date().getFullYear()} SwiftDrop · Fast, secure delivery across Sri Lanka
        </p>
      </div>
    </aside>
  );
}
