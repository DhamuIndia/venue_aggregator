const uppercaseWords = new Set(["AC", "BP", "DJ", "LED", "TV", "VIP"]);

export const guestCapacityOptions = [100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 5000];

export function toTitleCase(value: string) {
  return value
    .trim()
    .replace(/\s+/g, " ")
    .replace(/[A-Za-z][A-Za-z0-9'.-]*/g, (word) => {
      const normalized = word.toUpperCase();
      if (uppercaseWords.has(normalized)) return normalized;
      if (/^NO\.?\d*$/i.test(word)) return word.replace(/^no/i, "No");
      return word
        .toLowerCase()
        .replace(/(^|[-'])([a-z])/g, (_match, prefix: string, letter: string) => `${prefix}${letter.toUpperCase()}`);
    });
}

export function formatGuestCount(value: number | string) {
  const numericValue = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numericValue)) return "0";
  return new Intl.NumberFormat("en-IN").format(numericValue);
}

export function formatGuestCapacityOption(value: number | string) {
  return `Up to ${formatGuestCount(value)} guests`;
}
