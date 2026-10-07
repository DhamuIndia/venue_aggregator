import type { OvertureAmenities, OvertureDraftFacts, OvertureFactField } from "./overture-client";

export const overtureAmenityLabels: Record<keyof OvertureAmenities, string> = {
  ac: "Air conditioning", carParking: "Car parking", bikeParking: "Bike parking", dining: "Dining space",
  generator: "Generator", lift: "Lift", bridalRoom: "Bridal room", cateringKitchen: "Catering kitchen"
};
export const overtureFieldLabels: Record<OvertureFactField, string> = {
  name: "Venue name", address: "Address", city: "City", area: "Area", postcode: "Postcode",
  latitude: "Latitude", longitude: "Longitude", phone: "Phone", website: "Website",
  operatingStatus: "Operating status", capacity: "Capacity", description: "Description",
  ...Object.fromEntries(Object.entries(overtureAmenityLabels).map(([key, label]) => [`amenities.${key}`, label]))
} as Record<OvertureFactField, string>;
export const overtureIdentityFields: OvertureFactField[] = ["name", "city", "latitude", "longitude"];
export const overtureRequiredVerifiedFields: OvertureFactField[] = ["name", "address", "city", "area", "phone", "latitude", "longitude", "operatingStatus", "capacity"];
export type OvertureReviewForm = Record<OvertureFactField, string>;

export function overtureFactValue(facts: OvertureDraftFacts, field: OvertureFactField): string | number | boolean | null {
  if (field.startsWith("amenities.")) return facts.amenities[field.slice(10) as keyof OvertureAmenities];
  return facts[field as Exclude<keyof OvertureDraftFacts, "amenities">];
}

export function overtureFormFromFacts(facts: OvertureDraftFacts): OvertureReviewForm {
  return Object.fromEntries(Object.keys(overtureFieldLabels).map((key) => {
    const field = key as OvertureFactField;
    const value = overtureFactValue(facts, field);
    return [field, value === null || value === undefined ? "" : typeof value === "boolean" ? value ? "yes" : "no" : String(value)];
  })) as OvertureReviewForm;
}

export function overtureFactsFromForm(form: OvertureReviewForm): OvertureDraftFacts {
  const text = (field: OvertureFactField) => form[field].trim() || null;
  function number(field: "latitude" | "longitude" | "capacity", min: number, max: number, integer = false) {
    const value = text(field);
    if (value === null) return null;
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) throw new Error(`${overtureFieldLabels[field]} must be a number.`);
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed < min || parsed > max || (integer && !Number.isInteger(parsed))) throw new Error(`${overtureFieldLabels[field]} must be ${integer ? "a whole number " : ""}between ${min} and ${max}.`);
    return parsed;
  }
  const name = text("name");
  if (!name) throw new Error("Venue name is required.");
  const latitude = number("latitude", -90, 90);
  const longitude = number("longitude", -180, 180);
  if ((latitude === null) !== (longitude === null)) throw new Error("Supply both latitude and longitude, or leave both unknown.");
  const phone = text("phone");
  if (phone !== null && (phone.length > 20 || !/^\+?[0-9() -]+$/.test(phone) || phone.replace(/[^0-9]/g, "").length < 6)) throw new Error("Phone must be a business number with at least six digits. Use digits, spaces, brackets, hyphens and an optional leading +.");
  const amenities = Object.fromEntries(Object.keys(overtureAmenityLabels).map((key) => {
    const value = form[`amenities.${key}` as OvertureFactField];
    if (!["", "yes", "no"].includes(value)) throw new Error("Choose Unknown, Yes or No for each amenity.");
    return [key, value === "" ? null : value === "yes"];
  })) as OvertureAmenities;
  return { name, address: text("address"), city: text("city"), area: text("area"), postcode: text("postcode"), latitude, longitude,
    phone, website: text("website"), operatingStatus: text("operatingStatus"), capacity: number("capacity", 1, 100000, true), description: text("description"), amenities };
}

export function overtureDisplayFact(value: string | number | boolean | null | undefined): string {
  return value === null || value === undefined || value === "" ? "Unknown / not supplied" : typeof value === "boolean" ? value ? "Yes" : "No" : String(value);
}
