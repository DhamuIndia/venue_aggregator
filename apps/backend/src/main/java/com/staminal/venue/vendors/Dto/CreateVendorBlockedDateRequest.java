package com.staminal.venue.vendors.Dto;

import java.time.LocalDate;

import com.staminal.venue.enums.SlotType;

public class CreateVendorBlockedDateRequest {

    private Long vendorId;

    private LocalDate eventDate;

    private SlotType slotType;

    private String reason;

    public SlotType getSlotType() {
        return slotType;
    }

    public void setSlotType(SlotType slotType) {
        this.slotType = slotType;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
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
}