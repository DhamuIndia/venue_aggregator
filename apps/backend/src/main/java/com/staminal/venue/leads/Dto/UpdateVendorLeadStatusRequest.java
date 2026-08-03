package com.staminal.venue.leads.Dto;

import com.staminal.venue.enums.VendorLeadStatus;

public class UpdateVendorLeadStatusRequest {

    private VendorLeadStatus status;

    private String reason;

    public VendorLeadStatus getStatus() {
        return status;
    }

    public void setStatus(VendorLeadStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

}
