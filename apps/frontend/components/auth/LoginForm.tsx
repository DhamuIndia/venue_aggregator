"use client";

import { Eye, EyeOff, LoaderCircle, Phone } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import type { AuthRole } from "@/features/auth/types";

function routeForRole(role: AuthRole): Route {
  return role === "ADMIN" || role === "SUPER_ADMIN" ? "/admin" : role === "VENDOR" ? "/vendor" : role === "HALL_OWNER" ? "/owner" : "/customer";
}

export function LoginForm() {
  const { login, loginDemo } = useAuth();
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (phone.replace(/\D/g, "").length < 10 || password.length < 8) {
      setError("Enter a valid phone number and an 8-character password.");
      return;
    }

    try {
      setIsSubmitting(true);
      const user = await login({ phone, password });
      const nextPath = new URLSearchParams(window.location.search).get("next");
      const destination: Route = nextPath?.startsWith("/") && !nextPath.startsWith("//")
        ? nextPath as Route
        : routeForRole(user.role);
      router.push(destination);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "We could not sign you in. Please check your details.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function useDemoAccount(role: AuthRole) {
    setError("");
    setIsSubmitting(true);
    const user = await loginDemo(role);
    router.push(routeForRole(user.role));
  }

  return (
    <form className="grid gap-5" onSubmit={handleSubmit}>
      <label className="text-sm font-medium">
        Mobile number
        <span className="relative mt-2 block">
          <Phone aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={18} />
          <input
            autoComplete="tel"
            className="h-12 w-full rounded-md border border-border pl-10 pr-3 outline-none focus:border-primary"
            inputMode="tel"
            onChange={(event) => setPhone(event.target.value)}
            placeholder="10-digit mobile number"
            value={phone}
          />
        </span>
      </label>

      <label className="text-sm font-medium">
        Password
        <span className="relative mt-2 block">
          <input
            autoComplete="current-password"
            className="h-12 w-full rounded-md border border-border px-3 pr-11 outline-none focus:border-primary"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Enter your password"
            type={showPassword ? "text" : "password"}
            value={password}
          />
          <button
            aria-label={showPassword ? "Hide password" : "Show password"}
            className="absolute right-1 top-1/2 grid size-10 -translate-y-1/2 place-items-center text-muted-foreground"
            onClick={() => setShowPassword((value) => !value)}
            type="button"
          >
            {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
        </span>
      </label>

      <div className="flex items-center justify-end text-sm"><Link className="font-medium text-primary" href="/auth/forgot-password">Forgot password?</Link></div>

      {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}

      <button className="inline-flex h-12 items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} type="submit">
        {isSubmitting && <LoaderCircle className="animate-spin" size={18} />}
        Sign in
      </button>
      <div className="grid grid-cols-2 gap-2">
        <button className="h-10 rounded-md border border-border text-sm font-medium hover:border-primary hover:text-primary" onClick={() => useDemoAccount("CUSTOMER")} type="button">Customer demo</button>
        <button className="h-10 rounded-md border border-border text-sm font-medium hover:border-primary hover:text-primary" onClick={() => useDemoAccount("HALL_OWNER")} type="button">Hall owner demo</button>
        <button className="h-10 rounded-md border border-border text-sm font-medium hover:border-primary hover:text-primary" onClick={() => useDemoAccount("VENDOR")} type="button">Vendor demo</button>
        <button className="h-10 rounded-md border border-border text-sm font-medium hover:border-primary hover:text-primary" onClick={() => useDemoAccount("ADMIN")} type="button">Admin demo</button>
      </div>
      <p className="text-center text-sm text-muted-foreground">
        New to Venue Aggregator? <Link className="font-semibold text-primary" href="/auth/register">Create account</Link>
      </p>
    </form>
  );
}
