import { ArrowLeft, CheckCircle2 } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { VenueMartLogo } from "@/components/brand/VenueMartLogo";

type AuthShellProps = {
  title: string;
  description: string;
  children: ReactNode;
  homeLinkLabel?: string;
};

export function AuthShell({ title, description, children, homeLinkLabel }: AuthShellProps) {
  return (
    <main className="grid min-h-screen bg-white lg:grid-cols-[minmax(380px,0.85fr)_1.15fr]">
      <section className="relative hidden min-h-screen overflow-hidden lg:block">
        <Image
          alt="Decorated celebration venue"
          className="object-cover"
          fill
          priority
          sizes="45vw"
          src="https://images.unsplash.com/photo-1519167758481-83f550bb49b3?auto=format&fit=crop&w=1400&q=85"
        />
        <div className="absolute inset-0 bg-black/45" />
        <div className="absolute inset-0 flex flex-col justify-between p-10 text-white xl:p-14">
          <Link className="flex items-center gap-2 font-semibold" href="/">
            <VenueMartLogo inverted markClassName="size-10" />
          </Link>
          <div className="max-w-md">
            <h2 className="text-3xl font-semibold">Plan with confidence.</h2>
            <div className="mt-6 grid gap-3 text-sm text-white/90">
              <p className="flex items-center gap-2"><CheckCircle2 size={17} /> Save and compare venues</p>
              <p className="flex items-center gap-2"><CheckCircle2 size={17} /> Track every enquiry</p>
              <p className="flex items-center gap-2"><CheckCircle2 size={17} /> Review completed services</p>
            </div>
          </div>
        </div>
      </section>

      <section className="flex min-h-screen items-center justify-center px-5 py-10 sm:px-8">
        <div className="w-full max-w-md">
          <Link className="mb-10 flex items-center gap-2 font-semibold lg:hidden" href="/">
            <VenueMartLogo />
          </Link>
          {homeLinkLabel && (
            <Link className="mb-6 inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline" href="/">
              <ArrowLeft aria-hidden="true" size={17} /> {homeLinkLabel}
            </Link>
          )}
          <h1 className="text-3xl font-semibold text-foreground">{title}</h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">{description}</p>
          <div className="mt-8">{children}</div>
        </div>
      </section>
    </main>
  );
}
