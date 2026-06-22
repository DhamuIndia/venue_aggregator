import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/AuthShell";
import { LoginForm } from "@/components/auth/LoginForm";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return <AuthShell title="Welcome back" description="Sign in to access your account and workspace."><LoginForm /></AuthShell>;
}
