import { SiteHeader } from "@/components/layout/SiteHeader";
import { VendorOnboarding } from "@/components/vendors/VendorOnboarding";
import { AuthGate } from "@/features/auth/AuthGate";

export default function VendorOnboardingPage() {
  return <><SiteHeader /><AuthGate allowedRoles={["VENDOR"]}><VendorOnboarding /></AuthGate></>;
}
