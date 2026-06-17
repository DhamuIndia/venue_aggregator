package com.staminal.venue.vendors.Dto;

import java.time.LocalDate;

import com.staminal.venue.enums.SlotType;

public class VendorBlockedDateResponse {

    private Long id;

    private LocalDate eventDate;

    private SlotType slotType;

    private String reason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }
}