import { ApiError, apiRequest } from "@/lib/api-client";

const STORAGE_KEY = "venue-vendor-subscription";
const useMockVendorSubscription = process.env.NEXT_PUBLIC_VENDOR_SUBSCRIPTION_MODE === "mock";

export type SubscriptionPlan = {
  id: string;
  name: string;
  price: number;
  currency: "INR";
  billingCycle: "MONTHLY" | "YEARLY";
  description: string;
  features: string[];
  leadLimit?: number | null;
  isPopular?: boolean;
};

export type VendorSubscription = {
  planId: string;
  status: "NONE" | "ACTIVE" | "PENDING_PAYMENT" | "EXPIRED" | "CANCELLED";
  currentPeriodEnd?: string;
  pendingOrderId?: string;
};

export type SubscriptionOrder = {
  orderId: string;
  planId: string;
  amount: number;
  currency: "INR";
  status: "CREATED" | "PENDING_PAYMENT" | "ACTIVE";
  keyId?: string;
  checkoutUrl?: string;
};

export const fallbackSubscriptionPlans: SubscriptionPlan[] = [
  {
    id: "STARTER",
    name: "Starter",
    price: 999,
    currency: "INR",
    billingCycle: "MONTHLY",
    description: "For a growing local service business.",
    features: ["Public verified profile", "Up to 20 leads per month", "Two service packages", "Standard marketplace ranking"],
    leadLimit: 20
  },
  {
    id: "GROWTH",
    name: "Growth",
    price: 2499,
    currency: "INR",
    billingCycle: "MONTHLY",
    description: "For teams ready to expand across the city.",
    features: ["Unlimited customer leads", "Unlimited packages", "Priority marketplace placement", "Lead performance analytics"],
    leadLimit: null,
    isPopular: true
  }
];

export const fallbackVendorSubscription: VendorSubscription = {
  planId: "STARTER",
  status: "ACTIVE",
  currentPeriodEnd: "2026-07-23T00:00:00Z"
};

export async function getSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  if (useMockVendorSubscription) return fallbackSubscriptionPlans;

  try {
    const response = await apiRequest<unknown>("/public/subscription-plans");
    const plans = extractPlans(response);
    return plans.length ? plans : fallbackSubscriptionPlans;
  } catch {
    return fallbackSubscriptionPlans;
  }
}

export async function getVendorSubscription(accessToken?: string | null): Promise<VendorSubscription> {
  if (useMockVendorSubscription || !accessToken) return getLocalSubscription();

  try {
    const response = await apiRequest<unknown>("/vendor/subscription", {
      token: accessToken
    });
    const subscription = toVendorSubscription(response) ?? getLocalSubscription();
    saveLocalSubscription(subscription);
    return subscription;
  } catch {
    return getLocalSubscription();
  }
}

export async function createSubscriptionOrder(planId: string, accessToken?: string | null): Promise<SubscriptionOrder> {
  if (useMockVendorSubscription || !accessToken) return createLocalOrder(planId);

  try {
    const response = await apiRequest<unknown>("/vendor/subscription/orders", {
      method: "POST",
      token: accessToken,
      body: JSON.stringify({ planId })
    });
    const order = toSubscriptionOrder(response, planId) ?? createLocalOrder(planId);
    saveLocalSubscription({ planId, status: order.status === "ACTIVE" ? "ACTIVE" : "PENDING_PAYMENT", pendingOrderId: order.orderId });
    return order;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return createLocalOrder(planId);
  }
}

function getLocalSubscription() {
  if (typeof window === "undefined") return fallbackVendorSubscription;
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "null") as unknown;
    return toVendorSubscription(parsed) ?? fallbackVendorSubscription;
  } catch {
    return fallbackVendorSubscription;
  }
}

function saveLocalSubscription(subscription: VendorSubscription) {
  if (typeof window !== "undefined") window.localStorage.setItem(STORAGE_KEY, JSON.stringify(subscription));
  return subscription;
}

function createLocalOrder(planId: string): SubscriptionOrder {
  const plan = fallbackSubscriptionPlans.find((item) => item.id === planId) ?? fallbackSubscriptionPlans[0];
  const order: SubscriptionOrder = {
    orderId: `ORDER-${Date.now().toString().slice(-6)}`,
    planId,
    amount: plan.price,
    currency: plan.currency,
    status: "PENDING_PAYMENT"
  };
  saveLocalSubscription({ planId, status: "PENDING_PAYMENT", pendingOrderId: order.orderId });
  return order;
}

