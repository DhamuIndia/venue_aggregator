package com.staminal.venue.quotes;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.quotes.dto.QuoteAcceptanceResponse;
import com.staminal.venue.quotes.dto.UpsertVendorQuoteRequest;
import com.staminal.venue.quotes.dto.UpdateQuoteShortlistRequest;
import com.staminal.venue.quotes.dto.VendorQuoteResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Validated
public class VendorQuoteController {

    private final VendorQuoteService vendorQuoteService;
    private final VendorQuoteAcceptanceService vendorQuoteAcceptanceService;

    @PutMapping("/vendor/leads/{leadId}/quote")
    public VendorQuoteResponse saveQuote(
            @PathVariable Long leadId,
            @Valid @RequestBody UpsertVendorQuoteRequest request,
            Authentication authentication) {
        return vendorQuoteService.saveQuote(leadId, request, authentication);
    }

    @GetMapping("/vendor/quotes")
    public List<VendorQuoteResponse> getMyVendorQuotes(Authentication authentication) {
        return vendorQuoteService.getMyVendorQuotes(authentication);
    }

    @GetMapping("/customer/quotes")
    public List<VendorQuoteResponse> getMyCustomerQuotes(Authentication authentication) {
        return vendorQuoteService.getMyCustomerQuotes(authentication);
    }

    @PatchMapping("/customer/quotes/{quoteId}/shortlist")
    public VendorQuoteResponse updateShortlist(
            @PathVariable Long quoteId,
            @Valid @RequestBody UpdateQuoteShortlistRequest request,
            Authentication authentication) {
        return vendorQuoteService.updateShortlist(quoteId, request, authentication);
    }

    @PostMapping("/customer/quotes/{quoteId}/accept")
    public QuoteAcceptanceResponse acceptQuote(
            @PathVariable Long quoteId,
            Authentication authentication) {
        return vendorQuoteAcceptanceService.accept(quoteId, authentication);
    }
}
