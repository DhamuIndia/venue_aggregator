import { ApiError, apiRequest } from "@/lib/api-client";
import type { VendorPackage } from "./types";
import { workspaceVendor } from "./workspace-data";

const STORAGE_KEY = "venue-vendor-packages";
const useMockVendorPackages = process.env.NEXT_PUBLIC_VENDOR_PACKAGES_MODE === "mock";

export type VendorPackagePayload = {
  name: string;
  description: string;
  price: number;
  includes: string[];
};

export type VendorPackagesResult = {
  packages: VendorPackage[];
  source: "api" | "mock";
};

export async function getVendorPackages(accessToken?: string | null): Promise<VendorPackagesResult> {
  if (useMockVendorPackages || !accessToken) return { packages: getLocalPackages(), source: "mock" };

  try {
    const response = await apiRequest<unknown>("/vendor/packages", {
      token: accessToken
    });
    const packages = extractPackages(response);
    packages.forEach(cachePackage);
    return { packages: packages.length ? packages : getLocalPackages(), source: "api" };
  } catch {
    return { packages: getLocalPackages(), source: "mock" };
  }
}

export async function createVendorPackage(payload: VendorPackagePayload, accessToken?: string | null) {
  if (useMockVendorPackages || !accessToken) return createLocalPackage(payload);

  try {
    const response = await apiRequest<unknown>("/vendor/packages", {
      method: "POST",
      token: accessToken,
      body: JSON.stringify(payload)
    });
    const packageItem = toVendorPackage(response, payload) ?? createLocalPackage(payload);
    cachePackage(packageItem);
    return packageItem;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return createLocalPackage(payload);
  }
}

export async function updateVendorPackage(packageId: string, payload: VendorPackagePayload, accessToken?: string | null) {
  if (useMockVendorPackages || !accessToken) return updateLocalPackage(packageId, payload);

  try {
    const response = await apiRequest<unknown>(`/vendor/packages/${encodeURIComponent(packageId)}`, {
      method: "PUT",
      token: accessToken,
      body: JSON.stringify(payload)
    });
    const packageItem = toVendorPackage(response, payload) ?? updateLocalPackage(packageId, payload);
    cachePackage(packageItem);
    return packageItem;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateLocalPackage(packageId, payload);
  }
}

export async function deleteVendorPackage(packageId: string, accessToken?: string | null) {
  if (useMockVendorPackages || !accessToken) return deleteLocalPackage(packageId);

  try {
    await apiRequest<unknown>(`/vendor/packages/${encodeURIComponent(packageId)}`, {
      method: "DELETE",
      token: accessToken
    });
    return deleteLocalPackage(packageId);
  } catch (exception) {
    if (exception instanceof ApiError && [401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return deleteLocalPackage(packageId);
  }
}

function getLocalPackages() {
  if (typeof window === "undefined") return workspaceVendor.packages;
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "null") as unknown;
    const packages = Array.isArray(parsed) ? parsed.map((item) => toVendorPackage(item)).filter(Boolean) as VendorPackage[] : [];
    return packages.length ? packages : workspaceVendor.packages;
  } catch {
    return workspaceVendor.packages;
  }
}

function createLocalPackage(payload: VendorPackagePayload): VendorPackage {
  const packageItem = { ...payload, id: `PKG-${Date.now().toString().slice(-6)}` };
  cachePackage(packageItem);
  return packageItem;
}

function updateLocalPackage(packageId: string, payload: VendorPackagePayload) {
  const packageItem = { ...payload, id: packageId };
  cachePackage(packageItem);
  return packageItem;
}

function deleteLocalPackage(packageId: string) {
  if (typeof window === "undefined") return packageId;
  const packages = getLocalPackages().filter((item) => item.id !== packageId);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(packages));
  return packageId;
}

function cachePackage(packageItem: VendorPackage) {
  if (typeof window === "undefined") return;
  const packages = getLocalPackages().filter((item) => item.id !== packageItem.id);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify([packageItem, ...packages]));
}

function extractPackages(response: unknown) {
  if (Array.isArray(response)) return response.map((item) => toVendorPackage(item)).filter(Boolean) as VendorPackage[];
  if (!isRecord(response)) return [];
  const candidates = [response.items, response.content, response.data, response.results, response.packages];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.map((item) => toVendorPackage(item)).filter(Boolean) as VendorPackage[] : [];
}

function toVendorPackage(value: unknown, fallback?: VendorPackagePayload): VendorPackage | undefined {
  if (!isRecord(value)) return undefined;
  const record = isRecord(value.data) ? value.data : isRecord(value.package) ? value.package : value;

  const id = stringValue(record, ["id", "packageId", "package_id"]);
  const name = stringValue(record, ["name", "packageName", "package_name"]) ?? fallback?.name;
  const description = stringValue(record, ["description", "summary"]) ?? fallback?.description;
  const price = numberValue(record, ["price", "amount", "startingPrice"]) ?? fallback?.price;
  const includes = arrayOfStrings(record.includes) ?? arrayOfStrings(record.features) ?? fallback?.includes;

  if (!id || !name || typeof price !== "number") return undefined;

  return {
    id,
    name,
    description: description ?? "",
    price,
    includes: includes ?? []
  };
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

function arrayOfStrings(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const strings = value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  return strings.length ? strings : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
