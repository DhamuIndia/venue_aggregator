package com.staminal.venue.vendors.Dto;

import com.staminal.venue.enums.VendorServiceType;

public class VendorMediaResponse {

    private Long id;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
