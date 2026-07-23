package com.staminal.venue.quotes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertVendorQuoteRequest(
        @NotNull(message = "Quote amount is required")
        @DecimalMin(value = "0.01", message = "Quote amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Quote amount is too large")
        BigDecimal amount,

        @NotBlank(message = "Package name is required")
        @Size(max = 160, message = "Package name must be 160 characters or fewer")
        String packageName,

        @NotBlank(message = "Service description is required")
        @Size(max = 2000, message = "Service description must be 2000 characters or fewer")
        String serviceDescription,

        @NotEmpty(message = "Add at least one inclusion")
        @Size(max = 20, message = "A quote can contain at most 20 inclusions")
        List<@NotBlank(message = "Quote inclusions cannot be blank")
                @Size(max = 300, message = "Each inclusion must be 300 characters or fewer") String> inclusions,

        @DecimalMin(value = "0.00", message = "Additional charges cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Additional charges are too large")
        BigDecimal additionalCharges,

        @Size(max = 1000, message = "Additional charge details must be 1000 characters or fewer")
        String additionalChargesDescription,

        @Size(max = 2000, message = "Quote notes must be 2000 characters or fewer")
        String notes,

        @NotNull(message = "Quote validity date is required")
        @FutureOrPresent(message = "Quote validity date cannot be in the past")
        LocalDate validUntil) {
}
