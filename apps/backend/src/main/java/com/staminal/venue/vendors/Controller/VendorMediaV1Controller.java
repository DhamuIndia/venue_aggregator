package com.staminal.venue.vendors.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendors.Dto.CreateVendorMediaRequest;
import com.staminal.venue.vendors.Dto.VendorMediaResponse;
import com.staminal.venue.vendors.Service.VendorMediaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/vendor/media")
@RequiredArgsConstructor
public class VendorMediaV1Controller {

    private final VendorMediaService vendorMediaService;

    @GetMapping
    public List<VendorMediaResponse> getMedia(Authentication authentication) {
        return vendorMediaService.getMyMedia(authentication);
    }

    @PostMapping
    public VendorMediaResponse createMedia(
            @RequestBody CreateVendorMediaRequest request,
            Authentication authentication) {
        return vendorMediaService.createMyMedia(request, authentication);
    }

    @PatchMapping("/{mediaId}")
    public VendorMediaResponse updateMedia(
            @PathVariable String mediaId,
            @RequestBody CreateVendorMediaRequest request,
            Authentication authentication) {
        return vendorMediaService.updateMyMedia(mediaId, request, authentication);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(
            @PathVariable String mediaId,
            Authentication authentication) {
        vendorMediaService.deleteMyMedia(mediaId, authentication);
    }
}
