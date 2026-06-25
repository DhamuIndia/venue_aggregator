export type EnquiryStatus =
  | "NEW"
  | "PENDING_OWNER_RESPONSE"
  | "CONFIRMED"
  | "DECLINED"
  | "COMPLETED";

export type EnquirySlot = "MORNING" | "EVENING" | "FULL_DAY";

export type CreateEnquiryPayload = {
  hallId: string;
  hallName: string;
  customerId: string;
  eventDate: string;
  eventType: string;
  guestCount: number;
  slot: EnquirySlot;
  notes?: string;
};

export type StoredEnquiry = CreateEnquiryPayload & {
  id: string;
  status: EnquiryStatus;
  submittedAt: string;
};