function extractPlans(response: unknown) {
  if (Array.isArray(response)) return response.map(toSubscriptionPlan).filter(Boolean) as SubscriptionPlan[];
  if (!isRecord(response)) return [];
  const candidates = [response.items, response.content, response.data, response.results, response.plans];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.map(toSubscriptionPlan).filter(Boolean) as SubscriptionPlan[] : [];
}

function toSubscriptionPlan(value: unknown): SubscriptionPlan | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "planId", "plan_id"]);
  const name = stringValue(value, ["name", "planName", "plan_name"]);
  const price = numberValue(value, ["price", "amount", "monthlyPrice"]);
  if (!id || !name || typeof price !== "number") return undefined;

  return {
    id,
    name,
    price,
    currency: "INR",
    billingCycle: billingCycleValue(value) ?? "MONTHLY",
    description: stringValue(value, ["description", "summary"]) ?? "",
    features: arrayOfStrings(value.features) ?? arrayOfStrings(value.includes) ?? [],
    leadLimit: numberValue(value, ["leadLimit", "lead_limit", "monthlyLeadLimit"]) ?? (booleanValue(value, ["unlimitedLeads"]) ? null : undefined),
    isPopular: booleanValue(value, ["isPopular", "popular", "recommended"])
  };
}

function toVendorSubscription(value: unknown): VendorSubscription | undefined {
  if (!isRecord(value)) return undefined;
  const record = isRecord(value.data) ? value.data : isRecord(value.subscription) ? value.subscription : value;
  const planId = stringValue(record, ["planId", "plan_id", "id"]);
  if (!planId) return undefined;

  return {
    planId,
    status: subscriptionStatus(record) ?? "NONE",
    currentPeriodEnd: stringValue(record, ["currentPeriodEnd", "current_period_end", "expiresAt", "expires_at"]),
    pendingOrderId: stringValue(record, ["pendingOrderId", "pending_order_id", "orderId", "order_id"])
  };
}

function toSubscriptionOrder(value: unknown, fallbackPlanId: string): SubscriptionOrder | undefined {
  if (!isRecord(value)) return undefined;
  const record = isRecord(value.data) ? value.data : isRecord(value.order) ? value.order : value;
  const orderId = stringValue(record, ["orderId", "order_id", "razorpayOrderId", "razorpay_order_id", "id"]);
  const planId = stringValue(record, ["planId", "plan_id"]) ?? fallbackPlanId;
  const amount = numberValue(record, ["amount", "price"]) ?? fallbackSubscriptionPlans.find((plan) => plan.id === planId)?.price;
  if (!orderId || typeof amount !== "number") return undefined;

  return {
    orderId,
    planId,
    amount,
    currency: "INR",
    status: orderStatus(record) ?? "CREATED",
    keyId: stringValue(record, ["keyId", "key_id", "razorpayKeyId", "razorpay_key_id"]),
    checkoutUrl: stringValue(record, ["checkoutUrl", "checkout_url", "paymentUrl", "payment_url"])
  };
}

function subscriptionStatus(record: Record<string, unknown>): VendorSubscription["status"] | undefined {
  const value = stringValue(record, ["status"]);
  if (value === "NONE" || value === "ACTIVE" || value === "PENDING_PAYMENT" || value === "EXPIRED" || value === "CANCELLED") return value;
  if (value === "PENDING" || value === "CREATED") return "PENDING_PAYMENT";
  return undefined;
}

function orderStatus(record: Record<string, unknown>): SubscriptionOrder["status"] | undefined {
  const value = stringValue(record, ["status"]);
  if (value === "CREATED" || value === "PENDING_PAYMENT" || value === "ACTIVE") return value;
  if (value === "PENDING") return "PENDING_PAYMENT";
  return undefined;
}

function billingCycleValue(record: Record<string, unknown>): SubscriptionPlan["billingCycle"] | undefined {
  const value = stringValue(record, ["billingCycle", "billing_cycle", "interval"]);
  if (value === "MONTHLY" || value === "YEARLY") return value;
  if (value?.toUpperCase() === "MONTH") return "MONTHLY";
  if (value?.toUpperCase() === "YEAR") return "YEARLY";
  return undefined;
}

function stringValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function numberValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }
  return undefined;
}

function booleanValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "boolean") return value;
    if (typeof value === "string") return value.toLowerCase() === "true";
  }
  return undefined;
}

function arrayOfStrings(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const strings = value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  return strings.length ? strings : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
