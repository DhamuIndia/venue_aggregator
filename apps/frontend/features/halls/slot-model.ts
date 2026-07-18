export const HALL_BASE_SLOTS = ["MORNING", "AFTERNOON", "EVENING"] as const;

export type HallBaseSlot = typeof HALL_BASE_SLOTS[number];
export type HallSlot = HallBaseSlot | "FULL_DAY";

export type HallSlotRequest = {
  date: string;
  slot: HallBaseSlot;
  eventType?: string;
};

export type HallSlotCombinationId =
  | "MORNING"
  | "AFTERNOON"
  | "EVENING"
  | "MORNING_AFTERNOON"
  | "AFTERNOON_EVENING"
  | "FULL_DAY"
  | "EVENING_NEXT_MORNING";

export type HallSlotCombination = {
  id: HallSlotCombinationId;
  label: string;
  description: string;
  slots: HallBaseSlot[];
  nextDaySlots?: HallBaseSlot[];
};

export const HALL_SLOT_DETAILS: Record<HallBaseSlot, { label: string; time: string; shortTime: string }> = {
  MORNING: { label: "Morning", time: "6:00 AM - 12:00 PM", shortTime: "6 AM - 12 PM" },
  AFTERNOON: { label: "Afternoon", time: "12:00 PM - 4:00 PM", shortTime: "12 PM - 4 PM" },
  EVENING: { label: "Evening", time: "4:00 PM - 11:00 PM", shortTime: "4 PM - 11 PM" }
};

export const HALL_SLOT_COMBINATIONS: HallSlotCombination[] = [
  {
    id: "MORNING",
    label: "Morning only",
    description: HALL_SLOT_DETAILS.MORNING.shortTime,
    slots: ["MORNING"]
  },
  {
    id: "AFTERNOON",
    label: "Afternoon only",
    description: HALL_SLOT_DETAILS.AFTERNOON.shortTime,
    slots: ["AFTERNOON"]
  },
  {
    id: "EVENING",
    label: "Evening only",
    description: HALL_SLOT_DETAILS.EVENING.shortTime,
    slots: ["EVENING"]
  },
  {
    id: "MORNING_AFTERNOON",
    label: "Morning + afternoon",
    description: "6 AM - 4 PM",
    slots: ["MORNING", "AFTERNOON"]
  },
  {
    id: "AFTERNOON_EVENING",
    label: "Afternoon + evening",
    description: "12 PM - 11 PM",
    slots: ["AFTERNOON", "EVENING"]
  },
  {
    id: "FULL_DAY",
    label: "Full day",
    description: "6 AM - 11 PM",
    slots: ["MORNING", "AFTERNOON", "EVENING"]
  },
  {
    id: "EVENING_NEXT_MORNING",
    label: "Evening + next morning",
    description: "4 PM - 11 PM, then 6 AM - 12 PM next day",
    slots: ["EVENING"],
    nextDaySlots: ["MORNING"]
  }
];

export function isHallSlot(value: unknown): value is HallSlot {
  return value === "MORNING" || value === "AFTERNOON" || value === "EVENING" || value === "FULL_DAY";
}

export function isHallBaseSlot(value: unknown): value is HallBaseSlot {
  return value === "MORNING" || value === "AFTERNOON" || value === "EVENING";
}

export function slotLabel(slot: HallSlot) {
  if (slot === "FULL_DAY") return "Full day";
  return HALL_SLOT_DETAILS[slot].label;
}

export function slotTime(slot: HallSlot) {
  if (slot === "FULL_DAY") return "6:00 AM - 11:00 PM";
  return HALL_SLOT_DETAILS[slot].time;
}

export function formatSlot(slot: HallSlot) {
  const time = slotTime(slot);
  return `${slotLabel(slot)} (${time})`;
}

export function getSlotCombination(id: HallSlotCombinationId) {
  return HALL_SLOT_COMBINATIONS.find((combination) => combination.id === id) ?? HALL_SLOT_COMBINATIONS[0];
}

export function buildSlotRequests(date: string, combinationId: HallSlotCombinationId): HallSlotRequest[] {
  const combination = getSlotCombination(combinationId);
  return [
    ...combination.slots.map((slot) => ({ date, slot })),
    ...(combination.nextDaySlots ?? []).map((slot) => ({ date: addDays(date, 1), slot }))
  ];
}

export function representativeSlot(requests: HallSlotRequest[]): HallSlot {
  if (requests.length === 3 && requests.every((request) => request.date === requests[0]?.date)) return "FULL_DAY";
  return requests[0]?.slot ?? "MORNING";
}

export function formatSlotRequests(requests: HallSlotRequest[]) {
  return requests
    .map((request) => {
      const eventType = request.eventType ? ` - ${request.eventType}` : "";
      return `${formatDisplayDate(request.date)} ${slotLabel(request.slot)}${eventType}`;
    })
    .join(", ");
}

export function addDays(date: string, days: number) {
  const next = new Date(`${date}T00:00:00`);
  next.setDate(next.getDate() + days);
  return toDateInputValue(next);
}

export function toDateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function formatDisplayDate(date: string) {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(new Date(`${date}T00:00:00`));
}

export function slotConflicts(left: HallSlot, right: HallSlot) {
  return left === "FULL_DAY" || right === "FULL_DAY" || left === right;
}
