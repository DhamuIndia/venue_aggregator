package com.staminal.venue.vendors.Dto;

import java.util.List;

public record VendorPackageListResponse(
        List<VendorPackageResponse> content) {
}
