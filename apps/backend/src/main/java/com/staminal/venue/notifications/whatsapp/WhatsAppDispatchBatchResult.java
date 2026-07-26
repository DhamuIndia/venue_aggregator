package com.staminal.venue.notifications.whatsapp;

public record WhatsAppDispatchBatchResult(
        boolean sendingDisabled,
        int claimed,
        int submitted,
        int cancelled,
        int failed) {

    public static WhatsAppDispatchBatchResult disabled() {
        return new WhatsAppDispatchBatchResult(true, 0, 0, 0, 0);
    }
}
