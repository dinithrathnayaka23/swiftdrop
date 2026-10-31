/** Parcel box on a route between two pins, for the tracking search page. */
export default function TrackParcelIllustration({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 320 210" className={className} role="img" aria-label="Parcel on its route">
      <path
        d="M44 166C92 206 140 136 182 160S262 176 286 120"
        fill="none"
        stroke="#26985a"
        strokeWidth="3.5"
        strokeLinecap="round"
      />

      {/* start pin */}
      <g transform="translate(44 102)">
        <ellipse cy="64" rx="12" ry="4" fill="#13673b" opacity="0.2" />
        <path
          d="M0 0C-14 0-25 10-25 24c0 18 25 40 25 40s25-22 25-40C25 10 14 0 0 0Z"
          fill="#188048"
        />
        <circle cy="24" r="9" fill="#ffffff" />
      </g>

      {/* parcel box */}
      <g transform="translate(118 22)">
        <ellipse cx="66" cy="138" rx="62" ry="9" fill="#64748b" opacity="0.18" />
        <path d="M66 0 128 26 66 52 4 26Z" fill="#ecc896" />
        <path d="M4 26 66 52v76L4 102Z" fill="#d9a86a" />
        <path d="M66 52 128 26v76l-62 26Z" fill="#c4914f" />
        <path d="M35 13 97 39v18l-14 6V45L21 19Z" fill="#f5dfbd" />
        <path d="M18 70l14 6v14l-14-6Z" fill="#ffffff" opacity="0.6" />
      </g>

      {/* destination pin on the box */}
      <g transform="translate(230 54)">
        <ellipse cy="60" rx="11" ry="4" fill="#13673b" opacity="0.25" />
        <path
          d="M0 0C-13 0-24 10-24 23c0 17 24 37 24 37s24-20 24-37C24 10 13 0 0 0Z"
          fill="#26985a"
        />
        <circle cy="23" r="8.5" fill="#ffffff" />
      </g>
    </svg>
  );
}
