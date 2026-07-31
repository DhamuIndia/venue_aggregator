"use client";

import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Building2,
  Check,
  ChevronDown,
  ImagePlus,
  LoaderCircle,
  MapPin,
  Phone,
  UploadCloud
} from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { createOwnerMedia } from "@/features/owner/media-client";
import { emptyOwnerOnboardingDraft, getOwnerOnboardingDraft, saveOwnerOnboardingDraft, submitOwnerOnboardingDraft, type OwnerOnboardingDraft } from "@/features/owner/onboarding-client";
import { uploadImageFile } from "@/features/uploads/upload-client";
import { formatGuestCapacityOption, formatGuestCount, guestCapacityOptions, toTitleCase } from "@/lib/display-format";
import ImageCropDialog from "@/components/shared/ImageCropDialog";
import { validateImage } from "@/lib/imageValidation";

const steps = ["Venue details", "Facilities & pricing", "Photos", "Review"];
const amenityOptions = ["Air conditioned", "Parking", "Dining hall", "Guest rooms", "Lift", "Generator", "Bridal room", "Catering kitchen"];

export function OwnerOnboarding() {
  const { accessToken, getValidAccessToken } = useAuth();
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [draftId, setDraftId] = useState<string | undefined>();
  const [isLoadingDraft, setIsLoadingDraft] = useState(true);
  const [isSavingDraft, setIsSavingDraft] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCapturingLocation, setIsCapturingLocation] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [selectedPhotoPreviews, setSelectedPhotoPreviews] = useState<Array<{ name: string; url: string }>>([]);
  const [selectedCoverIndex, setSelectedCoverIndex] = useState(0);
  const [cropDialogOpen, setCropDialogOpen] = useState(false);
  const [imageToCrop, setImageToCrop] = useState("");
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const selectedPhotoPreviewsRef = useRef(selectedPhotoPreviews);
  const [amenities, setAmenities] = useState<string[]>(emptyOwnerOnboardingDraft.amenities);
  const [form, setForm] = useState(formFromDraft(emptyOwnerOnboardingDraft));
  const capacityOptions = (() => {
    const currentCapacity = Number(form.capacity);
    if (currentCapacity > 0 && !guestCapacityOptions.includes(currentCapacity)) {
      return [currentCapacity, ...guestCapacityOptions].sort((first, second) => first - second);
    }
    return guestCapacityOptions;
  })();

  useEffect(() => {
    let isCurrent = true;

    async function loadDraft() {
      setIsLoadingDraft(true);
      setError("");

      try {
        const token = await getValidAccessToken();
        const draft = await getOwnerOnboardingDraft(token ?? accessToken);
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

  useEffect(() => {
    selectedPhotoPreviewsRef.current = selectedPhotoPreviews;
  }, [selectedPhotoPreviews]);

  useEffect(() => () => {
    selectedPhotoPreviewsRef.current.forEach((preview) => URL.revokeObjectURL(preview.url));
  }, []);

  function updateField(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setError("");
  }

  async function selectVenuePhotos(files: FileList | null) {

    const file = files?.[0];

    if (!file) return;

    const validationError = await validateImage(file);
    if (validationError) {
      setError(validationError); // or your existing error state
      return;
    }

    setPendingFile(file);
    setImageToCrop(URL.createObjectURL(file));
    setCropDialogOpen(true);
  }

  function chooseCoverPhoto(index: number) {
    setSelectedCoverIndex(index);
    setForm((current) => ({ ...current, coverImageUrl: "" }));
    setError("");
  }

  function toggleAmenity(amenity: string) {
    setError("");
    setAmenities((current) => current.includes(amenity) ? current.filter((item) => item !== amenity) : [...current, amenity]);
  }

  function captureCurrentLocation() {
    if (!navigator.geolocation) {
      setError("Location capture is not supported in this browser.");
      return;
    }

    setError("");
    setNotice("");
    setIsCapturingLocation(true);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setForm((current) => ({
          ...current,
          latitude: position.coords.latitude.toFixed(6),
          longitude: position.coords.longitude.toFixed(6)
        }));
        setNotice("Location captured. Please confirm the pin belongs to the venue.");
        setIsCapturingLocation(false);
      },
      () => {
        setError("Could not capture location. Allow browser location access or enter latitude and longitude manually.");
        setIsCapturingLocation(false);
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 }
    );
  }

  function continueStep() {
    if (step === 0) {
      if (!form.hallName.trim()) {
        setError("Enter the venue name.");
        return;
      }
      if (Number(form.capacity) < 1) {
        setError("Choose the maximum guest capacity.");
        return;
      }
      if (!form.area.trim()) {
        setError("Enter the venue area.");
        return;
      }
      if (!form.pincode.trim()) {
        setError("Enter the venue pincode.");
        return;
      }
      if (!form.addressLine.trim()) {
        setError("Enter the venue address.");
        return;
      }
      if (!hasCapturedLocation(form)) {
        setError("Capture or enter the venue location coordinates.");
        return;
      }
      if (!form.contactNumber.trim()) {
        setError("Enter the venue contact number.");
        return;
      }
      if (!form.description.trim()) {
        setError("Enter a short venue description.");
        return;
      }
    }
    if (step === 1 && amenities.length === 0) {
      setError("Select at least one facility.");
      return;
    }
    if (step === 1 && !hasAnyStartingPrice(form)) {
      setError("Enter at least one starting price.");
      return;
    }
    if (step === 2 && !form.coverImageUrl.trim() && selectedFiles.length === 0) {
      setError("Upload at least one venue photo before review.");
      return;
    }
    setError("");
    if (step === 0) setNotice("");
    setStep((current) => Math.min(current + 1, steps.length - 1));
  }

  async function saveDraft() {
    try {
      setError("");
      setNotice("");
      setIsSavingDraft(true);
      const token = await getValidAccessToken();
      if (!token) {
        setError("Your session expired. Sign in again to save this draft.");
        router.push("/auth/login?next=/owner/onboarding");
        return;
      }

      const draft = await saveOwnerOnboardingDraft(draftFromForm(form, amenities, draftId), token);
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
      const token = await getValidAccessToken();
      if (!token) {
        setError("Your session expired. Sign in again to submit this venue.");
        router.push("/auth/login?next=/owner/onboarding");
        return;
      }

      const uploadedFiles = selectedFiles.length > 0
        ? await Promise.all(selectedFiles.map((file) => uploadImageFile(file, "OWNER_HALL_MEDIA", token)))
        : [];
      const draftPayload = draftFromForm(form, amenities, draftId);
      const coverFile = uploadedFiles[selectedCoverIndex] ?? uploadedFiles[0];
      const draftWithCover = coverFile?.url ? { ...draftPayload, coverImageUrl: coverFile.url } : draftPayload;
      const savedDraft = await saveOwnerOnboardingDraft(draftWithCover, token);
      setDraftId(savedDraft.id);
      setForm(formFromDraft(savedDraft));

      if (savedDraft.id && uploadedFiles.length > 0) {
        await Promise.all(uploadedFiles.map((file, index) => createOwnerMedia(savedDraft.id as string, {
          url: file.url,
          storageKey: file.storageKey,
          fileName: file.fileName,
          caption: file.fileName,
          isCover: index === selectedCoverIndex,
          sortOrder: index
        }, token)));
      }

      const submittedDraft = await submitOwnerOnboardingDraft(savedDraft, token);
      const submittedHallId = submittedDraft.id ? `&hallId=${encodeURIComponent(submittedDraft.id)}` : "";
      router.push(`/owner?submitted=true${submittedHallId}`);
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
            <div><div className="flex items-center gap-3"><Building2 className="text-primary" size={23} /><div><h2 className="text-xl font-semibold">Venue details</h2><p className="mt-1 text-sm text-muted-foreground">Basic information customers will see.</p></div></div><div className="mt-7 grid gap-5 sm:grid-cols-2"><label className="text-sm font-medium sm:col-span-2">Venue name<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("hallName", e.target.value)} placeholder="e.g. Emerald Convention Centre" value={form.hallName} /></label><label className="text-sm font-medium">Venue type<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("venueType", e.target.value)} value={form.venueType}><option>Marriage Hall</option><option>Banquet Hall</option><option>Mini Hall</option><option>Convention Centre</option></select></label><label className="text-sm font-medium">Maximum guests<span className="relative mt-2 block"><select className="h-11 w-full appearance-none rounded-md border border-border bg-white px-3 pr-10 font-normal outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15" onChange={(e) => updateField("capacity", e.target.value)} value={form.capacity}><option value="">Choose guest capacity</option>{capacityOptions.map((option) => <option key={option} value={option}>{formatGuestCapacityOption(option)}</option>)}</select><ChevronDown aria-hidden="true" className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} /></span></label><label className="text-sm font-medium">City<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("city", e.target.value)} value={form.city}><option>Chennai</option><option>Coimbatore</option><option>Madurai</option><option>Bengaluru</option></select></label><label className="text-sm font-medium">Area<span className="relative mt-2 block"><MapPin className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} /><input className="h-11 w-full rounded-md border border-border pl-10 pr-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("area", e.target.value)} placeholder="Locality or area" value={form.area} /></span></label><label className="text-sm font-medium">Pincode<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="numeric" onChange={(e) => updateField("pincode", e.target.value)} placeholder="6-digit pincode" value={form.pincode} /></label><label className="text-sm font-medium sm:col-span-2">Address line<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(e) => updateField("addressLine", e.target.value)} placeholder="Door number, street, landmark" value={form.addressLine} /></label><div className="rounded-lg border border-border bg-background p-4 sm:col-span-2"><div className="flex flex-wrap items-center justify-between gap-3"><div><h3 className="text-sm font-semibold">Venue map location</h3><p className="mt-1 text-xs text-muted-foreground">Capture this while standing at the hall entrance.</p></div><button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={isCapturingLocation} onClick={captureCurrentLocation} type="button">{isCapturingLocation ? <LoaderCircle className="animate-spin" size={16} /> : <MapPin size={16} />} Use current location</button></div><div className="mt-4 grid gap-4 sm:grid-cols-2"><label className="text-sm font-medium">Latitude<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="decimal" onChange={(e) => updateField("latitude", e.target.value)} placeholder="13.082680" value={form.latitude} /></label><label className="text-sm font-medium">Longitude<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="decimal" onChange={(e) => updateField("longitude", e.target.value)} placeholder="80.270721" value={form.longitude} /></label></div>{hasCapturedLocation(form) && <a className="mt-3 inline-flex text-sm font-semibold text-primary" href={googleMapsUrl(form.latitude, form.longitude)} rel="noreferrer" target="_blank">Check pin in Google Maps</a>}</div><label className="text-sm font-medium">Contact number<span className="relative mt-2 block"><Phone className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} /><input className="h-11 w-full rounded-md border border-border pl-10 pr-3 font-normal outline-none focus:border-primary" inputMode="tel" onChange={(e) => updateField("contactNumber", e.target.value)} placeholder="Owner or venue phone" value={form.contactNumber} /></span></label><label className="text-sm font-medium">WhatsApp number<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="tel" onChange={(e) => updateField("whatsappNumber", e.target.value)} placeholder="Optional" value={form.whatsappNumber} /></label><label className="text-sm font-medium sm:col-span-2">Description<textarea className="mt-2 min-h-28 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" maxLength={800} onChange={(e) => updateField("description", e.target.value)} placeholder="Describe the venue, event spaces, and what makes it suitable for celebrations." value={form.description} /></label></div></div>
          )}

          {step === 1 && (
            <div><div><h2 className="text-xl font-semibold">Facilities and pricing</h2><p className="mt-1 text-sm text-muted-foreground">Select available facilities and starting prices.</p></div><fieldset className="mt-7"><legend className="text-sm font-medium">Amenities</legend><div className="mt-3 grid gap-2 sm:grid-cols-2">{amenityOptions.map((amenity) => <label className={`flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-sm ${amenities.includes(amenity) ? "border-primary bg-emerald-50" : "border-border"}`} key={amenity}><input checked={amenities.includes(amenity)} className="size-4 accent-[hsl(var(--primary))]" onChange={() => toggleAmenity(amenity)} type="checkbox" />{amenity}</label>)}</div></fieldset><div className="mt-7"><h3 className="text-sm font-medium">Starting price by slot</h3><div className="mt-3 grid gap-4 sm:grid-cols-3">{[{ field: "morningPrice" as const, label: "Morning" }, { field: "eveningPrice" as const, label: "Evening" }, { field: "fullDayPrice" as const, label: "Full day" }].map((price) => <label className="text-sm font-medium" key={price.field}>{price.label}<span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="0" onChange={(e) => updateField(price.field, e.target.value)} placeholder="0" type="number" value={form[price.field]} /></span></label>)}</div></div></div>
          )}

          {step === 2 && (
            <div>
              <div className="flex items-center gap-3">
                <ImagePlus className="text-primary" size={23} />
                <div>
                  <h2 className="text-xl font-semibold">Venue photos</h2>
                  <p className="mt-1 text-sm text-muted-foreground">Add clear images of the hall, dining, entrance, and facilities.</p>
                </div>
              </div>

              <label className="mt-7 flex min-h-40 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-border bg-background px-5 text-center hover:border-primary">
                <UploadCloud className="text-primary" size={28} />
                <span className="mt-3 text-sm font-semibold">Choose venue photos</span>
                <span className="mt-1 text-xs text-muted-foreground">JPG, PNG or WebP, up to 10 files. Choose the cover after upload.</span>
                <input accept="image/jpeg,image/png,image/webp" className="sr-only" multiple onChange={(event) => { selectVenuePhotos(event.target.files); event.target.value = ""; }} type="file" />
              </label>

              {selectedFiles.length > 0 && <p className="mt-3 text-sm text-emerald-700">{selectedFiles.length} photo{selectedFiles.length === 1 ? "" : "s"} selected</p>}

              {selectedPhotoPreviews.length > 0 ? (
                <div className="mt-7">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-medium">Selected gallery</h3>
                    <span className="text-xs text-muted-foreground">Pick one cover photo</span>
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
                    {selectedPhotoPreviews.map((photo, index) => (
                      <article className={`overflow-hidden rounded-lg border bg-white ${selectedCoverIndex === index ? "border-primary ring-2 ring-primary/15" : "border-border"}`} key={photo.url}>
                        <div className="relative aspect-[4/3] bg-muted">
                          <Image alt={photo.name} className="object-cover" fill sizes="240px" src={photo.url} unoptimized />
                          {selectedCoverIndex === index && <span className="absolute left-2 top-2 rounded-full bg-white px-2 py-1 text-xs font-medium text-primary">Cover</span>}
                        </div>
                        <button aria-pressed={selectedCoverIndex === index} className={`h-10 w-full text-sm font-semibold ${selectedCoverIndex === index ? "bg-emerald-50 text-primary" : "text-muted-foreground hover:bg-muted/60 hover:text-foreground"}`} onClick={() => chooseCoverPhoto(index)} type="button">
                          {selectedCoverIndex === index ? "Selected cover" : "Set as cover"}
                        </button>
                      </article>
                    ))}
                  </div>
                </div>
              ) : form.coverImageUrl ? (
                <div className="relative mt-7 aspect-[4/3] overflow-hidden rounded-lg bg-muted sm:max-w-md">
                  <Image alt="Venue cover preview" className="object-cover" fill sizes="420px" src={form.coverImageUrl} unoptimized />
                </div>
              ) : null}
            </div>
          )}

          {step === 3 && (
            <div><div className="flex items-center gap-3"><BadgeCheck className="text-primary" size={24} /><div><h2 className="text-xl font-semibold">Review your listing</h2><p className="mt-1 text-sm text-muted-foreground">Admin approval is required before publication.</p></div></div><dl className="mt-7 divide-y divide-border rounded-lg border border-border">{[{ label: "Venue", value: form.hallName ? toTitleCase(form.hallName) : "Not provided" }, { label: "Type", value: form.venueType }, { label: "Location", value: `${form.area ? toTitleCase(form.area) : "Area"}, ${toTitleCase(form.city)}${form.pincode ? ` - ${form.pincode}` : ""}` }, { label: "Address", value: form.addressLine ? toTitleCase(form.addressLine) : "Not provided" }, { label: "Map pin", value: hasCapturedLocation(form) ? `${form.latitude}, ${form.longitude}` : "Not captured" }, { label: "Contact", value: form.contactNumber || "Not provided" }, { label: "Capacity", value: `${formatGuestCount(form.capacity || 0)} guests` }, { label: "Amenities", value: `${amenities.length} selected` }, { label: "Full-day price", value: form.fullDayPrice ? `INR ${new Intl.NumberFormat("en-IN").format(Number(form.fullDayPrice))}` : "Not provided" }, { label: "Cover image", value: form.coverImageUrl || selectedFiles.length > 0 ? "Added" : "Not provided" }, { label: "Photos", value: selectedFiles.length > 0 ? `${selectedFiles.length} selected` : "Add from the media tab" }].map((item) => <div className="grid gap-1 px-4 py-3 sm:grid-cols-[170px_1fr]" key={item.label}><dt className="text-sm text-muted-foreground">{item.label}</dt><dd className="text-sm font-medium">{item.value}</dd></div>)}</dl><label className="mt-6 flex items-start gap-3 text-sm text-muted-foreground"><input checked={confirmed} className="mt-1 size-4 accent-[hsl(var(--primary))]" onChange={(event) => { setConfirmed(event.target.checked); setError(""); }} required type="checkbox" /><span>I confirm that the venue information is accurate and I am authorized to manage this listing.</span></label></div>
          )}

          {error && <p className="mt-6 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
          <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5"><button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-medium disabled:opacity-40" disabled={step === 0 || isSavingDraft || isSubmitting} onClick={() => setStep((current) => current - 1)}><ArrowLeft size={16} /> Back</button><div className="flex flex-wrap gap-2"><button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50" disabled={isLoadingDraft || isSavingDraft || isSubmitting} onClick={saveDraft}>{isSavingDraft && <LoaderCircle className="animate-spin" size={16} />} Save draft</button>{step < steps.length - 1 ? <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white disabled:opacity-50" disabled={isLoadingDraft || isSubmitting} onClick={continueStep}>Continue <ArrowRight size={16} /></button> : <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50" disabled={!confirmed || isSubmitting} onClick={submitListing}>{isSubmitting ? <LoaderCircle className="animate-spin" size={17} /> : <BadgeCheck size={17} />} Submit for approval</button>}</div></div>
        </section>
      </div>
      <ImageCropDialog
        open={cropDialogOpen}
        image={imageToCrop}
        onCancel={() => {
          setCropDialogOpen(false);
          setPendingFile(null);

          URL.revokeObjectURL(imageToCrop);

          setImageToCrop("");
        }}
        onSave={(blob) => {

          if (!pendingFile) return;

          const croppedFile = new File(
            [blob],
            pendingFile.name,
            {
              type: "image/jpeg",
            }
          );

          setSelectedFiles((current) => [...current, croppedFile]);

          const previewUrl = URL.createObjectURL(croppedFile);

          setSelectedPhotoPreviews((current) => [
            ...current,
            {
              name: croppedFile.name,
              url: previewUrl,
            },
          ]);

          if (selectedFiles.length === 0) {
            setSelectedCoverIndex(0);
          }

          setCropDialogOpen(false);
          setPendingFile(null);
          URL.revokeObjectURL(imageToCrop);
          setImageToCrop("");
        }}
      />
    </main>
  );
}

