package com.staminal.venue.vendors.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.vendors.Dto.VendorCategoryResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorCategoryService {

    private final VendorCategoryRepository vendorCategoryRepository;

    public List<VendorCategoryResponse> getAllCategories() {

        List<VendorCategory> categories =
                vendorCategoryRepository.findAll();

        return categories.stream()
                .map(category -> {

                    VendorCategoryResponse response =
                            new VendorCategoryResponse();

                    response.setId(category.getId());
                    response.setCategoryName(
                            category.getCategoryName());

                    return response;
                })
                .toList();
    }
}