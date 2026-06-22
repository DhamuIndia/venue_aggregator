"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode
} from "react";
import { loginCustomer, loginDemo, registerCustomer } from "./auth-client";
import type { AuthRole, AuthUser, LoginPayload, RegisterPayload } from "./types";

type StoredSession = {
  token: string;
  user: AuthUser;
};

type AuthContextValue = {
  user: AuthUser | null;
  isLoading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  loginDemo: (role: AuthRole) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
};

const SESSION_KEY = "venue-aggregator-session";
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    try {
      const value = window.localStorage.getItem(SESSION_KEY);
      if (value) setUser((JSON.parse(value) as StoredSession).user);
    } finally {
      setIsLoading(false);
    }
  }, []);

  function saveSession(session: StoredSession) {
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    setUser(session.user);
  }

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      login: async (payload) => saveSession(await loginCustomer(payload)),
      loginDemo: async (role) => saveSession(await loginDemo(role)),
      register: async (payload) => saveSession(await registerCustomer(payload)),
      logout: () => {
        window.localStorage.removeItem(SESSION_KEY);
        setUser(null);
      }
    }),
    [isLoading, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