function formFromDraft(draft: OwnerOnboardingDraft) {
  return {
    hallName: draft.hallName,
    venueType: draft.venueType,
    description: draft.description,
    addressLine: draft.addressLine,
    city: draft.city,
    area: draft.area,
    pincode: draft.pincode,
    latitude: draft.latitude ? String(draft.latitude) : "",
    longitude: draft.longitude ? String(draft.longitude) : "",
    contactNumber: draft.contactNumber,
    whatsappNumber: draft.whatsappNumber,
    coverImageUrl: draft.coverImageUrl,
    capacity: draft.capacity ? String(draft.capacity) : "",
    morningPrice: draft.morningPrice ? String(draft.morningPrice) : "",
    eveningPrice: draft.eveningPrice ? String(draft.eveningPrice) : "",
    fullDayPrice: draft.fullDayPrice ? String(draft.fullDayPrice) : ""
  };
}

function draftFromForm(form: ReturnType<typeof formFromDraft>, amenities: string[], id?: string): OwnerOnboardingDraft {
  return {
    id,
    hallName: toTitleCase(form.hallName),
    venueType: form.venueType,
    description: form.description.trim(),
    addressLine: toTitleCase(form.addressLine),
    city: toTitleCase(form.city),
    area: toTitleCase(form.area),
    pincode: form.pincode.trim(),
    latitude: parseCoordinate(form.latitude),
    longitude: parseCoordinate(form.longitude),
    contactNumber: form.contactNumber.trim(),
    whatsappNumber: form.whatsappNumber.trim(),
    coverImageUrl: form.coverImageUrl.trim(),
    capacity: Number(form.capacity) || 0,
    morningPrice: Number(form.morningPrice) || 0,
    eveningPrice: Number(form.eveningPrice) || 0,
    fullDayPrice: Number(form.fullDayPrice) || 0,
    amenities,
    status: "DRAFT"
  };
}

function hasAnyStartingPrice(form: ReturnType<typeof formFromDraft>) {
  return [form.morningPrice, form.eveningPrice, form.fullDayPrice].some((price) => Number(price) > 0);
}

function hasCapturedLocation(form: ReturnType<typeof formFromDraft>) {
  return parseCoordinate(form.latitude) !== undefined && parseCoordinate(form.longitude) !== undefined;
}

function googleMapsUrl(latitude: string, longitude: string) {
  return `https://www.google.com/maps?q=${encodeURIComponent(`${latitude},${longitude}`)}`;
}

function parseCoordinate(value: string) {
  if (!value.trim()) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}
