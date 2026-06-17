package com.staminal.venue.vendors.Controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.staminal.venue.vendors.Dto.CreateVendorBlockedDateRequest;
import com.staminal.venue.vendors.Dto.VendorBlockedDateResponse;
import com.staminal.venue.vendors.Service.VendorBlockedDateService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-blocked-dates")
@RequiredArgsConstructor
public class VendorBlockedDateController {

    private final VendorBlockedDateService vendorBlockedDateService;

    @PostMapping
    public VendorBlockedDateResponse createBlockedDate(
            @RequestBody CreateVendorBlockedDateRequest request) {

        return vendorBlockedDateService
                .createBlockedDate(request);
    }

    @GetMapping("/{vendorId}")
    public List<VendorBlockedDateResponse> getBlockedDates(
            @PathVariable Long vendorId) {

        return vendorBlockedDateService
                .getBlockedDates(vendorId);
    }
}