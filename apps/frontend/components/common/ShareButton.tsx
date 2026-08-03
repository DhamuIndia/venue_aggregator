"use client";

import { Check, Share2 } from "lucide-react";
import { useState } from "react";

type ShareButtonProps = {
  label: string;
  text?: string;
  title?: string;
};

export function ShareButton({ label, text, title }: ShareButtonProps) {
  const [copied, setCopied] = useState(false);

  async function share() {
    const url = window.location.href;
    const payload = { title: title ?? document.title, text, url };

    try {
      if (navigator.share) {
        await navigator.share(payload);
        return;
      }

      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  }

  return (
    <button
      aria-label={copied ? "Link copied" : label}
      className="inline-flex size-10 items-center justify-center rounded-md border border-border bg-white text-muted-foreground hover:border-primary hover:text-primary"
      onClick={share}
      title={copied ? "Link copied" : label}
      type="button"
    >
      {copied ? <Check aria-hidden="true" size={17} /> : <Share2 aria-hidden="true" size={17} />}
    </button>
  );
}
