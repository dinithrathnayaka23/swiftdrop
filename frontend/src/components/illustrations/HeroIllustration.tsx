/** Delivery van in a city scene for the landing page hero. */
export default function HeroIllustration({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 560 420"
      className={className}
      role="img"
      aria-label="SwiftDrop delivery van on its way"
    >
      <defs>
        <clipPath id="hero-clip">
          <rect width="560" height="420" rx="28" />
        </clipPath>
        <linearGradient id="hero-sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#effaf3" />
          <stop offset="1" stopColor="#ffffff" />
        </linearGradient>
        <linearGradient id="hero-van" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#e6ebf0" />
        </linearGradient>
      </defs>

      <g clipPath="url(#hero-clip)">
        <rect width="560" height="420" fill="url(#hero-sky)" />

        {/* skyline */}
        <g fill="#dde6ee">
          <rect x="30" y="130" width="46" height="200" rx="4" />
          <rect x="84" y="86" width="38" height="244" rx="4" />
          <rect x="130" y="160" width="52" height="170" rx="4" />
          <rect x="320" y="108" width="42" height="222" rx="4" />
          <rect x="370" y="64" width="54" height="266" rx="4" />
          <rect x="432" y="136" width="46" height="194" rx="4" />
          <rect x="486" y="176" width="40" height="154" rx="4" />
        </g>
        <g fill="#eef3f7">
          <rect x="94" y="104" width="8" height="10" rx="1" />
          <rect x="106" y="104" width="8" height="10" rx="1" />
          <rect x="94" y="124" width="8" height="10" rx="1" />
          <rect x="106" y="124" width="8" height="10" rx="1" />
          <rect x="384" y="84" width="9" height="11" rx="1" />
          <rect x="400" y="84" width="9" height="11" rx="1" />
          <rect x="384" y="106" width="9" height="11" rx="1" />
          <rect x="400" y="106" width="9" height="11" rx="1" />
        </g>

        {/* trees */}
        <g>
          <rect x="62" y="300" width="6" height="30" fill="#8aa596" />
          <circle cx="65" cy="292" r="24" fill="#b1e4c4" />
          <rect x="497" y="296" width="6" height="34" fill="#8aa596" />
          <circle cx="500" cy="286" r="28" fill="#b1e4c4" />
        </g>

        {/* road */}
        <rect y="330" width="560" height="90" fill="#cfd8e1" />
        <path
          className="hero-road"
          d="M30 378H540"
          stroke="#ffffff"
          strokeWidth="5"
          strokeDasharray="28 20"
          strokeLinecap="round"
        />

        {/* route + destination pin */}
        <path
          className="hero-route"
          d="M120 236C190 140 300 230 420 128"
          fill="none"
          stroke="#26985a"
          strokeWidth="4"
          strokeDasharray="1 11"
          strokeLinecap="round"
        />
        <g transform="translate(420 44)">
          <ellipse cx="0" cy="96" rx="16" ry="5" fill="#13673b" opacity="0.2" />
          <g className="hero-pin">
            <path
              d="M0 0C-21 0-37 15-37 35c0 27 37 59 37 59s37-32 37-59C37 15 21 0 0 0Z"
              fill="#188048"
            />
            <circle cy="35" r="13" fill="#ffffff" />
          </g>
        </g>

        {/* van */}
        <ellipse cx="300" cy="356" rx="200" ry="13" fill="#64748b" opacity="0.25" />
        {/* body bobs on its suspension; the wheels stay on the road */}
        <g className="hero-van">
          <rect
            x="140"
            y="192"
            width="276"
            height="146"
            rx="14"
            fill="url(#hero-van)"
            stroke="#c4ced8"
            strokeWidth="2"
          />
          <path
            d="M416 214h52c14 0 24 8 31 21l24 47c4 8 6 15 6 23v19c0 8-6 14-14 14H416Z"
            fill="url(#hero-van)"
            stroke="#c4ced8"
            strokeWidth="2"
          />
          <path d="M430 228h36c8 0 14 4 18 12l19 40h-73Z" fill="#334155" />
          <path d="M436 234h14l-6 40h-8Z" fill="#475569" />
          <path d="M140 296h389v16H140Z" fill="#188048" />
          <path d="M140 312h389v6H140Z" fill="#13673b" />
          <text
            x="278"
            y="266"
            textAnchor="middle"
            fontFamily="Inter, sans-serif"
            fontSize="36"
            fontStyle="italic"
            fontWeight="800"
            fill="#188048"
          >
            SwiftDrop
          </text>
          <path d="M214 276h128" stroke="#4cb478" strokeWidth="3" strokeLinecap="round" />
          <rect x="514" y="286" width="13" height="9" rx="3" fill="#fde68a" />
          <rect x="430" y="290" width="16" height="4" rx="2" fill="#94a3b8" />
        </g>

        {/* wheels */}
        <g>
          <circle cx="214" cy="338" r="30" fill="#1e293b" />
          <circle cx="214" cy="338" r="13" fill="#cbd5e1" />
          <circle cx="462" cy="338" r="30" fill="#1e293b" />
          <circle cx="462" cy="338" r="13" fill="#cbd5e1" />
        </g>

        {/* speed lines */}
        <g className="hero-speed" stroke="#7fcf9f" strokeWidth="5" strokeLinecap="round">
          <path d="M70 230h48" />
          <path d="M50 256h70" />
          <path d="M80 282h40" />
        </g>
      </g>
    </svg>
  );
}
