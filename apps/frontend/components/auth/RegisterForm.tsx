"use client";

import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import type { AuthRole } from "@/features/auth/types";

export function RegisterForm() {
  const { register } = useAuth();
  const router = useRouter();
  const [form, setForm] = useState({ fullName: "", phone: "", email: "", password: "", role: "CUSTOMER" as AuthRole });
  const [accepted, setAccepted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!form.fullName.trim() || form.phone.replace(/\D/g, "").length < 10 || form.password.length < 8 || !accepted) {
      setError("Complete the required fields, use an 8-character password, and accept the terms.");
      return;
    }

    try {
      setIsSubmitting(true);
      await register(form);
      router.push(form.role === "VENDOR" ? "/vendor/onboarding" : form.role === "HALL_OWNER" ? "/owner/onboarding" : "/customer");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "We could not create your account. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function updateField(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <form className="grid gap-4" onSubmit={handleSubmit}>
      <fieldset><legend className="text-sm font-medium">Account type</legend><div className="mt-2 grid gap-1 rounded-md bg-muted p-1 sm:grid-cols-3"><button aria-pressed={form.role === "CUSTOMER"} className={`min-h-10 rounded-md px-3 text-sm font-medium ${form.role === "CUSTOMER" ? "bg-white shadow-sm" : "text-muted-foreground"}`} onClick={() => updateField("role", "CUSTOMER")} type="button">Planning an event</button><button aria-pressed={form.role === "HALL_OWNER"} className={`min-h-10 rounded-md px-3 text-sm font-medium ${form.role === "HALL_OWNER" ? "bg-white shadow-sm" : "text-muted-foreground"}`} onClick={() => updateField("role", "HALL_OWNER")} type="button">Managing a venue</button><button aria-pressed={form.role === "VENDOR"} className={`min-h-10 rounded-md px-3 text-sm font-medium ${form.role === "VENDOR" ? "bg-white shadow-sm" : "text-muted-foreground"}`} onClick={() => updateField("role", "VENDOR")} type="button">Offering services</button></div></fieldset>
      <label className="text-sm font-medium">Full name<input autoComplete="name" className="mt-2 h-11 w-full rounded-md border border-border px-3 outline-none focus:border-primary" onChange={(e) => updateField("fullName", e.target.value)} placeholder="Your full name" value={form.fullName} /></label>
      <label className="text-sm font-medium">Mobile number<input autoComplete="tel" className="mt-2 h-11 w-full rounded-md border border-border px-3 outline-none focus:border-primary" inputMode="tel" onChange={(e) => updateField("phone", e.target.value)} placeholder="10-digit mobile number" value={form.phone} /></label>
      <label className="text-sm font-medium">Email <span className="font-normal text-muted-foreground">(optional)</span><input autoComplete="email" className="mt-2 h-11 w-full rounded-md border border-border px-3 outline-none focus:border-primary" onChange={(e) => updateField("email", e.target.value)} placeholder="you@example.com" type="email" value={form.email} /></label>
      <label className="text-sm font-medium">Password<span className="relative mt-2 block"><input autoComplete="new-password" className="h-11 w-full rounded-md border border-border px-3 pr-11 outline-none focus:border-primary" onChange={(e) => updateField("password", e.target.value)} placeholder="At least 8 characters" type={showPassword ? "text" : "password"} value={form.password} /><button aria-label={showPassword ? "Hide password" : "Show password"} className="absolute right-1 top-1/2 grid size-9 -translate-y-1/2 place-items-center text-muted-foreground" onClick={() => setShowPassword((value) => !value)} type="button">{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button></span></label>
      <label className="flex items-start gap-3 text-sm text-muted-foreground"><input checked={accepted} className="mt-1 size-4 accent-[hsl(var(--primary))]" onChange={(e) => setAccepted(e.target.checked)} type="checkbox" /><span>I agree to the terms and privacy policy.</span></label>
      {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
      <button className="inline-flex h-12 items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} type="submit">{isSubmitting && <LoaderCircle className="animate-spin" size={18} />}Create account</button>
      <p className="text-center text-sm text-muted-foreground">Already registered? <Link className="font-semibold text-primary" href="/auth/login">Sign in</Link></p>
    </form>
  );
}
