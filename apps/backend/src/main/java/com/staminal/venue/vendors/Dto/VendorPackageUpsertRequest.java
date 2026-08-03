package com.staminal.venue.vendors.Dto;

import java.math.BigDecimal;
import java.util.List;

public record VendorPackageUpsertRequest(
        String name,
        String packageName,
        String description,
        BigDecimal price,
        List<String> includes) {
}
