import Link from "next/link";
import type { Route } from "next";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { APP_NAME } from "@/lib/constants";

type LegalSection = {
  title: string;
  body: string[];
};

type LegalDocumentProps = {
  title: string;
  description: string;
  sections: LegalSection[];
  children?: React.ReactNode;
};

export function LegalDocument({ title, description, sections, children }: LegalDocumentProps) {
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto w-full max-w-4xl px-4 py-10 sm:px-6 lg:py-14">
        <p className="text-sm font-semibold text-primary">{APP_NAME}</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-normal text-foreground sm:text-4xl">{title}</h1>
        <p className="mt-3 max-w-3xl text-base leading-7 text-muted-foreground">{description}</p>
        <p className="mt-4 text-sm text-muted-foreground">Last updated: 20 July 2026</p>

        <div className="mt-10 grid gap-8">
          {sections.map((section) => (
            <section className="border-t border-border pt-6" key={section.title}>
              <h2 className="text-xl font-semibold">{section.title}</h2>
              <div className="mt-3 grid gap-3 text-sm leading-7 text-muted-foreground sm:text-base">
                {section.body.map((paragraph) => (
                  <p key={paragraph}>{paragraph}</p>
                ))}
              </div>
            </section>
          ))}
          {children}
        </div>

        <div className="mt-10 flex flex-wrap gap-3 border-t border-border pt-6 text-sm">
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" href={"/terms" as Route}>Terms</Link>
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" href={"/privacy" as Route}>Privacy Policy</Link>
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" href="/">Back to halls</Link>
        </div>
      </main>
    </div>
  );
}
