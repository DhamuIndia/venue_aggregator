package com.staminal.venue.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.enquiries.EnquiryRepository;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.SlotType;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class ReportsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private EnquiryRepository enquiryRepository;

    @Mock
    private BookingRepository bookingRepository;

    private ReportsService reportsService;

    @BeforeEach
    void setUp() {
        reportsService = new ReportsService(
                userRepository,
                hallRepository,
                vendorRepository,
                enquiryRepository,
                bookingRepository);
    }

    @Test
    void adminSummaryReturnsPlatformTotals() {
        User admin = user(900L, "Admin");
        Halls hall = hall();
        Vendors vendor = vendor();
        Enquiry enquiry = enquiry(hall, customer());
        Booking booking = booking(hall, customer(), BookingStatus.CONFIRMED, new BigDecimal("125000"));

        when(userRepository.findById(900L)).thenReturn(Optional.of(admin));
        when(userRepository.count()).thenReturn(4L);
        when(enquiryRepository.findAll()).thenReturn(List.of(enquiry));
        when(bookingRepository.findAll()).thenReturn(List.of(booking));
        when(hallRepository.findAll()).thenReturn(List.of(hall));
        when(vendorRepository.findAll()).thenReturn(List.of(vendor));

        AdminReportResponse response = reportsService.getAdminSummary(auth(900L, UserRole.ADMIN));

        assertThat(response.totalUsers()).isEqualTo(4);
        assertThat(response.activeListings()).isEqualTo(2);
        assertThat(response.monthlyEnquiries()).isEqualTo(1);
        assertThat(response.confirmedBookings()).isEqualTo(1);
        assertThat(response.bookingRevenue()).isEqualByComparingTo("125000");
        assertThat(response.conversionRate()).isEqualTo(100);
        assertThat(response.topCities()).extracting(TopCityResponse::city).contains("Chennai");
    }

    @Test
    void ownerSummaryRequiresOwnedHall() {
        User owner = user(301L, "Owner");
        Halls hall = hall();
        hall.setOwnerUserId(user(999L, "Other Owner"));

        when(userRepository.findById(301L)).thenReturn(Optional.of(owner));
        when(hallRepository.findById(201L)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> reportsService.getOwnerHallSummary("201", auth(301L, UserRole.HALL_OWNER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void ownerSummaryReturnsHallMetrics() {
        User owner = user(301L, "Owner");
        User customer = customer();
        Halls hall = hall();
        Enquiry enquiry = enquiry(hall, customer);
        Booking confirmed = booking(hall, customer, BookingStatus.CONFIRMED, new BigDecimal("125000"));
        Booking completed = booking(hall, customer, BookingStatus.COMPLETED, new BigDecimal("95000"));

        when(userRepository.findById(301L)).thenReturn(Optional.of(owner));
        when(hallRepository.findById(201L)).thenReturn(Optional.of(hall));
        when(enquiryRepository.findByHall_IdOrderByCreatedAtDesc(201L)).thenReturn(List.of(enquiry));
        when(bookingRepository.findByHall_IdOrderByEventDateDesc(201L)).thenReturn(List.of(confirmed, completed));

        OwnerReportResponse response = reportsService.getOwnerHallSummary("201", auth(301L, UserRole.HALL_OWNER));

        assertThat(response.enquiries()).isEqualTo(1);
        assertThat(response.confirmedBookings()).isEqualTo(2);
        assertThat(response.completedBookings()).isEqualTo(1);
        assertThat(response.estimatedRevenue()).isEqualByComparingTo("220000");
        assertThat(response.averageRating()).isEqualTo(4.8);
        assertThat(response.eventMix()).extracting(EventMixResponse::eventType).contains("Wedding");
    }

    @Test
    void vendorSummaryUsesCurrentVendorOnly() {
        User vendorUser = user(401L, "Vendor");
        Vendors vendor = vendor();
        Enquiry lead = enquiry(null, customer());
        lead.setVendor(vendor);
        lead.setStatus(EnquiryStatus.CONFIRMED);
        lead.setGuestCount(300);

        when(userRepository.findById(401L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(401L)).thenReturn(Optional.of(vendor));
        when(enquiryRepository.findAll()).thenReturn(List.of(lead));

        VendorReportResponse response = reportsService.getVendorSummary(
                "saffron-leaf-catering",
                auth(401L, UserRole.VENDOR));

        assertThat(response.leads()).isEqualTo(1);
        assertThat(response.contacted()).isEqualTo(1);
        assertThat(response.quotesSent()).isEqualTo(1);
        assertThat(response.booked()).isEqualTo(1);
        assertThat(response.bookedValue()).isEqualByComparingTo("195000");
        assertThat(response.conversionRate()).isEqualTo(100);
        assertThat(response.serviceMix()).extracting(ServiceMixResponse::service).contains("Wedding");
    }

    private Enquiry enquiry(Halls hall, User customer) {
        Enquiry enquiry = new Enquiry();
        enquiry.setId(55L);
        enquiry.setHall(hall);
        enquiry.setCustomer(customer);
        enquiry.setCustomerName(customer.getFullName());
        enquiry.setEventDate(LocalDate.now().plusDays(10));
        enquiry.setEventType("Wedding");
        enquiry.setGuestCount(450);
        enquiry.setSlotType(SlotType.EVENING);
        enquiry.setStatus(EnquiryStatus.PENDING_OWNER_RESPONSE);
        enquiry.setCreatedAt(YearMonth.now(ZoneId.of("Asia/Kolkata"))
                .atDay(1)
                .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                .toInstant());
        return enquiry;
    }

    private Booking booking(Halls hall, User customer, BookingStatus status, BigDecimal amount) {
        Booking booking = new Booking();
        booking.setId(88L);
        booking.setHall(hall);
        booking.setCustomer(customer);
        booking.setEventDate(LocalDate.now());
        booking.setSlotType(SlotType.EVENING);
        booking.setStatus(status);
        booking.setAmount(amount);
        booking.setCustomerName(customer.getFullName());
        booking.setCreatedAt(Instant.now());
        return booking;
    }

    private Halls hall() {
        Halls hall = new Halls();
        hall.setId(201L);
        hall.setName("Emerald Convention Centre");
        hall.setCity("Chennai");
        hall.setStatus(HallStatus.APPROVED);
        hall.setOwnerUserId(user(301L, "Owner"));
        hall.setRatings(4.8);
        return hall;
    }

    private Vendors vendor() {
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setUser(user(401L, "Vendor"));
        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setStartingPrice(new BigDecimal("650"));
        vendor.setServices(List.of("Wedding catering", "Traditional meals"));
        return vendor;
    }

    private User customer() {
        return user(101L, "Priya Raman");
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setPhone("900000000" + id);
        user.setEmail("user" + id + "@example.com");
        user.setStatus("ACTIVE");
        return user;
    }

    private UsernamePasswordAuthenticationToken auth(Long userId, UserRole role) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
