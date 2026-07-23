import type { Route } from "next";
import type { AuthRole } from "./types";

export function routeForRole(role: AuthRole): Route {
  return role === "ADMIN" || role === "SUPER_ADMIN"
    ? "/admin"
    : role === "VENDOR"
      ? "/vendor"
      : role === "HALL_OWNER"
        ? "/owner"
        : "/customer";
}

export function safeNextRouteForRole(nextPath: string | null, role: AuthRole): Route | null {
  if (!nextPath?.startsWith("/") || nextPath.startsWith("//") || nextPath.includes("\\")) return null;

  if (role === "ADMIN" || role === "SUPER_ADMIN") {
    return nextPath === "/admin" || nextPath.startsWith("/admin?") ? nextPath as Route : null;
  }
  if (role === "VENDOR") {
    return nextPath === "/vendor" || nextPath.startsWith("/vendor/") ? nextPath as Route : null;
  }
  if (role === "HALL_OWNER") {
    return nextPath === "/owner" || nextPath.startsWith("/owner/") ? nextPath as Route : null;
  }

  const isCustomerPath = nextPath === "/customer" || nextPath.startsWith("/customer?");
  const isMarketplacePath = nextPath.startsWith("/halls") || nextPath.startsWith("/vendors");
  const isRequirementPath = nextPath === "/requirements/new";
  return isCustomerPath || isMarketplacePath || isRequirementPath ? nextPath as Route : null;
}
