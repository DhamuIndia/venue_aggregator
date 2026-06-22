import { AdminDashboard } from "@/components/admin/AdminDashboard";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { AuthGate } from "@/features/auth/AuthGate";

export default function AdminPage() {
  return (
    <>
      <SiteHeader />
      <AuthGate allowedRoles={["ADMIN"]}>
        <AdminDashboard />
      </AuthGate>
    </>
  );
}
