export type AuthRole = "CUSTOMER" | "HALL_OWNER" | "VENDOR" | "ADMIN" | "SUPER_ADMIN";

export type AuthUser = {
  id: string;
  fullName: string;
  phone: string;
  email?: string;
  role: AuthRole;
  status?: string;
};

export type AuthSession = {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  expiresAt: number;
  user: AuthUser;
};

export type LoginPayload = {
  phone: string;
  password: string;
};

export type RegisterPayload = LoginPayload & {
  fullName: string;
  email?: string;
  role: AuthRole;
};
