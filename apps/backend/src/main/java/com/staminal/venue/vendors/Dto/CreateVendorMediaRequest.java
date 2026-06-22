package com.staminal.venue.vendors.Dto;

import com.staminal.venue.enums.VendorServiceType;

public class CreateVendorMediaRequest {

    private Long vendorId;

    private String mediaUrl;

    private boolean primary;

    private VendorServiceType serviceType;

    private Long serviceId;

    public VendorServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(VendorServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}