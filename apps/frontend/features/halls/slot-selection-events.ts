import type { HallBaseSlot } from "./slot-model";

export const HALL_SLOT_SELECTION_EVENT = "venue:hall-slot-selected";

export type HallSlotSelectionDetail = {
  hallId: string;
  date: string;
  slot: HallBaseSlot;
};

export function emitHallSlotSelection(detail: HallSlotSelectionDetail) {
  window.dispatchEvent(new CustomEvent<HallSlotSelectionDetail>(HALL_SLOT_SELECTION_EVENT, { detail }));
}
