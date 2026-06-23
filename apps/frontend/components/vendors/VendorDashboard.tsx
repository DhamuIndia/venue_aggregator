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
  MapPin,
  MessageSquareText,
  Plus,
  Send,
  Sparkles,
  Store,
  X
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { getVendorLeads, updateVendorLeadStatus } from "@/features/vendors/lead-client";
import type { VendorLead, VendorLeadStatus } from "@/features/vendors/types";
import { fallbackVendorLeads, workspaceVendor } from "@/features/vendors/workspace-data";

type VendorTab = "overview" | "leads" | "services" | "portfolio" | "subscription";

const tabs: { id: VendorTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "leads", label: "Leads" },
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

export function VendorDashboard() {
  const { accessToken } = useAuth();
  const [activeTab, setActiveTab] = useState<VendorTab>("overview");
  const [leads, setLeads] = useState<VendorLead[]>(fallbackVendorLeads);
  const [isLoadingLeads, setIsLoadingLeads] = useState(true);
  const [leadsError, setLeadsError] = useState("");
  const [leadFilter, setLeadFilter] = useState<"ALL" | VendorLeadStatus>("ALL");
  const [notice, setNotice] = useState("");
  const [portfolio, setPortfolio] = useState(workspaceVendor.galleryUrls);
  const [plan, setPlan] = useState<"STARTER" | "GROWTH">("STARTER");

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

  function addPortfolioImages(files: FileList | null) {
    if (!files?.length) return;
    setPortfolio((current) => [...Array.from(files).map((file) => URL.createObjectURL(file)), ...current]);
    setNotice(`${files.length} portfolio image${files.length === 1 ? "" : "s"} added.`);
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
        ].map((stat) => <button className="rounded-lg border border-border bg-white p-5 text-left hover:border-primary" key={stat.label} onClick={() => setActiveTab(stat.tab)}><stat.icon className={stat.color} size={21} /><p className="mt-5 text-2xl font-semibold">{stat.value}</p><p className="mt-1 text-sm text-muted-foreground">{stat.label}</p></button>)}</div><div className="mt-9 grid gap-8 lg:grid-cols-[1.45fr_1fr]"><section><div className="flex items-center justify-between"><div><h2 className="text-xl font-semibold">Recent leads</h2><p className="mt-1 text-sm text-muted-foreground">Respond quickly to improve conversion.</p></div><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("leads")}>View all</button></div><div className="mt-4 grid gap-3">{leads.slice(0, 3).map((lead) => <button className="flex w-full items-center gap-4 rounded-lg border border-border bg-white p-4 text-left hover:border-primary" key={lead.id} onClick={() => setActiveTab("leads")}><span className="grid size-11 shrink-0 place-items-center rounded-md bg-blue-50 text-blue-700"><BriefcaseBusiness size={20} /></span><span className="min-w-0 flex-1"><strong className="block truncate">{lead.eventType} | {lead.service}</strong><span className="mt-1 block text-sm text-muted-foreground">{lead.eventDate} | {lead.location}</span></span><span className={`hidden rounded-full px-2.5 py-1 text-xs font-medium sm:block ${statusStyle[lead.status]}`}>{readableStatus(lead.status)}</span><ChevronRight size={18} /></button>)}</div></section><section><h2 className="text-xl font-semibold">Profile strength</h2><div className="mt-4 rounded-lg border border-border bg-white p-5"><div className="flex items-center justify-between"><span className="inline-flex items-center gap-2 font-semibold text-emerald-700"><BadgeCheck size={18} /> Approved</span><span className="text-sm font-semibold">88%</span></div><div className="mt-4 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full w-[88%] bg-primary" /></div><div className="mt-5 grid gap-3 text-sm"><p className="flex items-center gap-2"><Check className="text-emerald-700" size={16} /> Business and services complete</p><p className="flex items-center gap-2"><Check className="text-emerald-700" size={16} /> Two packages published</p><p className="flex items-center gap-2 text-amber-700"><ImagePlus size={16} /> Add three recent event photos</p></div><button className="mt-5 text-sm font-semibold text-primary" onClick={() => setActiveTab("portfolio")}>Improve portfolio</button></div></section></div></section>}

        {activeTab === "leads" && <section className="py-7"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Lead inbox</h2><p className="mt-1 text-sm text-muted-foreground">Qualify requests and keep the customer status current.</p></div><label className="text-xs font-medium text-muted-foreground">Status<select className="mt-1 block h-10 rounded-md border border-border bg-white px-3 text-sm text-foreground" onChange={(event) => setLeadFilter(event.target.value as "ALL" | VendorLeadStatus)} value={leadFilter}><option value="ALL">All leads</option><option value="NEW">New</option><option value="CONTACTED">Contacted</option><option value="QUOTE_SENT">Quote sent</option><option value="BOOKED">Booked</option><option value="DECLINED">Declined</option></select></label></div>{leadsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{leadsError}</p>}{isLoadingLeads ? <div className="mt-5 grid gap-4">{[1, 2, 3].map((item) => <div className="h-36 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div> : filteredLeads.length > 0 ? <div className="mt-5 grid gap-4">{filteredLeads.map((lead) => <article className="rounded-lg border border-border bg-white p-5" key={lead.id}><div className="flex flex-wrap items-start justify-between gap-4"><div><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{lead.eventType} | {lead.service}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle[lead.status]}`}>{readableStatus(lead.status)}</span></div><p className="mt-2 text-sm text-muted-foreground">{lead.eventDate} | {lead.location}</p></div><div className="text-right"><p className="font-semibold">INR {formatMoney(lead.budget)}</p><p className="mt-1 text-xs text-muted-foreground">Expected budget</p></div></div><div className="mt-4 grid gap-3 rounded-md bg-muted/60 p-4 text-sm sm:grid-cols-2"><p><span className="text-muted-foreground">Customer:</span> {lead.customerName}</p><p><span className="text-muted-foreground">Reference:</span> {lead.id}</p>{lead.notes && <p className="leading-6 sm:col-span-2"><span className="text-muted-foreground">Notes:</span> {lead.notes}</p>}</div>{lead.status !== "BOOKED" && lead.status !== "DECLINED" && <div className="mt-5 flex flex-wrap gap-2 border-t border-border pt-4">{lead.status === "NEW" && <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold" onClick={() => updateLead(lead.id, "CONTACTED")}><MessageSquareText size={17} /> Mark contacted</button>}{lead.status !== "QUOTE_SENT" && <button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" onClick={() => updateLead(lead.id, "QUOTE_SENT")}><Send size={17} /> Mark quote sent</button>}<button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white" onClick={() => updateLead(lead.id, "BOOKED")}><Check size={17} /> Mark booked</button><button className="h-10 px-3 text-sm font-medium text-rose-700" onClick={() => updateLead(lead.id, "DECLINED")}>Decline</button></div>}</article>)}</div> : <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><MessageSquareText className="mx-auto text-muted-foreground" size={28} /><h3 className="mt-4 font-semibold">No leads yet</h3><p className="mt-2 text-sm text-muted-foreground">New quote requests will appear here.</p></div>}</section>}

        {activeTab === "services" && <section className="py-7"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Services and packages</h2><p className="mt-1 text-sm text-muted-foreground">Transparent starting prices help customers qualify themselves.</p></div><button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white"><Plus size={17} /> Add package</button></div><div className="mt-5 grid gap-4 sm:grid-cols-2">{workspaceVendor.packages.map((item) => <article className="rounded-lg border border-border bg-white p-5" key={item.id}><div className="flex items-start justify-between gap-4"><span className="grid size-10 place-items-center rounded-md bg-violet-50 text-violet-700"><Layers3 size={19} /></span><button className="text-sm font-semibold text-primary">Edit</button></div><h3 className="mt-5 text-lg font-semibold">{item.name}</h3><p className="mt-2 text-sm leading-6 text-muted-foreground">{item.description}</p><p className="mt-4 text-xl font-semibold">INR {formatMoney(item.price)} / plate</p><div className="mt-4 flex flex-wrap gap-2">{item.includes.map((included) => <span className="rounded-md bg-muted px-2.5 py-1 text-xs text-muted-foreground" key={included}>{included}</span>)}</div></article>)}</div></section>}

        {activeTab === "portfolio" && <section className="py-7"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Portfolio</h2><p className="mt-1 text-sm text-muted-foreground">Show recent, clearly photographed event work.</p></div><label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white"><ImagePlus size={17} /> Add photos<input accept="image/jpeg,image/png,image/webp" className="sr-only" multiple onChange={(event) => addPortfolioImages(event.target.files)} type="file" /></label></div><div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">{[workspaceVendor.imageUrl, ...portfolio].map((url, index) => <div className="group relative aspect-[4/3] overflow-hidden rounded-lg bg-muted" key={`${url}-${index}`}><Image alt={`Portfolio image ${index + 1}`} className="object-cover" fill sizes="(max-width: 640px) 50vw, 25vw" src={url} unoptimized={url.startsWith("blob:")} />{index === 0 && <span className="absolute left-2 top-2 rounded-full bg-white px-2 py-1 text-xs font-medium text-primary">Cover</span>}</div>)}</div></section>}

        {activeTab === "subscription" && <section className="py-7"><div><h2 className="text-xl font-semibold">Subscription</h2><p className="mt-1 text-sm text-muted-foreground">Choose how prominently your business appears and how many leads you can receive.</p></div><div className="mt-5 grid gap-5 lg:grid-cols-2">{[
          { id: "STARTER" as const, name: "Starter", price: 999, description: "For a growing local service business.", features: ["Public verified profile", "Up to 20 leads per month", "Two service packages", "Standard marketplace ranking"] },
          { id: "GROWTH" as const, name: "Growth", price: 2499, description: "For teams ready to expand across the city.", features: ["Unlimited customer leads", "Unlimited packages", "Priority marketplace placement", "Lead performance analytics"] }
        ].map((item) => <article className={`rounded-lg border bg-white p-6 ${plan === item.id ? "border-primary" : "border-border"}`} key={item.id}><div className="flex items-start justify-between gap-4"><div><h3 className="text-xl font-semibold">{item.name}</h3><p className="mt-1 text-sm text-muted-foreground">{item.description}</p></div>{plan === item.id && <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-800">Current plan</span>}</div><p className="mt-6 text-3xl font-semibold">INR {formatMoney(item.price)} <span className="text-sm font-normal text-muted-foreground">/ month</span></p><div className="mt-6 grid gap-3 text-sm">{item.features.map((feature) => <p className="flex items-center gap-2" key={feature}><Check className="text-emerald-700" size={16} />{feature}</p>)}</div><button className={`mt-7 inline-flex h-11 w-full items-center justify-center gap-2 rounded-md text-sm font-semibold ${plan === item.id ? "border border-border text-muted-foreground" : "bg-primary text-white"}`} disabled={plan === item.id} onClick={() => { setPlan(item.id); setNotice(`${item.name} plan selected. Payment integration will complete activation.`); }}>{plan === item.id ? "Active plan" : <>Choose {item.name} <ArrowUpRight size={17} /></>}</button></article>)}</div><p className="mt-5 flex items-center gap-2 text-sm text-muted-foreground"><Sparkles className="text-amber-600" size={17} /> Payments will be handled through the backend Razorpay order and webhook flow.</p></section>}
      </div>
    </main>
  );
}
