package com.staminal.venue.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminHallModerationServiceTest {

    @Mock
    private HallRepository hallRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    private AdminHallModerationService adminHallModerationService;

    @BeforeEach
    void setUp() {
        adminHallModerationService = new AdminHallModerationService(
                hallRepository,
                userRepository,
                adminRepository);
    }

    @Test
    void listPendingHallApplicationsReturnsFrontendShape() {
        Halls hall = hall(11L, HallStatus.PENDING_APPROVAL);

        when(hallRepository.findByStatus(HallStatus.PENDING_APPROVAL)).thenReturn(List.of(hall));

        AdminHallListResponse response = adminHallModerationService.getHalls("PENDING_APPROVAL", 0, 50);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        AdminHallResponse item = response.content().get(0);
        assertThat(item.id()).isEqualTo("11");
        assertThat(item.name()).isEqualTo("Emerald Convention Centre");
        assertThat(item.ownerName()).isEqualTo("Arun Kumar");
        assertThat(item.ownerPhone()).isEqualTo("9876501234");
        assertThat(item.location()).isEqualTo("T Nagar, Chennai");
        assertThat(item.status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void approvePendingHallWorksWithUnifiedNumericAdminSession() {
        Halls hall = hall(11L, HallStatus.PENDING_APPROVAL);
        User adminUser = adminUser();
        Admin legacyAdmin = legacyAdmin();

        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));
        when(userRepository.findById(900L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(legacyAdmin));
        when(hallRepository.save(any(Halls.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminHallResponse response = adminHallModerationService.reviewHall(
                "11",
                new AdminReviewRequest("APPROVED", "Documents verified"),
                auth());

        ArgumentCaptor<Halls> hallCaptor = ArgumentCaptor.forClass(Halls.class);
        verify(hallRepository).save(hallCaptor.capture());

        Halls saved = hallCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(HallStatus.APPROVED);
        assertThat(saved.getRejectionReason()).isNull();
        assertThat(saved.getApprovedBy()).isSameAs(legacyAdmin);
        assertThat(saved.getApprovedAt()).isNotNull();
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewedBy()).isEqualTo("Test Admin");
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void rejectRequiresReason() {
        Halls hall = hall(11L, HallStatus.PENDING_APPROVAL);

        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> adminHallModerationService.reviewHall(
                "11",
                new AdminReviewRequest("REJECTED", " "),
                auth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void draftHallCannotBeReviewed() {
        Halls hall = hall(11L, HallStatus.DRAFT);

        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> adminHallModerationService.reviewHall(
                "11",
                new AdminReviewRequest("APPROVED", "Documents verified"),
                auth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private Halls hall(Long id, HallStatus status) {
        User owner = new User();
        owner.setId(301L);
        owner.setFullName("Arun Kumar");
        owner.setPhone("9876501234");
        owner.setEmail("owner@example.com");
        owner.setStatus("ACTIVE");
        owner.setPasswordHash("hashed-password");

        Halls hall = new Halls();
        hall.setId(id);
        hall.setOwnerUserId(owner);
        hall.setOwnerName("Arun Kumar");
        hall.setName("Emerald Convention Centre");
        hall.setArea("T Nagar");
        hall.setCity("Chennai");
        hall.setHallType("Banquet hall");
        hall.setCapacityMax(600);
        hall.setFullDayAmount(new BigDecimal("150000"));
        hall.setCoverImageUrl("https://cdn.example.com/halls/emerald.jpg");
        hall.setStatus(status);
        hall.setCreatedAt(LocalDateTime.parse("2026-06-20T10:00:00"));
        hall.setUpdatedAt(LocalDateTime.parse("2026-06-21T10:00:00"));
        return hall;
    }

    private User adminUser() {
        User admin = new User();
        admin.setId(900L);
        admin.setFullName("Test Admin");
        admin.setPhone("9000000001");
        admin.setEmail("admin@example.com");
        admin.setStatus("ACTIVE");
        admin.setPasswordHash("hashed-password");
        return admin;
    }

    private Admin legacyAdmin() {
        Admin admin = new Admin();
        admin.setId(100L);
        admin.setFullName("Legacy Admin");
        admin.setEmail("admin@example.com");
        admin.setStatus("ACTIVE");
        admin.setPasswordHash("hashed-password");
        return admin;
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(
                "900",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
