import type { Metadata } from "next";
import type { Route } from "next";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { CustomerRequirementForm } from "@/components/requirements/CustomerRequirementForm";
import { AuthGate } from "@/features/auth/AuthGate";

export const metadata: Metadata = { title: "Post your requirement" };

export default function NewRequirementPage() {
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <AuthGate allowedRoles={["CUSTOMER"]} loginNextPath={"/requirements/new" as Route}>
        <CustomerRequirementForm />
      </AuthGate>
    </div>
  );
}
