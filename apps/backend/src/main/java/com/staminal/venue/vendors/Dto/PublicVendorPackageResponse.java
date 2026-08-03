package com.staminal.venue.vendors.Dto;

import java.math.BigDecimal;
import java.util.List;

public record PublicVendorPackageResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        List<String> includes) {
}
