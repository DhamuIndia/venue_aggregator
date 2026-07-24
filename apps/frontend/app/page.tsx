import { HallDiscovery } from "@/components/halls/HallDiscovery";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { MarketplaceRequirementCta } from "@/components/requirements/MarketplaceRequirementCta";

export default function HomePage() {
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <MarketplaceRequirementCta />
      <HallDiscovery />
    </div>
  );
}
