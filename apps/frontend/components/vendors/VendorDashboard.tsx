"use client";

import {
  ArrowUpRight,
  BadgeCheck,
  BriefcaseBusiness,
  CalendarDays,
  Check,
  ChevronRight,
  Eye,
  ImagePlus,
  IndianRupee,
  Layers3,
  LoaderCircle,
  MapPin,
  MessageSquareText,
  Pencil,
  Plus,
  Send,
  Sparkles,
  Store,
  Trash2,
  X
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { fallbackVendorAnalytics, getVendorAnalytics, type VendorAnalytics } from "@/features/analytics/analytics-client";
import { useAuth } from "@/features/auth/AuthProvider";
import { getVendorLeads, updateVendorLeadStatus } from "@/features/vendors/lead-client";
import { deleteVendorMedia, getVendorMedia, mediaFromVendor, setVendorMediaCover, type VendorMediaItem, uploadAndCreateVendorMedia } from "@/features/vendors/media-client";
import { createVendorPackage, deleteVendorPackage, getVendorPackages, updateVendorPackage, type VendorPackagePayload } from "@/features/vendors/package-client";
import { createSubscriptionOrder, fallbackSubscriptionPlans, fallbackVendorSubscription, getSubscriptionPlans, getVendorSubscription, type SubscriptionPlan, type VendorSubscription } from "@/features/vendors/subscription-client";
import type { VendorLead, VendorLeadStatus, VendorPackage } from "@/features/vendors/types";
import { fallbackVendorLeads, workspaceVendor } from "@/features/vendors/workspace-data";

type VendorTab = "overview" | "leads" | "reports" | "services" | "portfolio" | "subscription";

const tabs: { id: VendorTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "leads", label: "Leads" },
  { id: "reports", label: "Reports" },
  { id: "services", label: "Services" },
  { id: "portfolio", label: "Portfolio" },
  { id: "subscription", label: "Subscription" }
];

const statusStyle: Record<VendorLeadStatus, string> = {
  NEW: "bg-blue-50 text-blue-700",
  CONTACTED: "bg-amber-50 text-amber-800",
  QUOTE_SENT: "bg-violet-50 text-violet-700",
  BOOKED: "bg-emerald-50 text-emerald-800",
  DECLINED: "bg-rose-50 text-rose-700"
};

function readableStatus(status: string) {
  return status.toLowerCase().replaceAll("_", " ");
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

function formatCompactMoney(value: number) {
  return `INR ${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 1, notation: "compact" }).format(value)}`;
}

function billingLabel(cycle: SubscriptionPlan["billingCycle"]) {
  return cycle === "YEARLY" ? "year" : "month";
}

function subscriptionStatusLabel(status: VendorSubscription["status"]) {
  return status.toLowerCase().replaceAll("_", " ");
}

const emptyPackageForm = { name: "", description: "", price: "", includes: "" };

function packagePayload(form: typeof emptyPackageForm): VendorPackagePayload {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    price: Number(form.price) || 0,
    includes: form.includes.split(",").map((item) => item.trim()).filter(Boolean)
  };
}

