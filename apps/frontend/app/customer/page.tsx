import type { Metadata } from "next";
import { CustomerDashboard } from "@/components/customer/CustomerDashboard";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { AuthGate } from "@/features/auth/AuthGate";

export const metadata: Metadata = { title: "My account" };

export default function CustomerAccountPage() {
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <AuthGate allowedRoles={["CUSTOMER"]}><CustomerDashboard /></AuthGate>
    </div>
  );
}
