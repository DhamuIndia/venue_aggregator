"use client";

import {
  BellRing,
  CheckCheck,
  ChevronDown,
  ChevronUp,
  CircleAlert,
  Clock3,
  LoaderCircle,
  MessageCircleMore,
  RefreshCw,
  Search,
  Send,
  UsersRound
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
  getAdminRequirementNotificationDetail,
  getAdminRequirementNotificationMonitoring,
  retryAdminLeadNotification,
  type AdminRequirementNotificationDetail,
  type AdminRequirementNotificationSummary,
  type AdminVendorNotificationDelivery
} from "@/features/admin/admin-client";
import { useAuth } from "@/features/auth/AuthProvider";

export function AdminLeadNotificationMonitor() {
  const { accessToken } = useAuth();
  const [requirements, setRequirements] = useState<AdminRequirementNotificationSummary[]>([]);
  const [sendingEnabled, setSendingEnabled] = useState(false);
  const [maxAttempts, setMaxAttempts] = useState(3);
  const [rolloutAllowedVendorIds, setRolloutAllowedVendorIds] = useState<number[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminRequirementNotificationDetail | null>(null);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [retryingJobId, setRetryingJobId] = useState<number | null>(null);

  useEffect(() => {
    let isCurrent = true;

    async function loadRequirements() {
      try {
        setIsLoading(true);
        setError("");
        const response = await getAdminRequirementNotificationMonitoring(accessToken);
        if (!isCurrent) return;
        setRequirements(response.content);
        setSendingEnabled(response.sendingEnabled);
        setMaxAttempts(response.maxAttempts);
        setRolloutAllowedVendorIds(response.rolloutAllowedVendorIds ?? []);
      } catch (exception) {
        if (isCurrent) {
          setError(exception instanceof Error ? exception.message : "Could not load notification monitoring.");
        }
      } finally {
        if (isCurrent) setIsLoading(false);
      }
    }

    void loadRequirements();
    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  const filteredRequirements = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return requirements;
    return requirements.filter((requirement) =>
      [
        requirement.requirementId,
        requirement.customerName,
        requirement.eventType,
        requirement.location,
        requirement.city,
        ...requirement.services
      ].join(" ").toLowerCase().includes(query)
    );
  }, [requirements, search]);

  async function toggleRequirement(requirementId: number) {
    if (selectedId === requirementId) {
      setSelectedId(null);
      setDetail(null);
      return;
    }

    try {
      setSelectedId(requirementId);
      setDetail(null);
      setIsLoadingDetail(true);
      setError("");
      const response = await getAdminRequirementNotificationDetail(requirementId, accessToken);
      setDetail(response);
      setSendingEnabled(response.sendingEnabled);
      setMaxAttempts(response.maxAttempts);
      setRolloutAllowedVendorIds(response.rolloutAllowedVendorIds ?? []);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not load requirement delivery details.");
    } finally {
      setIsLoadingDetail(false);
    }
  }

  async function scheduleRetry(jobId: number) {
    if (!selectedId) return;
    try {
      setRetryingJobId(jobId);
      setNotice("");
      setError("");
      const response = await retryAdminLeadNotification(jobId, accessToken);
      setNotice(response.message);
      const [updatedDetail, updatedList] = await Promise.all([
        getAdminRequirementNotificationDetail(selectedId, accessToken),
        getAdminRequirementNotificationMonitoring(accessToken)
      ]);
      setDetail(updatedDetail);
      setRequirements(updatedList.content);
      setSendingEnabled(updatedList.sendingEnabled);
      setRolloutAllowedVendorIds(updatedList.rolloutAllowedVendorIds ?? []);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not schedule the retry.");
    } finally {
      setRetryingJobId(null);
    }
  }

  const rolloutBlocked = sendingEnabled && rolloutAllowedVendorIds.length === 0;

  return (
    <section className="py-7">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">Lead notification monitoring</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Follow every requirement from vendor matching through WhatsApp delivery.
          </p>
        </div>
        <label className="relative block w-full sm:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} />
          <input
            className="h-10 w-full rounded-md border border-border bg-white pl-9 pr-3 text-sm outline-none focus:border-primary"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search requirement or vendor service"
            value={search}
          />
        </label>
      </div>

      <div className={`mt-5 flex items-start gap-3 rounded-md border px-4 py-3 text-sm ${
        rolloutBlocked
          ? "border-rose-200 bg-rose-50 text-rose-800"
          : sendingEnabled
            ? "border-emerald-200 bg-emerald-50 text-emerald-800"
            : "border-amber-200 bg-amber-50 text-amber-900"
      }`}>
        <BellRing className="mt-0.5 shrink-0" size={18} />
        <p>
          <strong>
            {rolloutBlocked
              ? "WhatsApp sending is blocked by the empty rollout allowlist."
              : sendingEnabled
                ? "WhatsApp sending is enabled for the controlled rollout."
                : "WhatsApp sending is disabled."}
          </strong>{" "}
          {rolloutBlocked
            ? "No vendor job can be claimed or sent."
            : sendingEnabled
              ? `Only ${rolloutAllowedVendorIds.length} allowlisted vendor${rolloutAllowedVendorIds.length === 1 ? "" : "s"} can be processed.`
              : `Monitoring remains live. ${rolloutAllowedVendorIds.length} vendor${rolloutAllowedVendorIds.length === 1 ? "" : "s"} ${rolloutAllowedVendorIds.length === 1 ? "is" : "are"} currently allowlisted, but queued notifications will wait.`}{" "}
          Maximum attempts: {maxAttempts}.
        </p>
      </div>

      {notice && <p className="mt-4 rounded-md bg-emerald-50 px-4 py-3 text-sm text-emerald-800" role="status">{notice}</p>}
      {error && <p className="mt-4 rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">{error}</p>}

      <div className="mt-5 grid gap-4">
        {isLoading ? [1, 2, 3].map((item) => (
          <div className="h-40 animate-pulse rounded-lg border border-border bg-white" key={item} />
        )) : filteredRequirements.map((requirement) => {
          const isOpen = selectedId === requirement.requirementId;
          return (
            <article className="overflow-hidden rounded-lg border border-border bg-white" key={requirement.requirementId}>
              <button
                aria-expanded={isOpen}
                className="w-full p-5 text-left hover:bg-muted/30"
                onClick={() => void toggleRequirement(requirement.requirementId)}
                type="button"
              >
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-semibold">{requirement.eventType}</h3>
                      <span className="rounded-full bg-slate-100 px-2.5 py-1 font-mono text-xs text-slate-700">
                        REQ-{requirement.requirementId}
                      </span>
                    </div>
                    <p className="mt-2 text-sm text-muted-foreground">
                      {formatDate(requirement.eventDate)} · {requirement.location}
                      {requirement.city ? `, ${requirement.city}` : ""}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {requirement.customerName} · {requirement.services.join(", ") || "No service category"}
                    </p>
                  </div>
                  {isOpen ? <ChevronUp size={19} /> : <ChevronDown size={19} />}
                </div>
                <div className="mt-5 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-8">
                  <Count label="Matched" value={requirement.matchedVendorCount} tone="slate" />
                  <Count label="Subscribed" value={requirement.subscribedVendorCount} tone="blue" />
                  <Count label="Skipped" value={requirement.notificationSkippedCount} tone="amber" />
                  <Count label="Queued" value={requirement.queuedCount} tone="violet" />
                  <Count label="Sent" value={requirement.sentCount} tone="blue" />
                  <Count label="Delivered" value={requirement.deliveredCount} tone="emerald" />
                  <Count label="Read" value={requirement.readCount} tone="emerald" />
                  <Count label="Failed" value={requirement.failedCount} tone="rose" />
                </div>
              </button>

              {isOpen && (
                <div className="border-t border-border bg-muted/20 p-5">
                  {isLoadingDetail || detail?.summary.requirementId !== requirement.requirementId ? (
                    <div className="grid min-h-32 place-items-center">
                      <LoaderCircle className="animate-spin text-primary" size={24} />
                    </div>
                  ) : detail.vendors.length ? (
                    <div className="grid gap-4">
                      {detail.vendors.map((vendor) => (
                        <VendorDeliveryCard
                          isRetrying={retryingJobId === vendor.notificationJobId}
                          key={vendor.vendorLeadId}
                          onRetry={scheduleRetry}
                          vendor={vendor}
                        />
                      ))}
                    </div>
                  ) : (
                    <p className="rounded-md border border-dashed border-border bg-white p-6 text-center text-sm text-muted-foreground">
                      No vendors were matched to this requirement.
                    </p>
                  )}
                </div>
              )}
            </article>
          );
        })}
      </div>

      {!isLoading && filteredRequirements.length === 0 && (
        <p className="mt-5 rounded-lg border border-dashed border-border bg-white p-10 text-center text-sm text-muted-foreground">
          No customer requirements match this search.
        </p>
      )}
    </section>
  );
}

function VendorDeliveryCard({
  vendor,
  isRetrying,
  onRetry
}: {
  vendor: AdminVendorNotificationDelivery;
  isRetrying: boolean;
  onRetry: (jobId: number) => Promise<void>;
}) {
  return (
    <article className="rounded-lg border border-border bg-white p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h4 className="font-semibold">{vendor.vendorName}</h4>
            <StatusBadge status={vendor.notificationStatus} />
            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${vendor.subscribedAtEvaluation ? "bg-blue-50 text-blue-700" : "bg-slate-100 text-slate-600"}`}>
              {vendor.subscribedAtEvaluation ? "Subscribed" : "Not subscribed"}
            </span>
            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${vendor.rolloutAllowed ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>
              {vendor.rolloutAllowed ? "Pilot allowlisted" : "Outside pilot"}
            </span>
          </div>
          <p className="mt-2 text-sm text-muted-foreground">
            {vendor.service} · {vendor.leadReference}
            {vendor.maskedDestination ? ` · ${vendor.maskedDestination}` : ""}
          </p>
        </div>
        {vendor.notificationJobId && vendor.canManualRetry && (
          <button
            className="inline-flex h-9 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-white disabled:opacity-60"
            disabled={isRetrying}
            onClick={() => void onRetry(vendor.notificationJobId!)}
            type="button"
          >
            {isRetrying ? <LoaderCircle className="animate-spin" size={16} /> : <RefreshCw size={16} />}
            Retry now
          </button>
        )}
      </div>

      {vendor.skipReason && (
        <div className="mt-4 flex gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900">
          <CircleAlert className="mt-0.5 shrink-0" size={16} />
          <span>{vendor.skipReason}</span>
        </div>
      )}
      {vendor.failureReason && (
        <div className="mt-4 rounded-md bg-rose-50 px-3 py-3 text-sm text-rose-800">
          <p className="font-medium">{vendor.failureTitle || "Delivery failure"}{vendor.failureCode ? ` · Meta ${vendor.failureCode}` : ""}</p>
          <p className="mt-1 leading-6">{vendor.failureReason}</p>
          <p className="mt-1 text-xs">{vendor.failureTemporary ? "Temporary failure" : "Permanent failure"}</p>
        </div>
      )}

      <div className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
        <Info icon={<UsersRound size={16} />} label="Current subscription" value={vendor.currentlySubscribed ? "Subscribed" : "Not subscribed"} />
        <Info icon={<Clock3 size={16} />} label="Attempts" value={`${vendor.attemptCount}`} />
        <Info
          icon={<RefreshCw size={16} />}
          label="Next retry"
          value={vendor.nextRetryAt ? formatDateTime(vendor.nextRetryAt) : vendor.canManualRetry ? "Manual retry available" : vendor.manualRetryBlockedReason || "None"}
        />
      </div>

      <div className="mt-5 border-t border-border pt-4">
        <h5 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Retry and delivery history</h5>
        {vendor.retryHistory.length ? (
          <div className="mt-3 grid gap-3">
            {vendor.retryHistory.map((attempt) => (
              <div className="grid gap-2 rounded-md bg-muted/50 p-3 text-sm sm:grid-cols-[110px_120px_1fr]" key={attempt.attemptNumber}>
                <strong>Attempt {attempt.attemptNumber}</strong>
                <StatusBadge status={attempt.status} />
                <div className="text-muted-foreground">
                  <p>Requested {formatDateTime(attempt.requestedAt)}</p>
                  {attempt.failureReason && <p className="mt-1 text-rose-700">{attempt.failureReason}</p>}
                  {attempt.providerMessageId && <p className="mt-1 break-all font-mono text-xs">{attempt.providerMessageId}</p>}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="mt-3 text-sm text-muted-foreground">No send attempt has been made.</p>
        )}
      </div>
    </article>
  );
}

function Count({ label, value, tone }: { label: string; value: number; tone: "slate" | "blue" | "amber" | "violet" | "emerald" | "rose" }) {
  const tones = {
    slate: "bg-slate-50 text-slate-700",
    blue: "bg-blue-50 text-blue-700",
    amber: "bg-amber-50 text-amber-800",
    violet: "bg-violet-50 text-violet-700",
    emerald: "bg-emerald-50 text-emerald-700",
    rose: "bg-rose-50 text-rose-700"
  };
  return <span className={`rounded-md px-3 py-2 ${tones[tone]}`}><strong className="block text-lg">{value}</strong><span className="text-xs">{label}</span></span>;
}

function StatusBadge({ status }: { status: string }) {
  const style = status === "READ" || status === "DELIVERED"
    ? "bg-emerald-50 text-emerald-700"
    : status === "FAILED"
      ? "bg-rose-50 text-rose-700"
      : status === "SKIPPED" || status === "CANCELLED"
        ? "bg-amber-50 text-amber-800"
        : status === "SENT"
          ? "bg-blue-50 text-blue-700"
          : "bg-violet-50 text-violet-700";
  const Icon = status === "READ" || status === "DELIVERED"
    ? CheckCheck
    : status === "FAILED"
      ? CircleAlert
      : status === "SENT"
        ? Send
        : MessageCircleMore;
  return <span className={`inline-flex w-fit items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${style}`}><Icon size={13} />{readableStatus(status)}</span>;
}

function Info({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return <div className="flex gap-2 rounded-md bg-muted/50 p-3"><span className="mt-0.5 text-primary">{icon}</span><div><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 text-sm font-medium">{value}</p></div></div>;
}

function readableStatus(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^\w/, (character) => character.toUpperCase());
}

function formatDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(date);
}

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(date);
}
