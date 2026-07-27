"use client";

import {
  AlertTriangle,
  BadgeCheck,
  BellOff,
  Check,
  LoaderCircle,
  MessageCircleMore,
  PauseCircle,
  RotateCcw,
  ShieldCheck
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import {
  getVendorNotificationPreference,
  updateVendorNotificationPreference,
  type VendorNotificationPreference
} from "@/features/notifications/vendor-notification-preference-client";

type PreferenceStatus = {
  label: string;
  description: string;
  className: string;
};

function comparablePhone(value: string | null) {
  const compact = (value ?? "").trim().replace(/[\s()-]/g, "");
  if (/^[6-9]\d{9}$/.test(compact)) return `+91${compact}`;
  if (/^0[6-9]\d{9}$/.test(compact)) return `+91${compact.slice(1)}`;
  if (/^91[6-9]\d{9}$/.test(compact)) return `+${compact}`;
  return compact;
}

function preferenceStatus(preference: VendorNotificationPreference): PreferenceStatus {
  if (preference.whatsAppLeadNotificationsEnabled && preference.whatsAppLeadNotificationsPaused) {
    return {
      label: "Paused",
      description: "Your consent is retained, but new WhatsApp lead alerts are paused.",
      className: "bg-amber-50 text-amber-800"
    };
  }
  if (preference.whatsAppLeadNotificationsEnabled) {
    return {
      label: "Subscribed",
      description: "Your preference is active. Delivery still depends on the controlled rollout.",
      className: "bg-emerald-50 text-emerald-800"
    };
  }
  if (preference.whatsAppOptedOutAt) {
    return {
      label: "Opted out",
      description: "WhatsApp lead alerts are turned off.",
      className: "bg-slate-100 text-slate-700"
    };
  }
  return {
    label: "Not subscribed",
    description: "Turn on alerts below if you want to receive matching leads on WhatsApp.",
    className: "bg-slate-100 text-slate-700"
  };
}

function formatDate(value: string | null) {
  if (!value) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(parsed);
}

export function VendorWhatsAppNotificationSettings() {
  const { accessToken } = useAuth();
  const [preference, setPreference] = useState<VendorNotificationPreference | null>(null);
  const [whatsAppNumber, setWhatsAppNumber] = useState("");
  const [paused, setPaused] = useState(false);
  const [consentConfirmed, setConsentConfirmed] = useState(false);
  const [confirmingOptOut, setConfirmingOptOut] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const applyPreference = useCallback((nextPreference: VendorNotificationPreference) => {
    setPreference(nextPreference);
    setWhatsAppNumber(nextPreference.whatsAppNumber ?? "");
    setPaused(nextPreference.whatsAppLeadNotificationsPaused);
    setConsentConfirmed(false);
    setConfirmingOptOut(false);
  }, []);

  const loadPreference = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      applyPreference(await getVendorNotificationPreference(accessToken));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not load WhatsApp notification settings.");
    } finally {
      setIsLoading(false);
    }
  }, [accessToken, applyPreference]);

  useEffect(() => {
    void loadPreference();
  }, [loadPreference]);

  const numberChanged = useMemo(
    () => comparablePhone(whatsAppNumber) !== comparablePhone(preference?.whatsAppNumber ?? null),
    [preference?.whatsAppNumber, whatsAppNumber]
  );
  const needsConsent = Boolean(
    preference &&
    (!preference.whatsAppLeadNotificationsEnabled ||
      !preference.whatsAppConsentedAt ||
      numberChanged)
  );
  const hasPreferenceChanges = Boolean(
    preference &&
    (numberChanged ||
      paused !== preference.whatsAppLeadNotificationsPaused ||
      (preference.whatsAppLeadNotificationsEnabled && !preference.whatsAppConsentedAt))
  );
  const status = preference ? preferenceStatus(preference) : null;

  async function saveEnabledPreference(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!preference) return;

    setError("");
    setNotice("");
    setIsSaving(true);
    try {
      const updated = await updateVendorNotificationPreference(
        {
          whatsAppLeadNotificationsEnabled: true,
          whatsAppLeadNotificationsPaused: preference.whatsAppLeadNotificationsEnabled ? paused : false,
          whatsAppNumber: whatsAppNumber.trim(),
          consentConfirmed: needsConsent && consentConfirmed
        },
        accessToken
      );
      applyPreference(updated);
      setNotice(
        updated.whatsAppLeadNotificationsPaused
          ? "WhatsApp lead notifications are paused."
          : "WhatsApp lead notification preference saved."
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not save WhatsApp notification settings.");
    } finally {
      setIsSaving(false);
    }
  }

  async function optOut() {
    if (!preference) return;

    setError("");
    setNotice("");
    setIsSaving(true);
    try {
      const updated = await updateVendorNotificationPreference(
        {
          whatsAppLeadNotificationsEnabled: false,
          whatsAppLeadNotificationsPaused: false,
          whatsAppNumber: whatsAppNumber.trim(),
          consentConfirmed: false
        },
        accessToken
      );
      applyPreference(updated);
      setNotice("You have opted out of WhatsApp lead notifications.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not turn off WhatsApp notifications.");
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <section className="py-7" aria-label="Loading WhatsApp notification settings">
        <div className="h-80 animate-pulse rounded-lg border border-border bg-white" />
      </section>
    );
  }

  if (!preference) {
    return (
      <section className="py-7">
        <div className="rounded-lg border border-rose-200 bg-white p-6">
          <AlertTriangle className="text-rose-700" size={24} />
          <h2 className="mt-4 text-xl font-semibold">WhatsApp settings could not be loaded</h2>
          <p className="mt-2 text-sm text-rose-700" role="alert">{error || "Please try again."}</p>
          <button className="mt-5 inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold" onClick={() => void loadPreference()} type="button">
            <RotateCcw size={16} /> Try again
          </button>
        </div>
      </section>
    );
  }

  const consentDate = formatDate(preference.whatsAppConsentedAt);
  const pauseDate = formatDate(preference.whatsAppPausedAt);
  const optOutDate = formatDate(preference.whatsAppOptedOutAt);

  return (
    <section className="py-7">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">WhatsApp lead notifications</h2>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
            Choose whether VenueMart may send matching customer requirements to your WhatsApp number.
          </p>
        </div>
        {status && (
          <div className="text-right">
            <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${status.className}`}>{status.label}</span>
            <p className="mt-2 max-w-sm text-xs leading-5 text-muted-foreground">{status.description}</p>
          </div>
        )}
      </div>

      <div className="mt-5 flex items-start gap-3 rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
        <ShieldCheck className="mt-0.5 shrink-0" size={20} />
        <p className="leading-6">
          Saving consent does not start messages by itself. VenueMart administrators must also include this vendor in the controlled rollout and enable sending globally.
        </p>
      </div>

      {notice && (
        <div className="mt-4 flex items-center gap-3 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800" role="status">
          <BadgeCheck size={18} /> {notice}
        </div>
      )}
      {error && (
        <div className="mt-4 flex items-start gap-3 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800" role="alert">
          <AlertTriangle className="mt-0.5 shrink-0" size={18} /> {error}
        </div>
      )}

      <form className="mt-5 rounded-lg border border-border bg-white p-5 sm:p-6" onSubmit={saveEnabledPreference}>
        <div className="flex items-start gap-3">
          <span className="grid size-11 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700">
            <MessageCircleMore size={21} />
          </span>
          <div>
            <h3 className="font-semibold">Notification number</h3>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">
              Enter the WhatsApp number that should receive matching customer leads.
            </p>
          </div>
        </div>

        <label className="mt-6 block max-w-xl text-sm font-medium">
          WhatsApp number
          <input
            autoComplete="tel"
            className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary disabled:bg-muted"
            disabled={isSaving}
            inputMode="tel"
            maxLength={20}
            onChange={(event) => {
              setWhatsAppNumber(event.target.value);
              setConsentConfirmed(false);
              setNotice("");
            }}
            placeholder="+91 98765 43210"
            required
            type="tel"
            value={whatsAppNumber}
          />
          <span className="mt-1.5 block text-xs font-normal text-muted-foreground">Indian 10-digit numbers are saved with the +91 country code.</span>
        </label>

        {preference.whatsAppLeadNotificationsEnabled && (
          <label className="mt-6 flex max-w-xl cursor-pointer items-start gap-3 rounded-md border border-border p-4 text-sm">
            <input
              checked={paused}
              className="mt-0.5 size-4 accent-[hsl(var(--primary))]"
              disabled={isSaving}
              onChange={(event) => {
                setPaused(event.target.checked);
                setNotice("");
              }}
              type="checkbox"
            />
            <span>
              <strong className="flex items-center gap-2 font-medium"><PauseCircle size={16} /> Pause WhatsApp lead notifications</strong>
              <span className="mt-1 block leading-5 text-muted-foreground">Keep your consent and number, but temporarily stop new alerts.</span>
            </span>
          </label>
        )}

        {needsConsent && (
          <label className="mt-6 flex max-w-2xl cursor-pointer items-start gap-3 rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
            <input
              checked={consentConfirmed}
              className="mt-0.5 size-4 accent-[hsl(var(--primary))]"
              disabled={isSaving}
              onChange={(event) => setConsentConfirmed(event.target.checked)}
              required
              type="checkbox"
            />
            <span>
              <strong className="block font-medium">Explicit WhatsApp consent</strong>
              <span className="mt-1 block leading-5">
                I consent to VenueMart sending matching customer requirement notifications to this WhatsApp number. I understand that I can pause or opt out at any time.
              </span>
            </span>
          </label>
        )}

        <div className="mt-6 flex flex-wrap items-center gap-3">
          <button
            className="inline-flex h-11 items-center gap-2 rounded-md bg-primary px-5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
            disabled={
              isSaving ||
              !whatsAppNumber.trim() ||
              (needsConsent && !consentConfirmed) ||
              (preference.whatsAppLeadNotificationsEnabled && !hasPreferenceChanges)
            }
            type="submit"
          >
            {isSaving ? <LoaderCircle className="animate-spin" size={17} /> : <Check size={17} />}
            {preference.whatsAppLeadNotificationsEnabled ? "Save changes" : "Enable WhatsApp notifications"}
          </button>
          {consentDate && <span className="text-xs text-muted-foreground">Consent recorded {consentDate}</span>}
          {pauseDate && preference.whatsAppLeadNotificationsPaused && <span className="text-xs text-muted-foreground">Paused {pauseDate}</span>}
        </div>
      </form>

      {preference.whatsAppLeadNotificationsEnabled && (
        <div className="mt-5 rounded-lg border border-border bg-white p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <BellOff className="mt-0.5 shrink-0 text-rose-700" size={20} />
            <div className="flex-1">
              <h3 className="font-semibold">Turn off WhatsApp notifications</h3>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">This records an opt-out and stops this number from receiving new WhatsApp lead alerts.</p>
              {!confirmingOptOut ? (
                <button className="mt-4 h-10 rounded-md border border-rose-200 px-4 text-sm font-semibold text-rose-700 hover:bg-rose-50" disabled={isSaving} onClick={() => setConfirmingOptOut(true)} type="button">
                  Turn off notifications
                </button>
              ) : (
                <div className="mt-4 rounded-md border border-rose-200 bg-rose-50 p-4">
                  <p className="text-sm font-medium text-rose-900">Are you sure you want to opt out?</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button className="h-10 rounded-md border border-border bg-white px-4 text-sm font-semibold" disabled={isSaving} onClick={() => setConfirmingOptOut(false)} type="button">Cancel</button>
                    <button className="inline-flex h-10 items-center gap-2 rounded-md bg-rose-700 px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={isSaving} onClick={() => void optOut()} type="button">
                      {isSaving && <LoaderCircle className="animate-spin" size={16} />} Confirm opt-out
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {!preference.whatsAppLeadNotificationsEnabled && optOutDate && (
        <p className="mt-4 text-xs text-muted-foreground">Last opted out {optOutDate}. Fresh consent is required to subscribe again.</p>
      )}
    </section>
  );
}
