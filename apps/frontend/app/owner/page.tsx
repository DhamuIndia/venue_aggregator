import type { Metadata } from "next";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { OwnerDashboard } from "@/components/owner/OwnerDashboard";
import { AuthGate } from "@/features/auth/AuthGate";

export const metadata: Metadata = { title: "Owner dashboard" };

export default function OwnerDashboardPage() {
  return <div className="min-h-screen bg-background"><SiteHeader /><AuthGate allowedRoles={["HALL_OWNER"]}><OwnerDashboard /></AuthGate></div>;
}
