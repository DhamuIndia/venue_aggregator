export type PreferredContactChannel = "IN_APP" | "PHONE" | "WHATSAPP" | "EMAIL";
export type CustomerRequirementStatus = "OPEN" | "CLOSED" | "CANCELLED" | "EXPIRED";

export type RequirementCategory = {
  id: number;
  name: string;
};

export type RequirementOptions = {
  enabled: boolean;
  categories: RequirementCategory[];
};

export type CreateCustomerRequirementInput = {
  categoryIds: number[];
  eventType: string;
  eventDate: string;
  location: string;
  city: string;
  pincode?: string;
  budgetMin?: number;
  budgetMax?: number;
  guestCount?: number;
  details?: string;
  preferredContactChannel: PreferredContactChannel;
  shareContactDetails: boolean;
};

export type CustomerRequirement = {
  id: string;
  eventType: string;
  eventDate: string;
  location: string;
  city?: string;
  pincode?: string;
  budgetMin?: number;
  budgetMax?: number;
  guestCount?: number;
  details?: string;
  preferredContactChannel: PreferredContactChannel;
  shareContactDetails: boolean;
  status: CustomerRequirementStatus;
  services: RequirementCategory[];
  createdAt: string;
  updatedAt: string;
};
