export type SocialPlatform = "instagram" | "facebook" | "whatsapp";

const labels: Record<SocialPlatform, string> = {
  instagram: "Instagram",
  facebook: "Facebook",
  whatsapp: "WhatsApp"
};

const allowedHosts: Record<SocialPlatform, string[]> = {
  instagram: ["instagram.com"],
  facebook: ["facebook.com", "fb.com"],
  whatsapp: ["wa.me", "whatsapp.com"]
};

export function normalizeSocialProfileInput(platform: SocialPlatform, value: string) {
  const input = value.trim();
  if (!input) return "";

  let candidate = input;
  if (platform === "instagram" && /^@?[A-Za-z0-9._]{1,30}$/.test(candidate)) {
    candidate = `https://instagram.com/${candidate.replace(/^@/, "")}`;
  } else if (platform === "facebook" && /^@?[A-Za-z0-9.]{5,50}$/.test(candidate)) {
    candidate = `https://facebook.com/${candidate.replace(/^@/, "")}`;
  } else if (platform === "whatsapp" && /^[+\d()\s-]{10,24}$/.test(candidate)) {
    let digits = candidate.replace(/\D/g, "");
    if (digits.length === 10) digits = `91${digits}`;
    if (digits.length >= 10 && digits.length <= 15) candidate = `https://wa.me/${digits}`;
  }

  if (!/^https?:\/\//i.test(candidate)) candidate = `https://${candidate}`;
  candidate = candidate.replace(/^http:\/\//i, "https://");

  try {
    const url = new URL(candidate);
    const hostname = url.hostname.toLowerCase();
    const validHost = allowedHosts[platform].some((host) => hostname === host || hostname.endsWith(`.${host}`));
    const hasProfilePath = url.pathname.length > 1;
    if (url.protocol !== "https:" || !validHost || !hasProfilePath) throw new Error();
    return url.toString();
  } catch {
    throw new Error(`Enter a valid ${labels[platform]} profile link.`);
  }
}
