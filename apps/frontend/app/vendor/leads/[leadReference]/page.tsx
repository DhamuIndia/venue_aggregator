import type { Route } from "next";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { VendorLeadDirectView } from "@/components/vendors/VendorLeadDirectView";
import { AuthGate } from "@/features/auth/AuthGate";

type VendorLeadPageProps = {
  params: Promise<{ leadReference: string }>;
};

export default async function VendorLeadPage({ params }: VendorLeadPageProps) {
  const { leadReference } = await params;
  const directLeadPath = `/vendor/leads/${encodeURIComponent(leadReference)}` as Route;

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <AuthGate allowedRoles={["VENDOR"]} loginNextPath={directLeadPath}>
        <VendorLeadDirectView leadReference={leadReference} />
      </AuthGate>
    </div>
  );
}
