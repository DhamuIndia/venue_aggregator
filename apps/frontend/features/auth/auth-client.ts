import { apiRequest } from "@/lib/api-client";
import type { AuthRole, AuthUser, LoginPayload, RegisterPayload } from "./types";

type AuthResponse = {
  token: string;
  message: string;
};

type Session = {
  token: string;
  user: AuthUser;
};

const useMockAuth = process.env.NEXT_PUBLIC_AUTH_MODE !== "api";

function createMockSession(payload: { phone: string; fullName?: string; email?: string; role?: AuthRole }): Session {
  const role = payload.role ?? "CUSTOMER";
  const mockUsers: Record<AuthRole, Pick<AuthUser, "id" | "fullName"> & { token: string }> = {
    CUSTOMER: { id: "customer-101", fullName: "Priya Raman", token: "mock-customer-token" },
    HALL_OWNER: { id: "owner-201", fullName: "Arun Kumar", token: "mock-owner-token" },
    VENDOR: { id: "vendor-301", fullName: "Manoj Krishnan", token: "mock-vendor-token" },
    ADMIN: { id: "admin-001", fullName: "Ananya Iyer", token: "mock-admin-token" }
  };
  const mockUser = mockUsers[role];
  return {
    token: mockUser.token,
    user: {
      id: mockUser.id,
      fullName: payload.fullName ?? mockUser.fullName,
      phone: payload.phone,
      email: payload.email,
      role
    }
  };
}

export async function loginCustomer(payload: LoginPayload): Promise<Session> {
  if (useMockAuth) return createMockSession(payload);

  const response = await apiRequest<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });

  return {
    token: response.token,
    user: createMockSession(payload).user
  };
}

export async function registerCustomer(payload: RegisterPayload): Promise<Session> {
  if (useMockAuth) return createMockSession(payload);

  const { role: _role, ...backendPayload } = payload;

  const response = await apiRequest<AuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(backendPayload)
  });

  return {
    token: response.token,
    user: createMockSession(payload).user
  };
}

export async function loginDemo(role: AuthRole): Promise<Session> {
  const phones: Record<AuthRole, string> = {
    CUSTOMER: "9876543210",
    HALL_OWNER: "9876501234",
    VENDOR: "9884012345",
    ADMIN: "9000000001"
  };
  return createMockSession({ phone: phones[role], role });
}
