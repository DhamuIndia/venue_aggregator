import type { HallSlot, HallSlotRequest } from "@/features/halls/slot-model";

export type EnquiryStatus =
  | "NEW"
  | "PENDING_OWNER_RESPONSE"
  | "CONFIRMED"
  | "DECLINED"
  | "COMPLETED";

export type EnquirySlot = HallSlot;

export type CreateEnquiryPayload = {
  hallId: string;
  hallName: string;
  customerId: string;
  eventDate: string;
  eventType: string;
  guestCount: number;
  slot: EnquirySlot;
  slotRequests?: HallSlotRequest[];
  notes?: string;
};

export type StoredEnquiry = CreateEnquiryPayload & {
  id: string;
  status: EnquiryStatus;
  submittedAt: string;

  customerName?: string;
  customerPhone?: string;
  customerEmail?: string;

  createdAt?:string;
  updatedAt?: string;
  ownerResponseMessage?: string;
  version?: number;
};
