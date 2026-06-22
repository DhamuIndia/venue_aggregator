"use client";

import { ArrowLeft, CheckCircle2 } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";

export function ForgotPasswordForm() {
  const [phone, setPhone] = useState("");
  const [sent, setSent] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (phone.replace(/\D/g, "").length >= 10) setSent(true);
  }

  if (sent) {
    return (
      <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-5">
        <CheckCircle2 className="text-emerald-700" size={26} />
        <h2 className="mt-3 font-semibold">Reset instructions sent</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">We sent password reset instructions to {phone}.</p>
        <Link className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-primary" href="/auth/login"><ArrowLeft size={16} /> Back to sign in</Link>
      </div>
    );
  }

  return (
    <form className="grid gap-5" onSubmit={handleSubmit}>
      <label className="text-sm font-medium">Mobile number<input className="mt-2 h-12 w-full rounded-md border border-border px-3 outline-none focus:border-primary" inputMode="tel" onChange={(e) => setPhone(e.target.value)} placeholder="10-digit mobile number" required value={phone} /></label>
      <button className="h-12 rounded-md bg-primary text-sm font-semibold text-white" type="submit">Send reset instructions</button>
      <Link className="inline-flex items-center justify-center gap-2 text-sm font-semibold text-primary" href="/auth/login"><ArrowLeft size={16} /> Back to sign in</Link>
    </form>
  );
}
