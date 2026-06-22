import type { Metadata } from "next";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { OwnerOnboarding } from "@/components/owner/OwnerOnboarding";
import { AuthGate } from "@/features/auth/AuthGate";

export const metadata: Metadata = { title: "List your venue" };

export default function OwnerOnboardingPage() {
  return <div className="min-h-screen bg-background"><SiteHeader /><AuthGate allowedRoles={["HALL_OWNER"]}><OwnerOnboarding /></AuthGate></div>;
}
