import { HallDiscovery } from "@/components/halls/HallDiscovery";
import { SiteHeader } from "@/components/layout/SiteHeader";

export default function HomePage() {
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <HallDiscovery />
    </div>
  );
}
