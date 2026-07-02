"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode
} from "react";
import {
  getCurrentUser,
  isMockAuthMode,
  loginCustomer,
  loginDemo,
  logoutSession,
  refreshSession,
  registerCustomer
} from "./auth-client";
import type { AuthRole, AuthSession, AuthUser, LoginPayload, RegisterPayload } from "./types";

type StoredSession = AuthSession;

type AuthContextValue = {
  user: AuthUser | null;
  accessToken: string | null;
  isLoading: boolean;
  login: (payload: LoginPayload) => Promise<AuthUser>;
  loginDemo: (role: AuthRole) => Promise<AuthUser>;
  register: (payload: RegisterPayload) => Promise<AuthUser>;
  logout: () => void;
};

const SESSION_KEY = "venue-aggregator-session";
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    async function hydrate() {
      try {
        const storedSession = getStoredSession();
        if (!storedSession) return;

        if (isMockAuthMode()) {
          if (isMounted) {
            setUser(storedSession.user);
            setAccessToken(storedSession.accessToken);
          }
          return;
        }

        const activeSession = await ensureFreshSession(storedSession);
        const currentUser = await getCurrentUser(activeSession.accessToken);
        if (!isMounted) return;
        saveSession({ ...activeSession, user: currentUser });
      } catch {
        window.localStorage.removeItem(SESSION_KEY);
        if (isMounted) setUser(null);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    hydrate();

    return () => {
      isMounted = false;
    };
  }, []);

  async function ensureFreshSession(session: StoredSession) {
    const expiresSoon = session.expiresAt <= Date.now() + 30_000;
    return expiresSoon ? refreshSession(session.refreshToken) : session;
  }

  function saveSession(session: StoredSession) {
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    setUser(session.user);
    setAccessToken(session.accessToken);
  }

  function clearSession() {
    window.localStorage.removeItem(SESSION_KEY);
    setUser(null);
    setAccessToken(null);
  }

  function getStoredSession() {
    try {
      const value = window.localStorage.getItem(SESSION_KEY);
      return value ? JSON.parse(value) as StoredSession : null;
    } catch {
      return null;
    }
  }

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      accessToken,
      isLoading,
      login: async (payload) => {
        const session = await loginCustomer(payload);
        saveSession(session);
        return session.user;
      },
      loginDemo: async (role) => {
        const session = await loginDemo(role);
        saveSession(session);
        return session.user;
      },
      register: async (payload) => {
        const session = await registerCustomer(payload);
        saveSession(session);
        return session.user;
      },
      logout: () => {
        const session = getStoredSession();
        if (session && !isMockAuthMode()) {
          void logoutSession(session.accessToken).catch(() => undefined);
        }
        clearSession();
      }
    }),
    [accessToken, isLoading, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
