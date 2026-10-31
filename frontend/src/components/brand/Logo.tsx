import { cn } from '../../utils/cn';

const SIZES = {
  sm: { mark: 'h-6', text: 'text-lg' },
  md: { mark: 'h-8', text: 'text-xl' },
  lg: { mark: 'h-10', text: 'text-2xl' },
} as const;

interface LogoProps {
  size?: keyof typeof SIZES;
  withText?: boolean;
  className?: string;
}

/** SwiftDrop mark (moving parcel) + wordmark. */
export default function Logo({ size = 'md', withText = true, className }: LogoProps) {
  const s = SIZES[size];
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <svg viewBox="0 0 40 36" className={cn('w-auto shrink-0', s.mark)} aria-hidden="true">
        <path d="M2 12h8M0 18h8M3 24h6" stroke="#4cb478" strokeWidth="2.6" strokeLinecap="round" />
        <path d="M25 3 12 9.5v15L25 31l13-6.5v-15z" fill="#188048" />
        <path d="M25 3 12 9.5 25 16l13-6.5z" fill="#26985a" />
        <path d="M25 16v15l13-6.5v-15z" fill="#13673b" />
        <path
          d="m18.5 6.2 13 6.5v4.5"
          fill="none"
          stroke="#fff"
          strokeOpacity="0.75"
          strokeWidth="1.8"
        />
      </svg>
      {withText && (
        <span className={cn('font-bold tracking-tight text-brand-700', s.text)}>SwiftDrop</span>
      )}
    </span>
  );
}
