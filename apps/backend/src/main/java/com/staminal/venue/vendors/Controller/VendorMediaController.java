package com.staminal.venue.vendors.Controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.staminal.venue.vendors.Dto.CreateVendorMediaRequest;
import com.staminal.venue.vendors.Dto.VendorMediaResponse;
import com.staminal.venue.vendors.Service.VendorMediaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-media")
@RequiredArgsConstructor
public class VendorMediaController {

    private final VendorMediaService vendorMediaService;

    @PostMapping
    public VendorMediaResponse createMedia(
            @RequestBody CreateVendorMediaRequest request) {

        return vendorMediaService.createMedia(request);
    }

    @GetMapping("/{vendorId}")
    public List<VendorMediaResponse> getVendorMedia(
            @PathVariable Long vendorId) {

        return vendorMediaService.getVendorMedia(vendorId);
    }
}