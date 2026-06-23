"use client";

import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Building2,
  Check,
  ImagePlus,
  LoaderCircle,
  MapPin,
  UploadCloud,
  UsersRound
} from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ownerHall } from "@/features/owner/mock-data";
import { emptyOwnerOnboardingDraft, getOwnerOnboardingDraft, saveOwnerOnboardingDraft, submitOwnerOnboardingDraft, type OwnerOnboardingDraft } from "@/features/owner/onboarding-client";

const steps = ["Venue details", "Facilities & pricing", "Photos", "Review"];
const amenityOptions = ["Air conditioned", "Parking", "Dining hall", "Guest rooms", "Lift", "Generator", "Bridal room", "Catering kitchen"];

export function OwnerOnboarding() {
  const { accessToken } = useAuth();
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [draftId, setDraftId] = useState<string | undefined>();
  const [isLoadingDraft, setIsLoadingDraft] = useState(true);
  const [isSavingDraft, setIsSavingDraft] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [fileNames, setFileNames] = useState<string[]>([]);
  const [amenities, setAmenities] = useState<string[]>(emptyOwnerOnboardingDraft.amenities);
  const [form, setForm] = useState(formFromDraft(emptyOwnerOnboardingDraft));

  useEffect(() => {
    let isCurrent = true;

    async function loadDraft() {
      setIsLoadingDraft(true);
      setError("");

      try {
        const draft = await getOwnerOnboardingDraft(accessToken);
        if (!isCurrent) return;
        setDraftId(draft.id);
        setForm(formFromDraft(draft));
        setAmenities(draft.amenities.length ? draft.amenities : emptyOwnerOnboardingDraft.amenities);
        if (draft.status === "PENDING_APPROVAL") setNotice("This venue listing is already submitted for admin approval.");
      } catch {
        if (!isCurrent) return;
        setError("Could not load saved venue draft.");
      } finally {
        if (isCurrent) setIsLoadingDraft(false);
      }
    }

    loadDraft();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  function updateField(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setError("");
  }

  function toggleAmenity(amenity: string) {
    setError("");
    setAmenities((current) => current.includes(amenity) ? current.filter((item) => item !== amenity) : [...current, amenity]);
  }

  function continueStep() {
    if (step === 0 && (!form.hallName.trim() || !form.area.trim() || Number(form.capacity) < 1)) {
      setError("Enter the venue name, area, and guest capacity.");
      return;
    }
    if (step === 1 && (!form.fullDayPrice || amenities.length === 0)) {
      setError("Add at least one facility and a full-day starting price.");
      return;
    }
    setError("");
    setStep((current) => Math.min(current + 1, steps.length - 1));
  }

  async function saveDraft() {
    try {
      setError("");
      setNotice("");
      setIsSavingDraft(true);
      const draft = await saveOwnerOnboardingDraft(draftFromForm(form, amenities, draftId), accessToken);
      setDraftId(draft.id);
      setNotice("Draft saved.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not save venue draft.");
    } finally {
      setIsSavingDraft(false);
    }
  }

  async function submitListing() {
    if (!confirmed) {
      setError("Confirm that you are authorized to manage this venue.");
      return;
    }
    try {
      setError("");
      setNotice("");
      setIsSubmitting(true);
      await submitOwnerOnboardingDraft(draftFromForm(form, amenities, draftId), accessToken);
      router.push("/owner?submitted=true");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not submit venue for approval.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="mx-auto w-full max-w-6xl px-4 py-7 sm:px-6 sm:py-10">
      <div className="flex flex-wrap items-start justify-between gap-5">
        <div><p className="text-sm font-semibold text-primary">Hall owner onboarding</p><h1 className="mt-2 text-3xl font-semibold">List your venue</h1><p className="mt-2 text-sm text-muted-foreground">Complete the profile for admin review.</p></div>
        <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium" onClick={() => router.push("/owner")}><ArrowLeft size={17} /> Owner dashboard</button>
      </div>

      <div className="mt-8 grid gap-8 lg:grid-cols-[240px_minmax(0,1fr)]">
        <nav className="h-fit lg:sticky lg:top-24" aria-label="Onboarding progress">
          <ol className="grid grid-cols-4 gap-2 lg:grid-cols-1">
            {steps.map((label, index) => (
              <li className={`flex min-w-0 items-center gap-3 rounded-md p-2 text-sm ${index === step ? "bg-emerald-50 font-semibold text-primary" : index < step ? "text-emerald-700" : "text-muted-foreground"}`} key={label}>
                <span className={`grid size-7 shrink-0 place-items-center rounded-full border text-xs ${index <= step ? "border-primary bg-primary text-white" : "border-border bg-white"}`}>{index < step ? <Check size={14} /> : index + 1}</span>
                <span className="hidden truncate lg:block">{label}</span>
              </li>
            ))}
          </ol>
        </nav>

        <section className="rounded-lg border border-border bg-white p-5 shadow-sm sm:p-7">
          {isLoadingDraft && <div className="mb-6 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full w-1/2 animate-pulse rounded-full bg-primary" /></div>}
          {notice && <p className="mb-6 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">{notice}</p>}
          {step === 0 && (
            <div><div className="flex items-center gap-3"><Building2 className="text-primary" size={23} /><div><h2 className="text-xl font-semibold">Venue details</h2><p className="mt-1 text-sm text-muted-foreground">Basic information customers will see.</p></div></div><div className="mt-7 grid gap-5 sm:grid-cols-2"><label className="text-sm font-medium sm:col-span-2">Venue name<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("hallName", e.target.value)} placeholder="e.g. Emerald Convention Centre" value={form.hallName} /></label><label className="text-sm font-medium">Venue type<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("venueType", e.target.value)} value={form.venueType}><option>Marriage Hall</option><option>Banquet Hall</option><option>Mini Hall</option><option>Convention Centre</option></select></label><label className="text-sm font-medium">Maximum guests<span className="relative mt-2 block"><UsersRound className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} /><input className="h-11 w-full rounded-md border border-border pl-10 pr-3 font-normal outline-none focus:border-primary" min="1" onChange={(e) => updateField("capacity", e.target.value)} placeholder="Guest capacity" type="number" value={form.capacity} /></span></label><label className="text-sm font-medium">City<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("city", e.target.value)} value={form.city}><option>Chennai</option><option>Coimbatore</option><option>Madurai</option><option>Bengaluru</option></select></label><label className="text-sm font-medium">Area<span className="relative mt-2 block"><MapPin className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} /><input className="h-11 w-full rounded-md border border-border pl-10 pr-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("area", e.target.value)} placeholder="Locality or area" value={form.area} /></span></label><label className="text-sm font-medium">Pincode<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="numeric" onChange={(e) => updateField("pincode", e.target.value)} placeholder="6-digit pincode" value={form.pincode} /></label><label className="text-sm font-medium sm:col-span-2">Description<textarea className="mt-2 min-h-28 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" maxLength={800} onChange={(e) => updateField("description", e.target.value)} placeholder="Describe the venue, event spaces, and what makes it suitable for celebrations." value={form.description} /></label></div></div>
          )}

          {step === 1 && (
            <div><div><h2 className="text-xl font-semibold">Facilities and pricing</h2><p className="mt-1 text-sm text-muted-foreground">Select available facilities and starting prices.</p></div><fieldset className="mt-7"><legend className="text-sm font-medium">Amenities</legend><div className="mt-3 grid gap-2 sm:grid-cols-2">{amenityOptions.map((amenity) => <label className={`flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-sm ${amenities.includes(amenity) ? "border-primary bg-emerald-50" : "border-border"}`} key={amenity}><input checked={amenities.includes(amenity)} className="size-4 accent-[hsl(var(--primary))]" onChange={() => toggleAmenity(amenity)} type="checkbox" />{amenity}</label>)}</div></fieldset><div className="mt-7"><h3 className="text-sm font-medium">Starting price by slot</h3><div className="mt-3 grid gap-4 sm:grid-cols-3">{[{ field: "morningPrice" as const, label: "Morning" }, { field: "eveningPrice" as const, label: "Evening" }, { field: "fullDayPrice" as const, label: "Full day" }].map((price) => <label className="text-sm font-medium" key={price.field}>{price.label}<span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="0" onChange={(e) => updateField(price.field, e.target.value)} placeholder="0" type="number" value={form[price.field]} /></span></label>)}</div></div></div>
          )}

          {step === 2 && (
            <div><div className="flex items-center gap-3"><ImagePlus className="text-primary" size={23} /><div><h2 className="text-xl font-semibold">Venue photos</h2><p className="mt-1 text-sm text-muted-foreground">Add clear images of the hall, dining, entrance, and facilities.</p></div></div><label className="mt-7 flex min-h-40 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-border bg-background px-5 text-center hover:border-primary"><UploadCloud className="text-primary" size={28} /><span className="mt-3 text-sm font-semibold">Choose venue photos</span><span className="mt-1 text-xs text-muted-foreground">JPG, PNG or WebP, up to 10 files</span><input accept="image/jpeg,image/png,image/webp" className="sr-only" multiple onChange={(event) => setFileNames(Array.from(event.target.files ?? []).map((file) => file.name))} type="file" /></label>{fileNames.length > 0 && <p className="mt-3 text-sm text-emerald-700">{fileNames.length} photo{fileNames.length === 1 ? "" : "s"} selected</p>}<div className="mt-7"><div className="flex items-center justify-between"><h3 className="text-sm font-medium">Example gallery order</h3><span className="text-xs text-muted-foreground">First image is the cover</span></div><div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">{[ownerHall.imageUrl, ...ownerHall.galleryUrls].map((image, index) => <div className="relative aspect-[4/3] overflow-hidden rounded-lg bg-muted" key={`${image}-${index}`}><Image alt={`Venue example ${index + 1}`} className="object-cover" fill sizes="240px" src={image} />{index === 0 && <span className="absolute left-2 top-2 rounded-full bg-white px-2 py-1 text-xs font-medium text-primary">Cover</span>}</div>)}</div></div></div>
          )}

          {step === 3 && (
            <div><div className="flex items-center gap-3"><BadgeCheck className="text-primary" size={24} /><div><h2 className="text-xl font-semibold">Review your listing</h2><p className="mt-1 text-sm text-muted-foreground">Admin approval is required before publication.</p></div></div><dl className="mt-7 divide-y divide-border rounded-lg border border-border">{[{ label: "Venue", value: form.hallName || "Not provided" }, { label: "Type", value: form.venueType }, { label: "Location", value: `${form.area || "Area"}, ${form.city}` }, { label: "Capacity", value: `${form.capacity || "0"} guests` }, { label: "Amenities", value: `${amenities.length} selected` }, { label: "Full-day price", value: form.fullDayPrice ? `INR ${new Intl.NumberFormat("en-IN").format(Number(form.fullDayPrice))}` : "Not provided" }, { label: "Photos", value: fileNames.length > 0 ? `${fileNames.length} selected` : "Add after submission" }].map((item) => <div className="grid gap-1 px-4 py-3 sm:grid-cols-[170px_1fr]" key={item.label}><dt className="text-sm text-muted-foreground">{item.label}</dt><dd className="text-sm font-medium">{item.value}</dd></div>)}</dl><label className="mt-6 flex items-start gap-3 text-sm text-muted-foreground"><input checked={confirmed} className="mt-1 size-4 accent-[hsl(var(--primary))]" onChange={(event) => { setConfirmed(event.target.checked); setError(""); }} required type="checkbox" /><span>I confirm that the venue information is accurate and I am authorized to manage this listing.</span></label></div>
          )}

          {error && <p className="mt-6 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
          <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5"><button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-medium disabled:opacity-40" disabled={step === 0 || isSavingDraft || isSubmitting} onClick={() => setStep((current) => current - 1)}><ArrowLeft size={16} /> Back</button><div className="flex flex-wrap gap-2"><button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50" disabled={isLoadingDraft || isSavingDraft || isSubmitting} onClick={saveDraft}>{isSavingDraft && <LoaderCircle className="animate-spin" size={16} />} Save draft</button>{step < steps.length - 1 ? <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white disabled:opacity-50" disabled={isLoadingDraft || isSubmitting} onClick={continueStep}>Continue <ArrowRight size={16} /></button> : <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50" disabled={!confirmed || isSubmitting} onClick={submitListing}>{isSubmitting ? <LoaderCircle className="animate-spin" size={17} /> : <BadgeCheck size={17} />} Submit for approval</button>}</div></div>
        </section>
      </div>
    </main>
  );
}

function formFromDraft(draft: OwnerOnboardingDraft) {
  return {
    hallName: draft.hallName,
    venueType: draft.venueType,
    description: draft.description,
    city: draft.city,
    area: draft.area,
    pincode: draft.pincode,
    capacity: draft.capacity ? String(draft.capacity) : "",
    morningPrice: draft.morningPrice ? String(draft.morningPrice) : "",
    eveningPrice: draft.eveningPrice ? String(draft.eveningPrice) : "",
    fullDayPrice: draft.fullDayPrice ? String(draft.fullDayPrice) : ""
  };
}

function draftFromForm(form: ReturnType<typeof formFromDraft>, amenities: string[], id?: string): OwnerOnboardingDraft {
  return {
    id,
    hallName: form.hallName.trim(),
    venueType: form.venueType,
    description: form.description.trim(),
    city: form.city,
    area: form.area.trim(),
    pincode: form.pincode.trim(),
    capacity: Number(form.capacity) || 0,
    morningPrice: Number(form.morningPrice) || 0,
    eveningPrice: Number(form.eveningPrice) || 0,
    fullDayPrice: Number(form.fullDayPrice) || 0,
    amenities,
    status: "DRAFT"
  };
}
