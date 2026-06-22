import type { Metadata } from "next";
import { EnquiryConfirmation } from "@/components/enquiries/EnquiryConfirmation";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { AuthGate } from "@/features/auth/AuthGate";

export const metadata: Metadata = { title: "Enquiry sent" };

export default async function EnquiryConfirmationPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto w-full max-w-7xl px-4 sm:px-6">
        <AuthGate><EnquiryConfirmation enquiryId={id} /></AuthGate>
      </main>
    </div>
  );
}
