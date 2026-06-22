"use client";

import { LoaderCircle } from "lucide-react";
import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "./AuthProvider";
import type { AuthRole } from "./types";

export function AuthGate({ children, allowedRoles }: { children: ReactNode; allowedRoles?: AuthRole[] }) {
  const { isLoading, user } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) return;
    if (!user) {
      router.replace("/auth/login");
      return;
    }
    if (allowedRoles && !allowedRoles.includes(user.role)) {
      const accountRoute: Route = user.role === "ADMIN" ? "/admin" : user.role === "VENDOR" ? "/vendor" : user.role === "HALL_OWNER" ? "/owner" : "/customer";
      router.replace(accountRoute);
    }
  }, [allowedRoles, isLoading, router, user]);

  if (isLoading || !user || (allowedRoles && !allowedRoles.includes(user.role))) {
    return (
      <div className="grid min-h-[60vh] place-items-center">
        <LoaderCircle aria-label="Checking your account" className="animate-spin text-primary" size={28} />
      </div>
    );
  }

  return children;
}
