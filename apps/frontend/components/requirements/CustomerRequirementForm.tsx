"use client";

import { CalendarDays, CheckCircle2, ClipboardList, LoaderCircle, MapPin, Send } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { createCustomerRequirement, getRequirementOptions } from "@/features/requirements/requirements-client";
import type { CustomerRequirement, PreferredContactChannel, RequirementCategory } from "@/features/requirements/types";

const eventTypes = ["Wedding", "Reception", "Engagement", "Birthday", "Corporate event", "Other"];
const contactOptions: Array<{ value: PreferredContactChannel; label: string; detail: string }> = [
  { value: "IN_APP", label: "VenueMart", detail: "Keep communication inside your account" },
  { value: "PHONE", label: "Phone", detail: "Prefer a call from matched vendors" },
  { value: "WHATSAPP", label: "WhatsApp", detail: "Prefer WhatsApp communication" },
  { value: "EMAIL", label: "Email", detail: "Prefer responses by email" }
];

type FormState = {
  categoryIds: number[];
  eventType: string;
  eventDate: string;
  location: string;
  city: string;
  pincode: string;
  budgetMin: string;
  budgetMax: string;
  guestCount: string;
  details: string;
  preferredContactChannel: PreferredContactChannel;
  shareContactDetails: boolean;
};

const initialForm: FormState = {
  categoryIds: [],
  eventType: "Wedding",
  eventDate: "",
  location: "",
  city: "Chennai",
  pincode: "",
  budgetMin: "",
  budgetMax: "",
  guestCount: "",
  details: "",
  preferredContactChannel: "IN_APP",
  shareContactDetails: false
};

