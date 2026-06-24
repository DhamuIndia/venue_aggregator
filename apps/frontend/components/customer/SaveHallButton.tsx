"use client";

import { Heart, LoaderCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import {
  isCustomerHallSaved,
  removeCustomerSavedHall,
  saveCustomerHall,
  subscribeToSavedHallChanges
} from "@/features/customer/saved-halls-client";
import type { HallSummary } from "@/features/halls/types";

type SaveHallButtonProps = {
  hall: HallSummary;
  className?: string;
  initialSaved?: boolean;
  onSavedChange?: (hallId: string, isSaved: boolean) => void;
  variant?: "icon" | "compact";
};

export function SaveHallButton({
  className,
  hall,
  initialSaved = false,
  onSavedChange,
  variant = "icon"
}: SaveHallButtonProps) {
  const { accessToken } = useAuth();
  const [isSaved, setIsSaved] = useState(initialSaved);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let isCurrent = true;

    async function loadSavedState() {
      setIsLoading(true);

      try {
        const saved = await isCustomerHallSaved(hall.id, accessToken);
        if (!isCurrent) return;
        setIsSaved(saved);
      } catch {
        if (isCurrent) setIsSaved(initialSaved);
      } finally {
        if (isCurrent) setIsLoading(false);
      }
    }

    loadSavedState();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, hall.id, initialSaved]);

  useEffect(() => {
    return subscribeToSavedHallChanges(async () => {
      try {
        setIsSaved(await isCustomerHallSaved(hall.id, accessToken));
      } catch {
        setIsSaved(initialSaved);
      }
    });
  }, [accessToken, hall.id, initialSaved]);

  async function toggleSaved() {
    if (isSaving) return;

    setIsSaving(true);
    const nextSaved = !isSaved;

    try {
      if (nextSaved) {
        await saveCustomerHall(hall, accessToken);
      } else {
        await removeCustomerSavedHall(hall.id, accessToken);
      }
      setIsSaved(nextSaved);
      onSavedChange?.(hall.id, nextSaved);
    } finally {
      setIsSaving(false);
      setIsLoading(false);
    }
  }

  const label = isSaved ? `Remove ${hall.name} from saved venues` : `Save ${hall.name}`;
  const disabled = isLoading || isSaving;

  if (variant === "compact") {
    return (
      <button
        aria-label={label}
        className={className ?? "inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"}
        disabled={disabled}
        onClick={toggleSaved}
        title={isSaved ? "Remove from saved" : "Save venue"}
        type="button"
      >
        {isSaving ? <LoaderCircle aria-hidden="true" className="animate-spin" size={17} /> : <Heart aria-hidden="true" fill={isSaved ? "currentColor" : "none"} size={17} />}
        <span>{isSaved ? "Saved" : "Save"}</span>
      </button>
    );
  }

  return (
    <button
      aria-label={label}
      className={className ?? "grid size-9 place-items-center rounded-full bg-white text-foreground shadow-sm hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-60"}
      disabled={disabled}
      onClick={toggleSaved}
      title={isSaved ? "Remove from saved" : "Save venue"}
      type="button"
    >
      {isSaving ? <LoaderCircle aria-hidden="true" className="animate-spin" size={18} /> : <Heart aria-hidden="true" fill={isSaved ? "currentColor" : "none"} size={18} />}
    </button>
  );
}
