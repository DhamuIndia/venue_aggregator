"use client";

import { ArrowLeft, ArrowRight, BadgeCheck, BriefcaseBusiness, Check, ImagePlus, MapPin, UploadCloud } from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { categoryLabels } from "@/features/vendors/mock-data";
import type { VendorCategory } from "@/features/vendors/types";
import { workspaceVendor } from "@/features/vendors/workspace-data";

const steps = ["Business", "Services", "Portfolio", "Review"];
const serviceOptions = ["Wedding service", "Reception service", "Corporate events", "Consultation", "Custom package", "On-site team"];

export function VendorOnboarding() {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [error, setError] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [services, setServices] = useState<string[]>(["Wedding service", "Reception service"]);
  const [fileNames, setFileNames] = useState<string[]>([]);
  const [form, setForm] = useState({
    businessName: "",
    category: "CATERING" as VendorCategory,
    city: "Chennai",
    area: "",
    serviceRadius: "25",
    yearsInBusiness: "",
    description: "",
    packageName: "",
    startingPrice: "",
    packageDescription: ""
  });

  function updateField(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setError("");
  }

  function toggleService(service: string) {
    setServices((current) => current.includes(service) ? current.filter((item) => item !== service) : [...current, service]);
  }

  function continueStep() {
    if (step === 0 && (!form.businessName.trim() || !form.area.trim() || Number(form.yearsInBusiness) < 1)) {
      setError("Enter the business name, area, and years in operation.");
      return;
    }
    if (step === 1 && (services.length === 0 || !form.packageName.trim() || Number(form.startingPrice) < 1)) {
      setError("Select a service and add a package with a starting price.");
      return;
    }
    setError("");
    setStep((current) => Math.min(current + 1, steps.length - 1));
  }

  function submit() {
    if (!confirmed) return;
    window.localStorage.setItem("venue-vendor-onboarding", JSON.stringify({ ...form, services, status: "PENDING_APPROVAL" }));
    router.push("/vendor?submitted=true");
  }

  return (
    <main className="mx-auto w-full max-w-6xl px-4 py-7 sm:px-6 sm:py-10">
      <div className="flex flex-wrap items-start justify-between gap-5"><div><p className="text-sm font-semibold text-primary">Vendor onboarding</p><h1 className="mt-2 text-3xl font-semibold">Create your service profile</h1><p className="mt-2 text-sm text-muted-foreground">Complete the profile for marketplace approval.</p></div><button className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium" onClick={() => router.push("/vendor")}><ArrowLeft size={17} /> Vendor workspace</button></div>
      <div className="mt-8 grid gap-8 lg:grid-cols-[240px_minmax(0,1fr)]">
        <nav aria-label="Onboarding progress" className="h-fit lg:sticky lg:top-24"><ol className="grid grid-cols-4 gap-2 lg:grid-cols-1">{steps.map((label, index) => <li className={`flex min-w-0 items-center gap-3 rounded-md p-2 text-sm ${index === step ? "bg-emerald-50 font-semibold text-primary" : index < step ? "text-emerald-700" : "text-muted-foreground"}`} key={label}><span className={`grid size-7 shrink-0 place-items-center rounded-full border text-xs ${index <= step ? "border-primary bg-primary text-white" : "border-border bg-white"}`}>{index < step ? <Check size={14} /> : index + 1}</span><span className="hidden truncate lg:block">{label}</span></li>)}</ol></nav>
        <section className="rounded-lg border border-border bg-white p-5 shadow-sm sm:p-7">
          {step === 0 && <div><div className="flex items-center gap-3"><BriefcaseBusiness className="text-primary" size={23} /><div><h2 className="text-xl font-semibold">Business details</h2><p className="mt-1 text-sm text-muted-foreground">Information customers and admins will verify.</p></div></div><div className="mt-7 grid gap-5 sm:grid-cols-2"><label className="text-sm font-medium sm:col-span-2">Business name<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("businessName", event.target.value)} placeholder="Registered or trading name" value={form.businessName} /></label><label className="text-sm font-medium">Service category<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("category", event.target.value)} value={form.category}>{(Object.entries(categoryLabels) as [VendorCategory, string][]).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label className="text-sm font-medium">Years in business<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => updateField("yearsInBusiness", event.target.value)} placeholder="Years" type="number" value={form.yearsInBusiness} /></label><label className="text-sm font-medium">City<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("city", event.target.value)} value={form.city}><option>Chennai</option><option>Coimbatore</option><option>Madurai</option><option>Bengaluru</option></select></label><label className="text-sm font-medium">Area<span className="relative mt-2 block"><MapPin className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} /><input className="h-11 w-full rounded-md border border-border pl-10 pr-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("area", event.target.value)} placeholder="Business locality" value={form.area} /></span></label><label className="text-sm font-medium">Service radius<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => updateField("serviceRadius", event.target.value)} type="number" value={form.serviceRadius} /><span className="mt-1 block text-xs font-normal text-muted-foreground">Kilometres from your business location</span></label><label className="text-sm font-medium sm:col-span-2">About the business<textarea className="mt-2 min-h-28 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" onChange={(event) => updateField("description", event.target.value)} placeholder="Describe your approach, team, and event experience." value={form.description} /></label></div></div>}
          {step === 1 && <div><h2 className="text-xl font-semibold">Services and starting package</h2><p className="mt-1 text-sm text-muted-foreground">Help customers understand your offering before they enquire.</p><fieldset className="mt-7"><legend className="text-sm font-medium">Services offered</legend><div className="mt-3 grid gap-2 sm:grid-cols-2">{serviceOptions.map((service) => <label className={`flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-sm ${services.includes(service) ? "border-primary bg-emerald-50" : "border-border"}`} key={service}><input checked={services.includes(service)} className="size-4 accent-[hsl(var(--primary))]" onChange={() => toggleService(service)} type="checkbox" />{service}</label>)}</div></fieldset><div className="mt-7 grid gap-5 sm:grid-cols-2"><label className="text-sm font-medium">Package name<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("packageName", event.target.value)} placeholder="e.g. Wedding essentials" value={form.packageName} /></label><label className="text-sm font-medium">Starting price<span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => updateField("startingPrice", event.target.value)} placeholder="0" type="number" value={form.startingPrice} /></span></label><label className="text-sm font-medium sm:col-span-2">Package summary<textarea className="mt-2 min-h-24 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" onChange={(event) => updateField("packageDescription", event.target.value)} placeholder="What is included in this starting package?" value={form.packageDescription} /></label></div></div>}
          {step === 2 && <div><div className="flex items-center gap-3"><ImagePlus className="text-primary" size={23} /><div><h2 className="text-xl font-semibold">Portfolio</h2><p className="mt-1 text-sm text-muted-foreground">Add recent work that accurately represents your service.</p></div></div><label className="mt-7 flex min-h-40 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-border bg-background px-5 text-center hover:border-primary"><UploadCloud className="text-primary" size={28} /><span className="mt-3 text-sm font-semibold">Choose portfolio photos</span><span className="mt-1 text-xs text-muted-foreground">JPG, PNG or WebP, up to 12 files</span><input accept="image/jpeg,image/png,image/webp" className="sr-only" multiple onChange={(event) => setFileNames(Array.from(event.target.files ?? []).map((file) => file.name))} type="file" /></label>{fileNames.length > 0 && <p className="mt-3 text-sm text-emerald-700">{fileNames.length} photo{fileNames.length === 1 ? "" : "s"} selected</p>}<div className="mt-7 grid grid-cols-2 gap-3 sm:grid-cols-3">{[workspaceVendor.imageUrl, ...workspaceVendor.galleryUrls].map((url, index) => <div className="relative aspect-[4/3] overflow-hidden rounded-lg bg-muted" key={url}><Image alt={`Portfolio example ${index + 1}`} className="object-cover" fill sizes="240px" src={url} />{index === 0 && <span className="absolute left-2 top-2 rounded-full bg-white px-2 py-1 text-xs font-medium text-primary">Cover</span>}</div>)}</div></div>}
          {step === 3 && <div><div className="flex items-center gap-3"><BadgeCheck className="text-primary" size={24} /><div><h2 className="text-xl font-semibold">Review your profile</h2><p className="mt-1 text-sm text-muted-foreground">Admin approval is required before publication.</p></div></div><dl className="mt-7 divide-y divide-border rounded-lg border border-border">{[{ label: "Business", value: form.businessName }, { label: "Category", value: categoryLabels[form.category] }, { label: "Location", value: `${form.area}, ${form.city}` }, { label: "Experience", value: `${form.yearsInBusiness} years` }, { label: "Services", value: `${services.length} selected` }, { label: "Package", value: form.packageName }, { label: "Starting price", value: `INR ${new Intl.NumberFormat("en-IN").format(Number(form.startingPrice))}` }, { label: "Photos", value: fileNames.length ? `${fileNames.length} selected` : "Add after submission" }].map((item) => <div className="grid gap-1 px-4 py-3 sm:grid-cols-[170px_1fr]" key={item.label}><dt className="text-sm text-muted-foreground">{item.label}</dt><dd className="text-sm font-medium">{item.value || "Not provided"}</dd></div>)}</dl><label className="mt-6 flex items-start gap-3 text-sm text-muted-foreground"><input checked={confirmed} className="mt-1 size-4 accent-[hsl(var(--primary))]" onChange={(event) => setConfirmed(event.target.checked)} type="checkbox" /><span>I confirm that the business information is accurate and I am authorized to manage this profile.</span></label></div>}
          {error && <p className="mt-6 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
          <div className="mt-8 flex items-center justify-between border-t border-border pt-5"><button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-medium disabled:opacity-40" disabled={step === 0} onClick={() => { setStep((current) => current - 1); setError(""); }}><ArrowLeft size={16} /> Back</button>{step < steps.length - 1 ? <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white" onClick={continueStep}>Continue <ArrowRight size={16} /></button> : <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50" disabled={!confirmed} onClick={submit}>Submit for approval <BadgeCheck size={17} /></button>}</div>
        </section>
      </div>
    </main>
  );
}
