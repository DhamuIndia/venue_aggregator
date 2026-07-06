import { APP_NAME } from "@/lib/constants";

type VenueMartLogoProps = {
  className?: string;
  markClassName?: string;
  wordmarkClassName?: string;
  showWordmark?: boolean;
  inverted?: boolean;
};

export function VenueMartLogo({
  className = "",
  markClassName = "size-9",
  wordmarkClassName = "",
  showWordmark = true,
  inverted = false
}: VenueMartLogoProps) {
  return (
    <span aria-label={APP_NAME} className={`inline-flex items-center gap-2.5 ${className}`}>
      <svg
        aria-hidden="true"
        className={markClassName}
        fill="none"
        viewBox="0 0 64 64"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M32 3.75c-13.8 0-25 10.35-25 23.1 0 15.9 20.95 32.35 23.95 34.6a1.75 1.75 0 0 0 2.1 0C36.05 59.2 57 42.75 57 26.85c0-12.75-11.2-23.1-25-23.1Z"
          fill={inverted ? "white" : "hsl(var(--primary))"}
        />
        <path
          d="M32 9.15c-10.9 0-19.75 8.1-19.75 18.1 0 9.05 7.6 19.3 14.05 26.05a1.75 1.75 0 0 0 2.8-.42l2.45-4.85c-5.75-6.05-11.8-14.15-11.8-20.8 0-6.2 5.5-11.25 12.25-11.25s12.25 5.05 12.25 11.25c0 1.35-.25 2.75-.72 4.18l5.48-1.25c.47-1.5.74-3 .74-4.5 0-10-8.85-18.1-19.75-18.1Z"
          fill={inverted ? "hsl(var(--primary))" : "white"}
          opacity={inverted ? "0.92" : "1"}
        />
        <path
          d="M19.5 33.75h25"
          stroke={inverted ? "hsl(var(--primary))" : "white"}
          strokeLinecap="round"
          strokeWidth="3.3"
        />
        <path
          d="M20.75 35.25V25.2L32 18.4l11.25 6.8v10.05"
          stroke={inverted ? "hsl(var(--primary))" : "white"}
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="3.3"
        />
        <path
          d="M26.25 24.75h11.5M26.25 35.25v-7h-5M37.75 35.25v-7h5"
          stroke={inverted ? "hsl(var(--primary))" : "white"}
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="3.3"
        />
        <path
          d="M28.2 35.25v-6.1c0-2.1 1.7-3.8 3.8-3.8s3.8 1.7 3.8 3.8v6.1"
          stroke={inverted ? "hsl(var(--primary))" : "white"}
          strokeLinecap="round"
          strokeWidth="3.3"
        />
        <path
          d="M33.65 43.8 41.2 51.15 55.1 36.25"
          stroke="#f6c451"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="5.2"
        />
      </svg>
      {showWordmark && (
        <span className={`font-semibold tracking-normal ${wordmarkClassName}`}>
          <span className={inverted ? "text-white" : "text-foreground"}>Venue</span>
          <span className={inverted ? "text-white" : "text-primary"}>Mart</span>
        </span>
      )}
    </span>
  );
}
