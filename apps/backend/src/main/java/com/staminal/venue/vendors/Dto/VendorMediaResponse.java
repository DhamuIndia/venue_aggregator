package com.staminal.venue.vendors.Dto;

import com.staminal.venue.enums.VendorServiceType;

public class VendorMediaResponse {

    private Long id;

    private String url;

    private String mediaUrl;

    private boolean primary;

    private boolean isCover;

    private String storageKey;

    private String fileName;

    private String caption;

    private Integer sortOrder;

    private String mediaType;

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

    public String getUrl() {
        return url != null ? url : mediaUrl;
    }

    public void setUrl(String url) {
        this.url = url;
        this.mediaUrl = url;
    }

    public String getMediaUrl() {
        return mediaUrl != null ? mediaUrl : url;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
        this.url = mediaUrl;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
        this.isCover = primary;
    }

    public boolean getIsCover() {
        return isCover;
    }

    public void setIsCover(boolean isCover) {
        this.isCover = isCover;
        this.primary = isCover;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }
}
