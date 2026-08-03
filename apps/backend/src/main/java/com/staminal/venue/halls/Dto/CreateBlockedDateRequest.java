package com.staminal.venue.halls.Dto;

import java.time.LocalDate;

public class CreateBlockedDateRequest {

    private LocalDate date;

    private String slot;

    private String reason;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

}
