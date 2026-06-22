package com.staminal.venue.vendors.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendors.Dto.VendorCategoryResponse;
import com.staminal.venue.vendors.Service.VendorCategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-categories")
@RequiredArgsConstructor
public class VendorCategoryController {

    private final VendorCategoryService
            vendorCategoryService;

    @GetMapping
    public List<VendorCategoryResponse>
            getAllCategories() {

        return vendorCategoryService
                .getAllCategories();
    }
}