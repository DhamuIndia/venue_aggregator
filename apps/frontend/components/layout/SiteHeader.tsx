"use client";

import { Building2, Heart, LayoutDashboard, ShieldCheck, Store, UserRound } from "lucide-react";
import Link from "next/link";
import { useAuth } from "@/features/auth/AuthProvider";
import { NotificationBell } from "@/components/notifications/NotificationCenter";

export function SiteHeader() {
  const { isLoading, user } = useAuth();
  const accountHref = user?.role === "ADMIN" ? "/admin" : user?.role === "VENDOR" ? "/vendor" : user?.role === "HALL_OWNER" ? "/owner" : "/customer";
  const workspaceHref = user?.role === "ADMIN" ? "/admin" : user?.role === "VENDOR" ? "/vendor" : user?.role === "HALL_OWNER" ? "/owner" : "/auth/login?next=/owner/onboarding";
  const workspaceLabel = user?.role === "ADMIN" ? "Admin dashboard" : user?.role === "VENDOR" ? "Vendor workspace" : user?.role === "HALL_OWNER" ? "Owner dashboard" : "List your venue";

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center gap-6 px-4 sm:px-6">
        <Link className="flex items-center gap-2 font-semibold text-foreground" href="/">
          <span className="grid size-9 place-items-center rounded-md bg-primary text-white"><Building2 aria-hidden="true" size={20} /></span>
          <span>Venue Aggregator</span>
        </Link>
        <nav className="hidden items-center gap-6 text-sm text-muted-foreground md:flex">
          <Link className="hover:text-foreground" href="/">Halls</Link>
          <Link className="hover:text-foreground" href="/vendors">Vendors</Link>
          <Link className="hover:text-foreground" href="/">How it works</Link>
        </nav>
        <div className="ml-auto flex items-center gap-1 sm:gap-2">
          {(!user || user.role === "CUSTOMER") && <Link aria-label="Saved venues" className="grid size-10 place-items-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground" href={user ? "/customer" : "/auth/login"} title="Saved venues"><Heart aria-hidden="true" size={19} /></Link>}
          {user && <NotificationBell />}
          {!isLoading && user ? (
            <Link aria-label="My account" className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-3 text-sm font-medium hover:bg-muted" href={accountHref}><span className="grid size-6 place-items-center rounded-full bg-emerald-50 text-xs font-semibold text-emerald-800">{user.fullName.charAt(0)}</span><span className="hidden sm:inline">My account</span></Link>
          ) : (
            <Link aria-label="Log in" className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-3 text-sm font-medium hover:bg-muted" href="/auth/login"><UserRound aria-hidden="true" size={18} /><span className="hidden sm:inline">Log in</span></Link>
          )}
          <Link className="hidden h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-medium text-white hover:bg-black sm:inline-flex" href={workspaceHref}>{user?.role === "ADMIN" ? <ShieldCheck size={17} /> : user?.role === "VENDOR" ? <Store size={17} /> : user?.role === "HALL_OWNER" ? <LayoutDashboard size={17} /> : null}{workspaceLabel}</Link>
        </div>
      </div>
    </header>
  );
}
