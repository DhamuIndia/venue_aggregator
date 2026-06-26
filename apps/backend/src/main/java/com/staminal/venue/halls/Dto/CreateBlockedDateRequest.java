package com.staminal.venue.halls.Dto;

import java.time.LocalDate;

public class CreateBlockedDateRequest {

    private LocalDate eventDate;

    private String slotType;

    private String reason;

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getSlotType() {
        return slotType;
    }

    public void setSlotType(String slotType) {
        this.slotType = slotType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

}
