package com.staminal.venue.leads.Dto;

import com.staminal.venue.enums.VendorLeadStatus;

public class UpdateVendorLeadStatusRequest {

    private VendorLeadStatus status;

    public VendorLeadStatus getStatus() {
        return status;
    }

    public void setStatus(VendorLeadStatus status) {
        this.status = status;
    }

}
