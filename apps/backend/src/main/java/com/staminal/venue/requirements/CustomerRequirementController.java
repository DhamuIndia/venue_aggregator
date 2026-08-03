package com.staminal.venue.requirements;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.requirements.dto.CreateCustomerRequirementRequest;
import com.staminal.venue.requirements.dto.CustomerRequirementOptionsResponse;
import com.staminal.venue.requirements.dto.CustomerRequirementResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Validated
public class CustomerRequirementController {

    private final CustomerRequirementService customerRequirementService;

    @GetMapping("/public/requirements/options")
    public CustomerRequirementOptionsResponse getOptions() {
        return customerRequirementService.getOptions();
    }

    @PostMapping("/customer/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerRequirementResponse create(
            @Valid @RequestBody CreateCustomerRequirementRequest request,
            Authentication authentication) {
        return customerRequirementService.create(request, authentication);
    }

    @GetMapping("/customer/requirements")
    public List<CustomerRequirementResponse> getMine(Authentication authentication) {
        return customerRequirementService.getMine(authentication);
    }

    @GetMapping("/customer/requirements/{requirementId}")
    public CustomerRequirementResponse getMine(
            @PathVariable Long requirementId,
            Authentication authentication) {
        return customerRequirementService.getMine(requirementId, authentication);
    }
}