export function CustomerRequirementForm() {
  const { getValidAccessToken } = useAuth();
  const [form, setForm] = useState(initialForm);
  const [categories, setCategories] = useState<RequirementCategory[]>([]);
  const [isAvailable, setIsAvailable] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState<CustomerRequirement | null>(null);
  const minimumDate = useMemo(localToday, []);

  useEffect(() => {
    let current = true;
    getRequirementOptions()
      .then((options) => {
        if (!current) return;
        setIsAvailable(options.enabled);
        setCategories(options.categories.filter((category) => category.name.toLowerCase() !== "hall"));
      })
      .catch(() => {
        if (current) setError("Could not load service options. Please try again.");
      })
      .finally(() => {
        if (current) setIsLoading(false);
      });
    return () => {
      current = false;
    };
  }, []);

  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function toggleCategory(categoryId: number) {
    setForm((current) => ({
      ...current,
      categoryIds: current.categoryIds.includes(categoryId)
        ? current.categoryIds.filter((id) => id !== categoryId)
        : [...current.categoryIds, categoryId]
    }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const validationError = validate(form, minimumDate);
    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setIsSubmitting(true);
      const token = await getValidAccessToken();
      if (!token) {
        window.location.assign(`/auth/login?next=${encodeURIComponent("/requirements/new")}`);
        return;
      }
      const requirement = await createCustomerRequirement({
        categoryIds: form.categoryIds,
        eventType: form.eventType,
        eventDate: form.eventDate,
        location: form.location.trim(),
        city: form.city.trim(),
        pincode: form.pincode.trim() || undefined,
        budgetMin: optionalNumber(form.budgetMin),
        budgetMax: optionalNumber(form.budgetMax),
        guestCount: optionalNumber(form.guestCount),
        details: form.details.trim() || undefined,
        preferredContactChannel: form.preferredContactChannel,
        shareContactDetails: form.shareContactDetails
      }, token);
      setCreated(requirement);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not submit your requirement. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return <div className="grid min-h-[55vh] place-items-center"><LoaderCircle aria-label="Loading requirement form" className="animate-spin text-primary" size={28} /></div>;
  }

  if (!isAvailable) {
    return <main className="mx-auto w-full max-w-3xl px-4 py-12 sm:px-6"><div className="rounded-lg border border-border bg-white p-8 text-center"><ClipboardList className="mx-auto text-muted-foreground" size={32} /><h1 className="mt-4 text-2xl font-semibold">Requirements are not open yet</h1><p className="mt-2 text-muted-foreground">This service is being prepared. You can continue browsing halls and vendors.</p><Link className="mt-6 inline-flex h-11 items-center rounded-md bg-foreground px-5 text-sm font-semibold text-white" href="/">Back to VenueMart</Link></div></main>;
  }

  if (created) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-12 sm:px-6">
        <div className="rounded-xl border border-emerald-200 bg-white p-8 text-center shadow-sm">
          <CheckCircle2 className="mx-auto text-emerald-700" size={42} />
          <p className="mt-5 text-sm font-semibold text-emerald-700">Requirement submitted</p>
          <h1 className="mt-2 text-2xl font-semibold">Your event requirement is saved</h1>
          <p className="mx-auto mt-3 max-w-xl leading-7 text-muted-foreground">Reference {created.id}. You can review the information from your account. We will show vendor activity here once matching is enabled.</p>
          <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
            <Link className="inline-flex h-11 items-center justify-center rounded-md bg-primary px-5 text-sm font-semibold text-white" href="/customer?tab=requirements">View my requirements</Link>
            <button className="h-11 rounded-md border border-border px-5 text-sm font-semibold hover:border-primary" onClick={() => { setCreated(null); setForm(initialForm); }} type="button">Post another</button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6 sm:py-11">
      <div className="max-w-2xl">
        <p className="text-sm font-semibold text-primary">Marketplace requirement</p>
        <h1 className="mt-2 text-3xl font-semibold">Tell us what your event needs</h1>
        <p className="mt-3 leading-7 text-muted-foreground">Create one requirement covering all the event services you are looking for and save it securely to your account.</p>
      </div>

      <form className="mt-8 grid gap-6" onSubmit={submit}>
        <section className="rounded-lg border border-border bg-white p-5 sm:p-6">
          <div className="flex items-center gap-3"><ClipboardList className="text-primary" size={22} /><div><h2 className="font-semibold">Services required</h2><p className="mt-1 text-sm text-muted-foreground">Select one or more services.</p></div></div>
          <fieldset className="mt-5 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            <legend className="sr-only">Services required</legend>
            {categories.map((category) => {
              const selected = form.categoryIds.includes(category.id);
              return <label className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-md border px-4 text-sm font-medium transition ${selected ? "border-primary bg-emerald-50 text-emerald-900" : "border-border hover:border-primary"}`} key={category.id}><input checked={selected} className="size-4 accent-[hsl(var(--primary))]" onChange={() => toggleCategory(category.id)} type="checkbox" />{serviceLabel(category.name)}</label>;
            })}
          </fieldset>
        </section>

        <section className="rounded-lg border border-border bg-white p-5 sm:p-6">
          <div className="flex items-center gap-3"><CalendarDays className="text-primary" size={22} /><h2 className="font-semibold">Event details</h2></div>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            <label className="text-sm font-medium">Event type<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("eventType", event.target.value)} value={form.eventType}>{eventTypes.map((type) => <option key={type}>{type}</option>)}</select></label>
            <label className="text-sm font-medium">Event date<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" min={minimumDate} name="eventDate" onChange={(event) => updateField("eventDate", event.target.value)} onInput={(event) => updateField("eventDate", event.currentTarget.value)} required type="date" value={form.eventDate} /></label>
            <label className="text-sm font-medium">Expected guests <span className="font-normal text-muted-foreground">(optional)</span><input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => updateField("guestCount", event.target.value)} placeholder="e.g. 500" type="number" value={form.guestCount} /></label>
            <div />
            <label className="text-sm font-medium">Minimum budget <span className="font-normal text-muted-foreground">(optional)</span><span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="0" onChange={(event) => updateField("budgetMin", event.target.value)} placeholder="50,000" type="number" value={form.budgetMin} /></span></label>
            <label className="text-sm font-medium">Maximum budget <span className="font-normal text-muted-foreground">(optional)</span><span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="0" onChange={(event) => updateField("budgetMax", event.target.value)} placeholder="1,50,000" type="number" value={form.budgetMax} /></span></label>
          </div>
        </section>

        <section className="rounded-lg border border-border bg-white p-5 sm:p-6">
          <div className="flex items-center gap-3"><MapPin className="text-primary" size={22} /><h2 className="font-semibold">Event location</h2></div>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            <label className="text-sm font-medium">Area or locality<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("location", event.target.value)} placeholder="e.g. Adyar" required value={form.location} /></label>
            <label className="text-sm font-medium">City<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateField("city", event.target.value)} placeholder="e.g. Chennai" required value={form.city} /></label>
            <label className="text-sm font-medium">Pincode <span className="font-normal text-muted-foreground">(optional)</span><input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="numeric" maxLength={6} onChange={(event) => updateField("pincode", event.target.value.replace(/\D/g, ""))} placeholder="600020" value={form.pincode} /></label>
          </div>
        </section>

        <section className="rounded-lg border border-border bg-white p-5 sm:p-6">
          <h2 className="font-semibold">Additional details</h2>
          <textarea className="mt-4 min-h-32 w-full resize-y rounded-md border border-border p-3 text-sm leading-6 outline-none focus:border-primary" maxLength={2000} onChange={(event) => updateField("details", event.target.value)} placeholder="Describe the style, timing, deliverables, or any special expectations." value={form.details} />
          <p className="mt-2 text-right text-xs text-muted-foreground">{form.details.length}/2000</p>
        </section>

        <section className="rounded-lg border border-border bg-white p-5 sm:p-6">
          <h2 className="font-semibold">Contact preference</h2>
          <div className="mt-4 grid gap-2 sm:grid-cols-2">
            {contactOptions.map((option) => <label className={`cursor-pointer rounded-md border p-4 ${form.preferredContactChannel === option.value ? "border-primary bg-emerald-50" : "border-border"}`} key={option.value}><span className="flex items-center gap-3"><input checked={form.preferredContactChannel === option.value} className="size-4 accent-[hsl(var(--primary))]" name="contact-channel" onChange={() => updateField("preferredContactChannel", option.value)} type="radio" /><strong className="text-sm">{option.label}</strong></span><span className="mt-1 block pl-7 text-xs leading-5 text-muted-foreground">{option.detail}</span></label>)}
          </div>
          <label className="mt-5 flex items-start gap-3 rounded-md border border-border bg-muted/40 p-4 text-sm"><input checked={form.shareContactDetails} className="mt-0.5 size-4 accent-[hsl(var(--primary))]" onChange={(event) => updateField("shareContactDetails", event.target.checked)} type="checkbox" /><span><strong className="block font-medium">Allow contact details to be shared with matched vendors</strong><span className="mt-1 block leading-5 text-muted-foreground">Your phone or email will remain private unless you give this permission. Saving the requirement does not contact vendors until matching is enabled.</span></span></label>
        </section>

        {error && <p className="rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">{error}</p>}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end"><Link className="inline-flex h-11 items-center justify-center rounded-md border border-border px-5 text-sm font-semibold hover:border-foreground" href="/">Cancel</Link><button className="inline-flex h-11 items-center justify-center gap-2 rounded-md bg-primary px-6 text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} type="submit">{isSubmitting ? <LoaderCircle className="animate-spin" size={17} /> : <Send size={17} />} Submit requirement</button></div>
      </form>
    </main>
  );
}

function validate(form: FormState, minimumDate: string) {
  if (form.categoryIds.length === 0) return "Select at least one service.";
  if (!form.eventType.trim()) return "Select an event type.";
  if (!form.eventDate) return "Select the event date.";
  if (form.eventDate < minimumDate) return "Event date cannot be in the past.";
  if (!form.location.trim()) return "Enter the event area or locality.";
  if (!form.city.trim()) return "Enter the event city.";
  if (form.pincode && form.pincode.length !== 6) return "Enter a valid 6-digit pincode.";
  const minimum = optionalNumber(form.budgetMin);
  const maximum = optionalNumber(form.budgetMax);
  if (minimum !== undefined && maximum !== undefined && minimum > maximum) return "Minimum budget cannot be greater than maximum budget.";
  return "";
}

function optionalNumber(value: string) {
  return value.trim() ? Number(value) : undefined;
}

function localToday() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

function serviceLabel(name: string) {
  return name.toLowerCase() === "makeup" ? "Bridal makeup" : name;
}
