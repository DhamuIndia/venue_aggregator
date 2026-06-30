package com.staminal.venue.vendors.Dto;

import java.math.BigDecimal;
import java.util.List;

import com.staminal.venue.enums.VendorServiceType;

public class VendorPackageResponse {

    private Long id;

    private String packageName;

    private String name;

    private String description;

    private BigDecimal price;

    private VendorServiceType serviceType;

    private Long serviceId;

    private List<String> includes;

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

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
        this.name = packageName;
    }

    public String getName() {
        return name != null ? name : packageName;
    }

    public void setName(String name) {
        this.name = name;
        this.packageName = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
