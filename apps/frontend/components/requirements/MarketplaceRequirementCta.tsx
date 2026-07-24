"use client";

import { ArrowRight, ClipboardList, Sparkles } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { getRequirementOptions } from "@/features/requirements/requirements-client";

const requirementPath = "/requirements/new" as Route;
const signedOutRequirementPath = `/auth/login?next=${encodeURIComponent(requirementPath)}` as Route;

export function MarketplaceRequirementCta() {
  const { isLoading, user } = useAuth();
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    let current = true;
    getRequirementOptions()
      .then((options) => {
        if (current) setEnabled(options.enabled);
      })
      .catch(() => {
        if (current) setEnabled(false);
      });
    return () => {
      current = false;
    };
  }, []);

  if (!enabled || isLoading || (user && user.role !== "CUSTOMER")) return null;
  const href = user ? requirementPath : signedOutRequirementPath;

  return (
    <section className="border-b border-emerald-200 bg-emerald-50/70">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 px-4 py-6 sm:px-6 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start gap-4">
          <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-white text-emerald-700 shadow-sm"><ClipboardList size={22} /></span>
          <div>
            <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-emerald-800"><Sparkles size={14} /> One request, all your event services</p>
            <h2 className="mt-1 text-xl font-semibold">Looking for photography, catering, makeup, or decoration?</h2>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">Tell us what your event needs and keep every service requirement together.</p>
          </div>
        </div>
        <Link className="inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-md bg-primary px-5 text-sm font-semibold text-white hover:bg-primary/90" href={href}>
          Post your requirement <ArrowRight size={17} />
        </Link>
      </div>
    </section>
  );
}

export function MarketplaceRequirementNavLink({ mobile = false }: { mobile?: boolean }) {
  const { isLoading, user } = useAuth();
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    let current = true;
    getRequirementOptions()
      .then((options) => {
        if (current) setEnabled(options.enabled);
      })
      .catch(() => {
        if (current) setEnabled(false);
      });
    return () => {
      current = false;
    };
  }, []);

  if (!enabled || isLoading || (user && user.role !== "CUSTOMER")) return null;
  const href = user ? requirementPath : signedOutRequirementPath;
  return <Link className={mobile ? "rounded-md px-3 py-2 text-primary hover:bg-muted" : "font-medium text-primary hover:text-emerald-800"} href={href}>Post requirement</Link>;
}
