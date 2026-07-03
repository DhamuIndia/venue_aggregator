package com.staminal.venue.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.enquiries.EnquiryRepository;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportsService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final Set<BookingStatus> BOOKED_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
    private static final Set<EnquiryStatus> VENDOR_RESPONDED_STATUSES = EnumSet.of(
            EnquiryStatus.CONTACTED,
            EnquiryStatus.CONFIRMED,
            EnquiryStatus.DECLINED,
            EnquiryStatus.COMPLETED,
            EnquiryStatus.CLOSED);
    private static final Set<EnquiryStatus> VENDOR_BOOKED_STATUSES = EnumSet.of(
            EnquiryStatus.CONFIRMED,
            EnquiryStatus.COMPLETED,
            EnquiryStatus.CLOSED);

    private final UserRepository userRepository;
    private final HallRepository hallRepository;
    private final VendorRepository vendorRepository;
    private final EnquiryRepository enquiryRepository;
    private final BookingRepository bookingRepository;

    public AdminReportResponse getAdminSummary(Authentication authentication) {
        currentUser(authentication, UserRole.ADMIN, UserRole.SUPER_ADMIN);

        List<Enquiry> enquiries = enquiryRepository.findAll();
        List<Booking> bookings = bookingRepository.findAll();
        List<Halls> halls = hallRepository.findAll();
        List<Vendors> vendors = vendorRepository.findAll();

        YearMonth currentMonth = YearMonth.now(REPORT_ZONE);
        long monthlyEnquiries = enquiries.stream()
                .filter(enquiry -> currentMonth.equals(monthOf(enquiry.getCreatedAt())))
                .count();
        long confirmedBookings = bookings.stream()
                .filter(this::isBooked)
                .count();

        return new AdminReportResponse(
                userRepository.count(),
                activeListings(halls, vendors),
                monthlyEnquiries,
                confirmedBookings,
                bookingRevenue(bookings),
                BigDecimal.ZERO,
                percentage(confirmedBookings, enquiries.size()),
                trends(enquiries, bookings, enquiry -> true, booking -> true),
                topCities(enquiries, bookings));
    }

    public OwnerReportResponse getOwnerHallSummary(String hallId, Authentication authentication) {
        User owner = currentUser(authentication, UserRole.HALL_OWNER);
        Halls hall = findOwnerHall(hallId, owner);

        List<Enquiry> enquiries = enquiryRepository.findByHall_IdOrderByCreatedAtDesc(hall.getId());
        List<Booking> bookings = bookingRepository.findByHall_IdOrderByEventDateDesc(hall.getId());
        long confirmedBookings = bookings.stream().filter(this::isBooked).count();
        long completedBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .count();

        return new OwnerReportResponse(
                enquiries.size(),
                confirmedBookings,
                completedBookings,
                bookingRevenue(bookings),
                percentage(confirmedBookings, enquiries.size()),
                hall.getRatings() != null ? hall.getRatings() : 0,
                occupancyRate(bookings),
                trends(enquiries, bookings, enquiry -> true, booking -> true),
                eventMix(enquiries));
    }

    public VendorReportResponse getVendorSummary(String vendorId, Authentication authentication) {
        User user = currentUser(authentication, UserRole.VENDOR);
        Vendors vendor = vendorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor profile not found"));
        assertRequestedVendorMatches(vendor, vendorId);

        List<Enquiry> leads = enquiryRepository.findAll()
                .stream()
                .filter(enquiry -> enquiry.getVendor() != null)
                .filter(enquiry -> vendor.getId().equals(enquiry.getVendor().getId()))
                .toList();

        long contacted = leads.stream().filter(this::isVendorResponded).count();
        long booked = leads.stream().filter(this::isVendorBooked).count();
        BigDecimal bookedValue = leads.stream()
                .filter(this::isVendorBooked)
                .map(enquiry -> vendorLeadValue(enquiry, vendor))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VendorReportResponse(
                leads.size(),
                contacted,
                booked,
                booked,
                bookedValue,
                percentage(booked, leads.size()),
                averageLeadValue(leads, vendor),
                percentage(contacted, leads.size()),
                vendorTrends(leads, vendor),
                serviceMix(leads, vendor));
    }

    private long activeListings(List<Halls> halls, List<Vendors> vendors) {
        long approvedHalls = halls.stream()
                .filter(hall -> hall.getStatus() == HallStatus.APPROVED)
                .count();
        long approvedVendors = vendors.stream()
                .filter(vendor -> vendor.getStatus() == VendorStatus.APPROVED)
                .count();
        return approvedHalls + approvedVendors;
    }

    private List<TrendPointResponse> trends(
            List<Enquiry> enquiries,
            List<Booking> bookings,
            java.util.function.Predicate<Enquiry> enquiryFilter,
            java.util.function.Predicate<Booking> bookingFilter) {
        return lastSixMonths().stream()
                .map(month -> {
                    long enquiryCount = enquiries.stream()
                            .filter(enquiryFilter)
                            .filter(enquiry -> month.equals(monthOf(enquiry.getCreatedAt())))
                            .count();
                    List<Booking> monthBookings = bookings.stream()
                            .filter(bookingFilter)
                            .filter(this::isBooked)
                            .filter(booking -> month.equals(monthOf(booking.getEventDate())))
                            .toList();
                    return new TrendPointResponse(
                            month.format(MONTH_LABEL),
                            enquiryCount,
                            monthBookings.size(),
                            bookingRevenue(monthBookings));
                })
                .toList();
    }

    private List<TrendPointResponse> vendorTrends(List<Enquiry> leads, Vendors vendor) {
        return lastSixMonths().stream()
                .map(month -> {
                    List<Enquiry> monthLeads = leads.stream()
                            .filter(lead -> month.equals(monthOf(lead.getCreatedAt())))
                            .toList();
                    long booked = monthLeads.stream().filter(this::isVendorBooked).count();
                    BigDecimal revenue = monthLeads.stream()
                            .filter(this::isVendorBooked)
                            .map(lead -> vendorLeadValue(lead, vendor))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new TrendPointResponse(month.format(MONTH_LABEL), monthLeads.size(), booked, revenue);
                })
                .toList();
    }

    private List<TopCityResponse> topCities(List<Enquiry> enquiries, List<Booking> bookings) {
        Map<String, CityTotals> totals = new LinkedHashMap<>();
        enquiries.forEach(enquiry -> {
            String city = cityOf(enquiry.getHall());
            totals.computeIfAbsent(city, key -> new CityTotals()).enquiries++;
        });
        bookings.stream()
                .filter(this::isBooked)
                .forEach(booking -> {
                    String city = cityOf(booking.getHall());
                    totals.computeIfAbsent(city, key -> new CityTotals()).bookings++;
                });

        return totals.entrySet()
                .stream()
                .sorted(Comparator
                        .<Map.Entry<String, CityTotals>>comparingLong(entry -> entry.getValue().enquiries + entry.getValue().bookings)
                        .reversed())
                .limit(5)
                .map(entry -> new TopCityResponse(entry.getKey(), entry.getValue().enquiries, entry.getValue().bookings))
                .toList();
    }

    private List<EventMixResponse> eventMix(List<Enquiry> enquiries) {
        return countByLabel(enquiries, enquiry -> label(enquiry.getEventType(), "Event"))
                .entrySet()
                .stream()
                .map(entry -> new EventMixResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ServiceMixResponse> serviceMix(List<Enquiry> leads, Vendors vendor) {
        Map<String, Long> leadMix = countByLabel(leads, lead -> label(lead.getEventType(), "Service"));
        if (!leadMix.isEmpty()) {
            return leadMix.entrySet()
                    .stream()
                    .map(entry -> new ServiceMixResponse(entry.getKey(), entry.getValue()))
                    .toList();
        }

        List<String> services = vendor.getServices();
        if (services == null) {
            return List.of();
        }
        return services.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(service -> new ServiceMixResponse(service, 0))
                .toList();
    }

    private <T> Map<String, Long> countByLabel(List<T> items, Function<T, String> labeler) {
        return items.stream()
                .collect(Collectors.groupingBy(labeler, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private BigDecimal bookingRevenue(List<Booking> bookings) {
        return bookings.stream()
                .filter(this::isBooked)
                .map(Booking::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal averageLeadValue(List<Enquiry> leads, Vendors vendor) {
        if (leads.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = leads.stream()
                .map(lead -> vendorLeadValue(lead, vendor))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(leads.size()), 0, RoundingMode.HALF_UP);
    }

    private BigDecimal vendorLeadValue(Enquiry enquiry, Vendors vendor) {
        if (vendor.getStartingPrice() == null) {
            return BigDecimal.ZERO;
        }
        int guests = enquiry.getGuestCount() != null && enquiry.getGuestCount() > 0 ? enquiry.getGuestCount() : 1;
        return vendor.getStartingPrice().multiply(BigDecimal.valueOf(guests));
    }

    private int occupancyRate(List<Booking> bookings) {
        YearMonth currentMonth = YearMonth.now(REPORT_ZONE);
        long bookedThisMonth = bookings.stream()
                .filter(this::isBooked)
                .filter(booking -> currentMonth.equals(monthOf(booking.getEventDate())))
                .count();
        return Math.min(100, percentage(bookedThisMonth, currentMonth.lengthOfMonth()));
    }

    private boolean isBooked(Booking booking) {
        return booking != null && BOOKED_STATUSES.contains(booking.getStatus());
    }

    private boolean isVendorResponded(Enquiry enquiry) {
        return enquiry != null && VENDOR_RESPONDED_STATUSES.contains(enquiry.getStatus());
    }

    private boolean isVendorBooked(Enquiry enquiry) {
        return enquiry != null && VENDOR_BOOKED_STATUSES.contains(enquiry.getStatus());
    }

    private int percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0) / denominator);
    }

    private Halls findOwnerHall(String hallId, User owner) {
        Halls hall = findHallByIdentifier(hallId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        if (hall.getOwnerUserId() == null || !owner.getId().equals(hall.getOwnerUserId().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall does not belong to this owner");
        }
        return hall;
    }

    private Optional<Halls> findHallByIdentifier(String hallId) {
        Long numericId = tryParseLong(stripPrefix(hallId, "HALL-"));
        if (numericId != null) {
            return hallRepository.findById(numericId);
        }
        String slug = slugify(hallId);
        return hallRepository.findAll()
                .stream()
                .filter(hall -> slug.equals(slugify(hall.getName())))
                .findFirst();
    }

    private void assertRequestedVendorMatches(Vendors vendor, String vendorId) {
        if (vendorId == null || vendorId.isBlank()) {
            return;
        }

        Long numericId = tryParseLong(stripPrefix(vendorId, "VENDOR-"));
        if (numericId != null && vendor.getId().equals(numericId)) {
            return;
        }
        if (numericId == null && slugify(vendorId).equals(slugify(vendor.getBusinessName()))) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendor report does not belong to this user");
    }

    private User currentUser(Authentication authentication, UserRole... allowedRoles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (allowedRoles.length > 0 && !hasAnyRole(authentication, allowedRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required role is missing");
        }

        try {
            Long userId = Long.valueOf(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid"));
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
            }
            return user;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }

    private boolean hasAnyRole(Authentication authentication, UserRole... roles) {
        Set<String> authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        for (UserRole role : roles) {
            if (authorities.contains("ROLE_" + role.name())) {
                return true;
            }
        }
        return false;
    }

    private List<YearMonth> lastSixMonths() {
        YearMonth current = YearMonth.now(REPORT_ZONE);
        return java.util.stream.IntStream.rangeClosed(0, 5)
                .mapToObj(offset -> current.minusMonths(5L - offset))
                .toList();
    }

    private YearMonth monthOf(Instant instant) {
        return instant == null ? null : YearMonth.from(instant.atZone(REPORT_ZONE));
    }

    private YearMonth monthOf(LocalDate date) {
        return date == null ? null : YearMonth.from(date);
    }

    private String cityOf(Halls hall) {
        return label(hall != null ? hall.getCity() : null, "Unknown");
    }

    private String label(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String stripPrefix(String value, String prefix) {
        String normalized = value == null ? "" : value.trim();
        return normalized.toUpperCase(Locale.ROOT).startsWith(prefix)
                ? normalized.substring(prefix.length())
                : normalized;
    }

    private Long tryParseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static class CityTotals {
        private long enquiries;
        private long bookings;
    }
}
