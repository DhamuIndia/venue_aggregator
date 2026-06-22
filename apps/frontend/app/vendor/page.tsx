import { SiteHeader } from "@/components/layout/SiteHeader";
import { VendorDashboard } from "@/components/vendors/VendorDashboard";
import { AuthGate } from "@/features/auth/AuthGate";

export default function VendorPage() {
  return <><SiteHeader /><AuthGate allowedRoles={["VENDOR"]}><VendorDashboard /></AuthGate></>;
}
