import { apiRequest } from "@/lib/api-client";
import type { AuthRole, AuthSession, AuthUser, LoginPayload, RegisterPayload } from "./types";

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: AuthUser;
};

const useMockAuth = process.env.NEXT_PUBLIC_AUTH_MODE !== "api";

export function isMockAuthMode() {
  return useMockAuth;
}

function createMockSession(payload: { phone: string; fullName?: string; email?: string; role?: AuthRole }): AuthSession {
  const role = payload.role ?? "CUSTOMER";
  const mockUsers: Record<AuthRole, Pick<AuthUser, "id" | "fullName"> & { token: string }> = {
    CUSTOMER: { id: "customer-101", fullName: "Priya Raman", token: "mock-customer-token" },
    HALL_OWNER: { id: "owner-201", fullName: "Arun Kumar", token: "mock-owner-token" },
    VENDOR: { id: "vendor-301", fullName: "Manoj Krishnan", token: "mock-vendor-token" },
    ADMIN: { id: "admin-001", fullName: "Ananya Iyer", token: "mock-admin-token" },
    SUPER_ADMIN: { id: "super-admin-001", fullName: "Platform Owner", token: "mock-super-admin-token" }
  };
  const mockUser = mockUsers[role];
  const expiresInSeconds = 60 * 60;

  return {
    accessToken: mockUser.token,
    refreshToken: `${mockUser.token}-refresh`,
    expiresInSeconds,
    expiresAt: Date.now() + expiresInSeconds * 1000,
    user: {
      id: mockUser.id,
      fullName: payload.fullName ?? mockUser.fullName,
      phone: payload.phone,
      email: payload.email,
      role,
      status: "ACTIVE"
    }
  };
}

export async function loginCustomer(payload: LoginPayload): Promise<AuthSession> {
  if (useMockAuth) return createMockSession(payload);

  const response = await apiRequest<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });

  return toSession(response);
}

export async function registerCustomer(payload: RegisterPayload): Promise<AuthSession> {
  if (useMockAuth) return createMockSession(payload);

  const requestPayload = {
    ...payload,
    email: payload.email?.trim() || undefined
  };

  const response = await apiRequest<AuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(requestPayload)
  });

  return toSession(response);
}

export async function refreshSession(refreshToken: string): Promise<AuthSession> {
  const response = await apiRequest<AuthResponse>("/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken })
  });

  return toSession(response);
}

export async function getCurrentUser(accessToken: string): Promise<AuthUser> {
  return apiRequest<AuthUser>("/auth/me", {
    token: accessToken
  });
}

export async function logoutSession(accessToken: string): Promise<void> {
  await apiRequest<void>("/auth/logout", {
    method: "POST",
    token: accessToken
  });
}

export async function loginDemo(role: AuthRole): Promise<AuthSession> {
  const phones: Record<AuthRole, string> = {
    CUSTOMER: "9876543210",
    HALL_OWNER: "9876501234",
    VENDOR: "9884012345",
    ADMIN: "9000000001",
    SUPER_ADMIN: "9000000002"
  };
  return createMockSession({ phone: phones[role], role });
}

function toSession(response: AuthResponse): AuthSession {
  return {
    ...response,
    expiresAt: Date.now() + response.expiresInSeconds * 1000
  };
}
