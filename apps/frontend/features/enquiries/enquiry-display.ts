import type { EnquiryStatus } from "./types";

export function venueMartEnquiryStatus(status: EnquiryStatus) {
  if (status === "NEW") return "Awaiting VenueMart team response";
  if (status === "CONTACTED") return "VenueMart team contacted you";
  if (status === "CLOSED") return "Enquiry closed — not a booking";
  return "VenueMart team enquiry";
}
