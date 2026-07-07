import Image from "next/image";
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
  wordmarkClassName = "",
  showWordmark = true,
  inverted = false
}: VenueMartLogoProps) {
  const sizeClass = showWordmark ? "h-11 w-auto max-w-[205px] sm:h-12 sm:max-w-[240px]" : "h-11 w-auto max-w-[88px]";

  return (
    <span aria-label={APP_NAME} className={`inline-flex items-center ${className}`}>
      <Image
        alt={APP_NAME}
        className={`${sizeClass} object-contain ${wordmarkClassName} ${inverted ? "rounded-md bg-white/95 px-2 py-1 shadow-sm" : ""}`}
        height={100}
        priority
        src="/brand/venuemart-logo.png"
        width={300}
      />
    </span>
  );
}
