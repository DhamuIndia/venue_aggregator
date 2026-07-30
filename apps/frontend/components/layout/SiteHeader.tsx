"use client";

import { Heart, LayoutDashboard, LogOut, Menu, ShieldCheck, Store, UserRound, X } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { NotificationBell } from "@/components/notifications/NotificationCenter";
import { VenueMartLogo } from "@/components/brand/VenueMartLogo";
import { MarketplaceRequirementNavLink } from "@/components/requirements/MarketplaceRequirementCta";

export function SiteHeader() {
  const { isLoading, logout, user } = useAuth();
  const router = useRouter();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const accountHref = user?.role === "ADMIN" ? "/admin" : user?.role === "VENDOR" ? "/vendor" : user?.role === "HALL_OWNER" ? "/owner" : "/customer";
  const workspaceHref = user?.role === "ADMIN" ? "/admin" : user?.role === "VENDOR" ? "/vendor" : user?.role === "HALL_OWNER" ? "/owner" : "/auth/login?next=/owner/onboarding";
  const workspaceLabel = user?.role === "ADMIN" ? "Admin dashboard" : user?.role === "VENDOR" ? "Vendor workspace" : user?.role === "HALL_OWNER" ? "Owner dashboard" : "List your venue";
  const navLinks = [
    { href: "/", label: "Halls" },
    { href: "/vendors", label: "Vendors" },
    { href: "/#how-it-works", label: "How it works" }
  ] as const;

  function signOut() {
    logout();
    router.replace("/auth/login");
    setIsMenuOpen(false);
  }

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center gap-6 px-4 sm:px-6">
        <Link className="flex items-center gap-2 font-semibold text-foreground" href="/" onClick={() => setIsMenuOpen(false)}>
          <VenueMartLogo />
        </Link>
        <nav className="hidden items-center gap-6 text-sm text-muted-foreground md:flex">
          {navLinks.map((link) => <Link className="hover:text-foreground" href={link.href} key={link.href}>{link.label}</Link>)}
          <MarketplaceRequirementNavLink />
        </nav>
        <div className="ml-auto flex items-center gap-1 sm:gap-2">
          {(!user || user.role === "CUSTOMER") && <Link aria-label="Saved venues" className="grid size-10 place-items-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground" href={user ? "/customer?tab=saved" : "/auth/login"} title="Saved venues"><Heart aria-hidden="true" size={19} /></Link>}
          {user && <NotificationBell />}
          {!isLoading && user ? (
            <>
              <Link aria-label="My account" className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-3 text-sm font-medium hover:bg-muted" href={accountHref}><span className="grid size-6 place-items-center rounded-full bg-emerald-50 text-xs font-semibold text-emerald-800">{user.fullName.charAt(0)}</span><span className="hidden sm:inline">My account</span></Link>
              <button aria-label="Sign out" className="grid size-10 place-items-center rounded-md border border-border text-muted-foreground hover:bg-muted hover:text-foreground" onClick={signOut} title="Sign out" type="button"><LogOut aria-hidden="true" size={18} /></button>
            </>
          ) : (
            <Link aria-label="Log in" className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-3 text-sm font-medium hover:bg-muted" href="/auth/login"><UserRound aria-hidden="true" size={18} /><span className="hidden sm:inline">Log in</span></Link>
          )}
          <Link className="hidden h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-medium text-white hover:bg-black sm:inline-flex" href={workspaceHref}>{user?.role === "ADMIN" ? <ShieldCheck size={17} /> : user?.role === "VENDOR" ? <Store size={17} /> : user?.role === "HALL_OWNER" ? <LayoutDashboard size={17} /> : null}{workspaceLabel}</Link>
          <button aria-expanded={isMenuOpen} aria-label={isMenuOpen ? "Close menu" : "Open menu"} className="grid size-10 place-items-center rounded-md border border-border text-muted-foreground hover:bg-muted hover:text-foreground md:hidden" onClick={() => setIsMenuOpen((current) => !current)} type="button">
            {isMenuOpen ? <X aria-hidden="true" size={18} /> : <Menu aria-hidden="true" size={18} />}
          </button>
        </div>
      </div>
      {isMenuOpen && (
        <div className="border-t border-border bg-white md:hidden">
          <nav className="mx-auto grid w-full max-w-7xl gap-1 px-4 py-3 text-sm font-medium sm:px-6">
            {navLinks.map((link) => (
              <Link className="rounded-md px-3 py-2 text-muted-foreground hover:bg-muted hover:text-foreground" href={link.href} key={link.href} onClick={() => setIsMenuOpen(false)}>
                {link.label}
              </Link>
            ))}
            <MarketplaceRequirementNavLink mobile />
            <Link className="mt-1 inline-flex h-10 items-center justify-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" href={workspaceHref} onClick={() => setIsMenuOpen(false)}>
              {user?.role === "ADMIN" ? <ShieldCheck size={17} /> : user?.role === "VENDOR" ? <Store size={17} /> : user?.role === "HALL_OWNER" ? <LayoutDashboard size={17} /> : null}
              {workspaceLabel}
            </Link>
          </nav>
        </div>
      )}
    </header>
  );
}