export function VendorDashboard() {
  const { accessToken } = useAuth();
  const [activeTab, setActiveTab] = useState<VendorTab>("overview");
  const [leads, setLeads] = useState<VendorLead[]>(fallbackVendorLeads);
  const [isLoadingLeads, setIsLoadingLeads] = useState(true);
  const [leadsError, setLeadsError] = useState("");
  const [leadFilter, setLeadFilter] = useState<"ALL" | VendorLeadStatus>("ALL");
  const [notice, setNotice] = useState("");
  const [portfolio, setPortfolio] = useState<VendorMediaItem[]>(mediaFromVendor(workspaceVendor));
  const [isLoadingPortfolio, setIsLoadingPortfolio] = useState(true);
  const [portfolioError, setPortfolioError] = useState("");
  const [isUploadingPortfolio, setIsUploadingPortfolio] = useState(false);
  const [updatingMediaId, setUpdatingMediaId] = useState<string | null>(null);
  const [subscriptionPlans, setSubscriptionPlans] = useState<SubscriptionPlan[]>(fallbackSubscriptionPlans);
  const [subscription, setSubscription] = useState<VendorSubscription>(fallbackVendorSubscription);
  const [isLoadingSubscription, setIsLoadingSubscription] = useState(true);
  const [subscriptionError, setSubscriptionError] = useState("");
  const [checkoutPlanId, setCheckoutPlanId] = useState<string | null>(null);
  const [packages, setPackages] = useState<VendorPackage[]>(workspaceVendor.packages);
  const [isLoadingPackages, setIsLoadingPackages] = useState(true);
  const [packagesError, setPackagesError] = useState("");
  const [isPackageEditorOpen, setIsPackageEditorOpen] = useState(false);
  const [editingPackageId, setEditingPackageId] = useState<string | null>(null);
  const [packageForm, setPackageForm] = useState(emptyPackageForm);
  const [savingPackageId, setSavingPackageId] = useState<string | null>(null);
  const [deletingPackageId, setDeletingPackageId] = useState<string | null>(null);
  const [analytics, setAnalytics] = useState<VendorAnalytics>(fallbackVendorAnalytics);
  const [isLoadingAnalytics, setIsLoadingAnalytics] = useState(true);
  const [analyticsError, setAnalyticsError] = useState("");

  useEffect(() => {
    let isCurrent = true;

    async function loadAnalytics() {
      setIsLoadingAnalytics(true);
      setAnalyticsError("");

      try {
        const response = await getVendorAnalytics(workspaceVendor.id, accessToken);
        if (!isCurrent) return;
        setAnalytics(response);
      } catch {
        if (!isCurrent) return;
        setAnalytics(fallbackVendorAnalytics);
        setAnalyticsError("Could not load latest reports.");
      } finally {
        if (isCurrent) setIsLoadingAnalytics(false);
      }
    }

    loadAnalytics();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    async function loadLeads() {
      setIsLoadingLeads(true);
      setLeadsError("");

      try {
        const response = await getVendorLeads(workspaceVendor.id, accessToken);
        if (!isCurrent) return;
        setLeads(response.source === "api" ? response.leads : [...response.leads, ...fallbackVendorLeads]);
      } catch {
        if (!isCurrent) return;
        setLeads(fallbackVendorLeads);
        setLeadsError("Could not load latest leads.");
      } finally {
        if (isCurrent) setIsLoadingLeads(false);
      }
    }

    loadLeads();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    async function loadPortfolio() {
      setIsLoadingPortfolio(true);
      setPortfolioError("");

      try {
        const media = await getVendorMedia(accessToken, mediaFromVendor(workspaceVendor));
        if (!isCurrent) return;
        setPortfolio(media);
      } catch {
        if (!isCurrent) return;
        setPortfolio(mediaFromVendor(workspaceVendor));
        setPortfolioError("Could not load portfolio photos.");
      } finally {
        if (isCurrent) setIsLoadingPortfolio(false);
      }
    }

    loadPortfolio();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    async function loadPackages() {
      setIsLoadingPackages(true);
      setPackagesError("");

      try {
        const response = await getVendorPackages(accessToken);
        if (!isCurrent) return;
        setPackages(response.packages);
      } catch {
        if (!isCurrent) return;
        setPackages(workspaceVendor.packages);
        setPackagesError("Could not load packages.");
      } finally {
        if (isCurrent) setIsLoadingPackages(false);
      }
    }

    loadPackages();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    async function loadSubscription() {
      setIsLoadingSubscription(true);
      setSubscriptionError("");

      try {
        const [plans, currentSubscription] = await Promise.all([
          getSubscriptionPlans(),
          getVendorSubscription(accessToken)
        ]);
        if (!isCurrent) return;
        setSubscriptionPlans(plans);
        setSubscription(currentSubscription);
      } catch {
        if (!isCurrent) return;
        setSubscriptionPlans(fallbackSubscriptionPlans);
        setSubscription(fallbackVendorSubscription);
        setSubscriptionError("Could not load subscription details.");
      } finally {
        if (isCurrent) setIsLoadingSubscription(false);
      }
    }

    loadSubscription();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  const newCount = leads.filter((lead) => lead.status === "NEW").length;
  const bookedCount = leads.filter((lead) => lead.status === "BOOKED").length;
  const bookedValue = leads.filter((lead) => lead.status === "BOOKED").reduce((total, lead) => total + lead.budget, 0);
  const filteredLeads = useMemo(() => leadFilter === "ALL" ? leads : leads.filter((lead) => lead.status === leadFilter), [leadFilter, leads]);

  async function updateLead(id: string, status: VendorLeadStatus) {
    try {
      const updatedLead = await updateVendorLeadStatus(id, status, accessToken);
      setLeads((current) => current.map((lead) => lead.id === id ? { ...lead, ...updatedLead, status } : lead));
      setNotice(`Lead ${id} updated to ${readableStatus(status)}.`);
    } catch (exception) {
      setNotice(exception instanceof Error ? exception.message : "Could not update lead status.");
    }
  }

  async function addPortfolioImages(files: FileList | null) {
    if (!files?.length) return;
    try {
      setPortfolioError("");
      setIsUploadingPortfolio(true);
      const currentLength = portfolio.length;
      const uploaded = await Promise.all(Array.from(files).map((file, index) => uploadAndCreateVendorMedia(file, currentLength + index, accessToken)));
      setPortfolio((current) => [...uploaded, ...current]);
      setNotice(`${files.length} portfolio image${files.length === 1 ? "" : "s"} added.`);
    } catch (exception) {
      setPortfolioError(exception instanceof Error ? exception.message : "Could not upload portfolio photos.");
    } finally {
      setIsUploadingPortfolio(false);
    }
  }

  async function makePortfolioCover(mediaId: string) {
    try {
      setUpdatingMediaId(mediaId);
      await setVendorMediaCover(mediaId, accessToken);
      setPortfolio((current) => current.map((item) => ({ ...item, isCover: item.id === mediaId })));
      setNotice("Cover photo updated.");
    } catch (exception) {
      setPortfolioError(exception instanceof Error ? exception.message : "Could not update cover photo.");
    } finally {
      setUpdatingMediaId(null);
    }
  }

  async function removePortfolioImage(mediaId: string) {
    if (!window.confirm("Delete this portfolio photo?")) return;
    try {
      setUpdatingMediaId(mediaId);
      await deleteVendorMedia(mediaId, accessToken);
      setPortfolio((current) => current.filter((item) => item.id !== mediaId));
      setNotice("Portfolio photo deleted.");
    } catch (exception) {
      setPortfolioError(exception instanceof Error ? exception.message : "Could not delete portfolio photo.");
    } finally {
      setUpdatingMediaId(null);
    }
  }

  function openPackageEditor(packageItem?: VendorPackage) {
    setPackagesError("");
    setEditingPackageId(packageItem?.id ?? null);
    setPackageForm(packageItem ? {
      name: packageItem.name,
      description: packageItem.description,
      price: String(packageItem.price),
      includes: packageItem.includes.join(", ")
    } : emptyPackageForm);
    setIsPackageEditorOpen(true);
  }

  function closePackageEditor() {
    setIsPackageEditorOpen(false);
    setEditingPackageId(null);
    setPackageForm(emptyPackageForm);
    setPackagesError("");
  }

  async function savePackage() {
    const payload = packagePayload(packageForm);
    if (!payload.name || payload.price < 1) {
      setPackagesError("Add a package name and valid price.");
      return;
    }

    try {
      setSavingPackageId(editingPackageId ?? "new");
      const savedPackage = editingPackageId
        ? await updateVendorPackage(editingPackageId, payload, accessToken)
        : await createVendorPackage(payload, accessToken);
      setPackages((current) => editingPackageId ? current.map((item) => item.id === editingPackageId ? savedPackage : item) : [savedPackage, ...current]);
      setNotice(editingPackageId ? "Package updated." : "Package added.");
      closePackageEditor();
    } catch (exception) {
      setPackagesError(exception instanceof Error ? exception.message : "Could not save package.");
    } finally {
      setSavingPackageId(null);
    }
  }

  async function removePackage(packageId: string) {
    if (!window.confirm("Delete this package?")) return;
    try {
      setDeletingPackageId(packageId);
      await deleteVendorPackage(packageId, accessToken);
      setPackages((current) => current.filter((item) => item.id !== packageId));
      setNotice("Package deleted.");
    } catch (exception) {
      setPackagesError(exception instanceof Error ? exception.message : "Could not delete package.");
    } finally {
      setDeletingPackageId(null);
    }
  }

  async function chooseSubscription(planId: string) {
    try {
      setSubscriptionError("");
      setCheckoutPlanId(planId);
      const order = await createSubscriptionOrder(planId, accessToken);
      setSubscription((current) => ({
        ...current,
        planId,
        status: order.status === "ACTIVE" ? "ACTIVE" : "PENDING_PAYMENT",
        pendingOrderId: order.orderId
      }));
      setNotice(`Payment order ${order.orderId} created.`);
      if (order.checkoutUrl) window.location.assign(order.checkoutUrl);
    } catch (exception) {
      setSubscriptionError(exception instanceof Error ? exception.message : "Could not create payment order.");
    } finally {
      setCheckoutPlanId(null);
    }
  }

  const tabBadge: Partial<Record<VendorTab, number>> = { leads: newCount };

  return (
    <main className="min-h-[calc(100vh-4rem)] bg-[#f7f8fa]">
      <div className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6 sm:py-9">
        <div className="flex flex-wrap items-start justify-between gap-5">
          <div><p className="inline-flex items-center gap-2 text-sm font-semibold text-primary"><Store size={17} /> Vendor workspace</p><h1 className="mt-2 text-3xl font-semibold">{workspaceVendor.businessName}</h1><p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground"><MapPin size={15} /> {workspaceVendor.area}, {workspaceVendor.city}</p></div>
          <div className="flex flex-wrap gap-2"><Link className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-4 text-sm font-medium" href={`/vendors/${workspaceVendor.id}`}><Eye size={17} /> Public profile</Link><Link className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" href="/vendor/onboarding"><Plus size={17} /> Edit business</Link></div>
        </div>

        {notice && <div className="mt-6 flex items-center gap-3 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800" role="status"><BadgeCheck size={18} /><span className="flex-1">{notice}</span><button aria-label="Dismiss notification" className="grid size-8 place-items-center rounded-md hover:bg-emerald-100" onClick={() => setNotice("")}><X size={16} /></button></div>}

        <div className="mt-7 overflow-x-auto border-b border-border"><div aria-label="Vendor dashboard" className="flex min-w-max gap-7" role="tablist">{tabs.map((tab) => <button aria-selected={activeTab === tab.id} className={`flex h-12 items-center gap-2 border-b-2 px-1 text-sm font-medium ${activeTab === tab.id ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} role="tab">{tab.label}{tabBadge[tab.id] ? <span className="grid min-w-5 place-items-center rounded-full bg-blue-50 px-1.5 py-0.5 text-xs text-blue-700">{tabBadge[tab.id]}</span> : null}</button>)}</div></div>

        {activeTab === "overview" && <section className="py-7"><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{[
          { label: "New leads", value: newCount, icon: MessageSquareText, color: "text-blue-700", tab: "leads" as const },
          { label: "Booked events", value: bookedCount, icon: CalendarDays, color: "text-emerald-700", tab: "leads" as const },
          { label: "Profile views", value: "1,926", icon: Eye, color: "text-violet-700", tab: "portfolio" as const },
          { label: "Booked value", value: `INR ${formatMoney(bookedValue)}`, icon: IndianRupee, color: "text-amber-700", tab: "leads" as const }
        ].map((stat) => <button className="rounded-lg border border-border bg-white p-5 text-left hover:border-primary" key={stat.label} onClick={() => setActiveTab(stat.tab)}><stat.icon className={stat.color} size={21} /><p className="mt-5 text-2xl font-semibold">{stat.value}</p><p className="mt-1 text-sm text-muted-foreground">{stat.label}</p></button>)}</div><div className="mt-9 grid gap-8 lg:grid-cols-[1.45fr_1fr]"><section><div className="flex items-center justify-between"><div><h2 className="text-xl font-semibold">Recent leads</h2><p className="mt-1 text-sm text-muted-foreground">Respond quickly to improve conversion.</p></div><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("leads")}>View all</button></div><div className="mt-4 grid gap-3">{leads.slice(0, 3).map((lead) => <button className="flex w-full items-center gap-4 rounded-lg border border-border bg-white p-4 text-left hover:border-primary" key={lead.id} onClick={() => setActiveTab("leads")}><span className="grid size-11 shrink-0 place-items-center rounded-md bg-blue-50 text-blue-700"><BriefcaseBusiness size={20} /></span><span className="min-w-0 flex-1"><strong className="block truncate">{lead.eventType} | {lead.service}</strong><span className="mt-1 block text-sm text-muted-foreground">{lead.eventDate} | {lead.location}</span></span><span className={`hidden rounded-full px-2.5 py-1 text-xs font-medium sm:block ${statusStyle[lead.status]}`}>{readableStatus(lead.status)}</span><ChevronRight size={18} /></button>)}</div></section><section><h2 className="text-xl font-semibold">Profile strength</h2><div className="mt-4 rounded-lg border border-border bg-white p-5"><div className="flex items-center justify-between"><span className="inline-flex items-center gap-2 font-semibold text-emerald-700"><BadgeCheck size={18} /> Approved</span><span className="text-sm font-semibold">88%</span></div><div className="mt-4 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full w-[88%] bg-primary" /></div><div className="mt-5 grid gap-3 text-sm"><p className="flex items-center gap-2"><Check className="text-emerald-700" size={16} /> Business and services complete</p><p className="flex items-center gap-2"><Check className="text-emerald-700" size={16} /> {packages.length} package{packages.length === 1 ? "" : "s"} published</p><p className="flex items-center gap-2 text-amber-700"><ImagePlus size={16} /> Add three recent event photos</p></div><button className="mt-5 text-sm font-semibold text-primary" onClick={() => setActiveTab("portfolio")}>Improve portfolio</button></div></section></div></section>}

        {activeTab === "reports" && (
          <section className="py-7">
            <div>
              <h2 className="text-xl font-semibold">Vendor reports</h2>
              <p className="mt-1 text-sm text-muted-foreground">Lead funnel, quote conversion, and booked value.</p>
            </div>

            {analyticsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{analyticsError}</p>}

            {isLoadingAnalytics ? (
              <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {[1, 2, 3, 4].map((item) => <div className="h-28 animate-pulse rounded-lg border border-border bg-white" key={item} />)}
              </div>
            ) : (
              <>
                <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  <ReportMetric label="Total leads" value={analytics.leads.toLocaleString("en-IN")} />
                  <ReportMetric label="Contacted" value={analytics.contacted.toLocaleString("en-IN")} />
                  <ReportMetric label="Quotes sent" value={analytics.quotesSent.toLocaleString("en-IN")} />
                  <ReportMetric label="Booked" value={analytics.booked.toLocaleString("en-IN")} />
                  <ReportMetric label="Booked value" value={formatCompactMoney(analytics.bookedValue)} />
                  <ReportMetric label="Conversion rate" value={`${analytics.conversionRate}%`} />
                  <ReportMetric label="Average budget" value={formatCompactMoney(analytics.averageBudget)} />
                  <ReportMetric label="Response rate" value={`${analytics.responseRate}%`} />
                </div>

                <div className="mt-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
                  <section className="rounded-lg border border-border bg-white">
                    <div className="border-b border-border px-5 py-4">
                      <h3 className="font-semibold">Monthly trend</h3>
                    </div>
                    <div className="divide-y divide-border">
                      {analytics.trends.map((point) => (
                        <div className="grid gap-2 px-5 py-4 text-sm sm:grid-cols-[80px_1fr_1fr_1fr]" key={point.label}>
                          <strong>{point.label}</strong>
                          <span>{point.enquiries} leads</span>
                          <span>{point.bookings} bookings</span>
                          <span className="font-medium">{formatCompactMoney(point.revenue)}</span>
                        </div>
                      ))}
                    </div>
                  </section>

                  <section className="rounded-lg border border-border bg-white">
                    <div className="border-b border-border px-5 py-4">
                      <h3 className="font-semibold">Service mix</h3>
                    </div>
                    <div className="divide-y divide-border">
                      {analytics.serviceMix.map((item) => (
                        <div className="flex items-center justify-between gap-4 px-5 py-4" key={item.service}>
                          <p className="font-medium">{item.service}</p>
                          <span className="rounded-full bg-violet-50 px-2.5 py-1 text-sm font-semibold text-violet-700">{item.count}</span>
                        </div>
                      ))}
                    </div>
                  </section>
                </div>
              </>
            )}
          </section>
        )}

        {activeTab === "leads" && <section className="py-7"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Lead inbox</h2><p className="mt-1 text-sm text-muted-foreground">Qualify requests and keep the customer status current.</p></div><label className="text-xs font-medium text-muted-foreground">Status<select className="mt-1 block h-10 rounded-md border border-border bg-white px-3 text-sm text-foreground" onChange={(event) => setLeadFilter(event.target.value as "ALL" | VendorLeadStatus)} value={leadFilter}><option value="ALL">All leads</option><option value="NEW">New</option><option value="CONTACTED">Contacted</option><option value="QUOTE_SENT">Quote sent</option><option value="BOOKED">Booked</option><option value="DECLINED">Declined</option></select></label></div>{leadsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{leadsError}</p>}{isLoadingLeads ? <div className="mt-5 grid gap-4">{[1, 2, 3].map((item) => <div className="h-36 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div> : filteredLeads.length > 0 ? <div className="mt-5 grid gap-4">{filteredLeads.map((lead) => <article className="rounded-lg border border-border bg-white p-5" key={lead.id}><div className="flex flex-wrap items-start justify-between gap-4"><div><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{lead.eventType} | {lead.service}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle[lead.status]}`}>{readableStatus(lead.status)}</span></div><p className="mt-2 text-sm text-muted-foreground">{lead.eventDate} | {lead.location}</p></div><div className="text-right"><p className="font-semibold">INR {formatMoney(lead.budget)}</p><p className="mt-1 text-xs text-muted-foreground">Expected budget</p></div></div><div className="mt-4 grid gap-3 rounded-md bg-muted/60 p-4 text-sm sm:grid-cols-2"><p><span className="text-muted-foreground">Customer:</span> {lead.customerName}</p><p><span className="text-muted-foreground">Reference:</span> {lead.id}</p>{lead.notes && <p className="leading-6 sm:col-span-2"><span className="text-muted-foreground">Notes:</span> {lead.notes}</p>}</div>{lead.status !== "BOOKED" && lead.status !== "DECLINED" && <div className="mt-5 flex flex-wrap gap-2 border-t border-border pt-4">{lead.status === "NEW" && <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold" onClick={() => updateLead(lead.id, "CONTACTED")}><MessageSquareText size={17} /> Mark contacted</button>}{lead.status !== "QUOTE_SENT" && <button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" onClick={() => updateLead(lead.id, "QUOTE_SENT")}><Send size={17} /> Mark quote sent</button>}<button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white" onClick={() => updateLead(lead.id, "BOOKED")}><Check size={17} /> Mark booked</button><button className="h-10 px-3 text-sm font-medium text-rose-700" onClick={() => updateLead(lead.id, "DECLINED")}>Decline</button></div>}</article>)}</div> : <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><MessageSquareText className="mx-auto text-muted-foreground" size={28} /><h3 className="mt-4 font-semibold">No leads yet</h3><p className="mt-2 text-sm text-muted-foreground">New quote requests will appear here.</p></div>}</section>}

        {activeTab === "services" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div><h2 className="text-xl font-semibold">Services and packages</h2><p className="mt-1 text-sm text-muted-foreground">Transparent starting prices help customers qualify themselves.</p></div>
              <button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" onClick={() => openPackageEditor()}><Plus size={17} /> Add package</button>
            </div>

            {packagesError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{packagesError}</p>}

            {isPackageEditorOpen && (
              <div className="mt-5 rounded-lg border border-border bg-white p-5">
                <div className="flex flex-wrap items-start justify-between gap-4"><div><h3 className="font-semibold">{editingPackageId ? "Edit package" : "Add package"}</h3><p className="mt-1 text-sm text-muted-foreground">Set a clear package name, starting price, and inclusions.</p></div><button className="grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-muted" onClick={closePackageEditor} type="button"><X size={17} /></button></div>
                <div className="mt-5 grid gap-4 sm:grid-cols-2">
                  <label className="text-sm font-medium">Package name<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => setPackageForm((current) => ({ ...current, name: event.target.value }))} placeholder="e.g. Wedding essentials" value={packageForm.name} /></label>
                  <label className="text-sm font-medium">Starting price<span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => setPackageForm((current) => ({ ...current, price: event.target.value }))} placeholder="0" type="number" value={packageForm.price} /></span></label>
                  <label className="text-sm font-medium sm:col-span-2">Description<textarea className="mt-2 min-h-20 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" onChange={(event) => setPackageForm((current) => ({ ...current, description: event.target.value }))} placeholder="Short package summary" value={packageForm.description} /></label>
                  <label className="text-sm font-medium sm:col-span-2">Inclusions<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => setPackageForm((current) => ({ ...current, includes: event.target.value }))} placeholder="Welcome drink, meal, service staff" value={packageForm.includes} /><span className="mt-1 block text-xs font-normal text-muted-foreground">Separate inclusions with commas</span></label>
                </div>
                <div className="mt-5 flex flex-wrap justify-end gap-2"><button className="h-10 rounded-md border border-border px-4 text-sm font-semibold" onClick={closePackageEditor} type="button">Cancel</button><button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={Boolean(savingPackageId)} onClick={savePackage} type="button">{savingPackageId && <LoaderCircle className="animate-spin" size={16} />} Save package</button></div>
              </div>
            )}

            {isLoadingPackages ? (
              <div className="mt-5 grid gap-4 sm:grid-cols-2">{[1, 2].map((item) => <div className="h-56 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div>
            ) : packages.length > 0 ? (
              <div className="mt-5 grid gap-4 sm:grid-cols-2">
                {packages.map((item) => <article className="rounded-lg border border-border bg-white p-5" key={item.id}><div className="flex items-start justify-between gap-4"><span className="grid size-10 place-items-center rounded-md bg-violet-50 text-violet-700"><Layers3 size={19} /></span><div className="flex gap-1"><button aria-label={`Edit ${item.name}`} className="grid size-9 place-items-center rounded-md text-primary hover:bg-muted" onClick={() => openPackageEditor(item)} type="button"><Pencil size={16} /></button><button aria-label={`Delete ${item.name}`} className="grid size-9 place-items-center rounded-md text-rose-700 hover:bg-rose-50 disabled:opacity-50" disabled={deletingPackageId === item.id} onClick={() => removePackage(item.id)} type="button">{deletingPackageId === item.id ? <LoaderCircle className="animate-spin" size={16} /> : <Trash2 size={16} />}</button></div></div><h3 className="mt-5 text-lg font-semibold">{item.name}</h3><p className="mt-2 text-sm leading-6 text-muted-foreground">{item.description}</p><p className="mt-4 text-xl font-semibold">INR {formatMoney(item.price)} / plate</p><div className="mt-4 flex flex-wrap gap-2">{item.includes.map((included) => <span className="rounded-md bg-muted px-2.5 py-1 text-xs text-muted-foreground" key={included}>{included}</span>)}</div></article>)}
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><Layers3 className="mx-auto text-muted-foreground" size={28} /><h3 className="mt-4 font-semibold">No packages yet</h3><p className="mt-2 text-sm text-muted-foreground">Add your first package to publish clear starting prices.</p></div>
            )}
          </section>
        )}

        {activeTab === "portfolio" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div><h2 className="text-xl font-semibold">Portfolio</h2><p className="mt-1 text-sm text-muted-foreground">Show recent, clearly photographed event work.</p></div>
              <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60"><ImagePlus size={17} /> {isUploadingPortfolio ? "Uploading" : "Add photos"}<input accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={isUploadingPortfolio} multiple onChange={(event) => addPortfolioImages(event.target.files)} type="file" /></label>
            </div>
            {portfolioError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{portfolioError}</p>}
            {isUploadingPortfolio && <p className="mt-4 inline-flex items-center gap-2 rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-700"><LoaderCircle className="animate-spin" size={16} /> Uploading photos</p>}
            {isLoadingPortfolio ? (
              <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">{[1, 2, 3, 4].map((item) => <div className="aspect-[4/3] animate-pulse rounded-lg bg-white" key={item} />)}</div>
            ) : portfolio.length > 0 ? (
              <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                {portfolio.map((media, index) => <div className="group relative aspect-[4/3] overflow-hidden rounded-lg bg-muted" key={media.id}><Image alt={media.caption ?? `Portfolio image ${index + 1}`} className="object-cover" fill sizes="(max-width: 640px) 50vw, 25vw" src={media.url} unoptimized={media.url.startsWith("blob:")} />{media.isCover && <span className="absolute left-2 top-2 rounded-full bg-white px-2 py-1 text-xs font-medium text-primary">Cover</span>}<div className="absolute inset-x-2 bottom-2 flex justify-end gap-1 opacity-0 transition group-hover:opacity-100"><button className="h-8 rounded-md bg-white px-2 text-xs font-semibold text-primary shadow-sm disabled:opacity-50" disabled={media.isCover || updatingMediaId === media.id} onClick={() => makePortfolioCover(media.id)} type="button">Cover</button><button aria-label="Delete photo" className="grid size-8 place-items-center rounded-md bg-white text-rose-700 shadow-sm disabled:opacity-50" disabled={updatingMediaId === media.id} onClick={() => removePortfolioImage(media.id)} type="button">{updatingMediaId === media.id ? <LoaderCircle className="animate-spin" size={15} /> : <Trash2 size={15} />}</button></div></div>)}
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><ImagePlus className="mx-auto text-muted-foreground" size={28} /><h3 className="mt-4 font-semibold">No portfolio photos yet</h3><p className="mt-2 text-sm text-muted-foreground">Add clear examples of your recent event work.</p></div>
            )}
          </section>
        )}

        {activeTab === "subscription" && <section className="py-7"><div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-xl font-semibold">Subscription</h2><p className="mt-1 text-sm text-muted-foreground">Choose how prominently your business appears and how many leads you can receive.</p></div><div className="rounded-md border border-border bg-white px-4 py-3 text-sm"><span className="text-muted-foreground">Current status</span><strong className="ml-2 capitalize">{subscriptionStatusLabel(subscription.status)}</strong>{subscription.currentPeriodEnd && <p className="mt-1 text-xs text-muted-foreground">Renews {new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(new Date(subscription.currentPeriodEnd))}</p>}{subscription.pendingOrderId && <p className="mt-1 text-xs text-muted-foreground">Order {subscription.pendingOrderId}</p>}</div></div>{subscriptionError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{subscriptionError}</p>}{isLoadingSubscription ? <div className="mt-5 grid gap-5 lg:grid-cols-2">{[1, 2].map((item) => <div className="h-72 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div> : <div className="mt-5 grid gap-5 lg:grid-cols-2">{subscriptionPlans.map((item) => { const isActive = subscription.planId === item.id && subscription.status === "ACTIVE"; const isPending = subscription.planId === item.id && subscription.status === "PENDING_PAYMENT"; return <article className={`rounded-lg border bg-white p-6 ${isActive || isPending ? "border-primary" : "border-border"}`} key={item.id}><div className="flex items-start justify-between gap-4"><div><h3 className="text-xl font-semibold">{item.name}</h3><p className="mt-1 text-sm text-muted-foreground">{item.description}</p></div>{isActive && <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-800">Current plan</span>}{isPending && <span className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-800">Payment pending</span>}{item.isPopular && !isActive && !isPending && <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">Popular</span>}</div><p className="mt-6 text-3xl font-semibold">INR {formatMoney(item.price)} <span className="text-sm font-normal text-muted-foreground">/ {billingLabel(item.billingCycle)}</span></p><div className="mt-6 grid gap-3 text-sm">{item.features.map((feature) => <p className="flex items-center gap-2" key={feature}><Check className="text-emerald-700" size={16} />{feature}</p>)}</div><button className={`mt-7 inline-flex h-11 w-full items-center justify-center gap-2 rounded-md text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${isActive ? "border border-border text-muted-foreground" : "bg-primary text-white"}`} disabled={isActive || checkoutPlanId === item.id} onClick={() => chooseSubscription(item.id)}>{checkoutPlanId === item.id ? <><LoaderCircle className="animate-spin" size={17} /> Creating order</> : isActive ? "Active plan" : isPending ? <>Retry payment <ArrowUpRight size={17} /></> : <>Choose {item.name} <ArrowUpRight size={17} /></>}</button></article>; })}</div>}<p className="mt-5 flex items-center gap-2 text-sm text-muted-foreground"><Sparkles className="text-amber-600" size={17} /> Razorpay order creation and payment verification are handled by the backend.</p></section>}
      </div>
    </main>
  );
}

function ReportMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-white p-5">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-2 text-2xl font-semibold">{value}</p>
    </div>
  );
}
