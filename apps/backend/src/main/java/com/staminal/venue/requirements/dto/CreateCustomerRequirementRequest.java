package com.staminal.venue.requirements.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.staminal.venue.enums.PreferredContactChannel;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequirementRequest(
        @NotEmpty(message = "Select at least one service")
        Set<@Positive(message = "Service category is invalid") Long> categoryIds,

        @NotBlank(message = "Event type is required")
        @Size(max = 120, message = "Event type must be 120 characters or fewer")
        String eventType,

        @NotNull(message = "Event date is required")
        @FutureOrPresent(message = "Event date cannot be in the past")
        LocalDate eventDate,

        @NotBlank(message = "Location is required")
        @Size(max = 255, message = "Location must be 255 characters or fewer")
        String location,

        @NotBlank(message = "City is required")
        @Size(max = 120, message = "City must be 120 characters or fewer")
        String city,

        @Pattern(regexp = "^(?:|\\d{6})$", message = "Pincode must contain 6 digits")
        String pincode,

        @DecimalMin(value = "0.00", message = "Minimum budget cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Minimum budget is too large")
        BigDecimal budgetMin,

        @DecimalMin(value = "0.00", message = "Maximum budget cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Maximum budget is too large")
        BigDecimal budgetMax,

        @Positive(message = "Guest count must be greater than zero")
        Integer guestCount,

        @Size(max = 2000, message = "Requirement details must be 2000 characters or fewer")
        String details,

        @NotNull(message = "Preferred contact method is required")
        PreferredContactChannel preferredContactChannel,

        @NotNull(message = "Contact sharing preference is required")
        Boolean shareContactDetails) {
}
