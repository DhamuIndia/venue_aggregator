package com.staminal.venue.halls;

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
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.halls.Dto.HallListResponse;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Dto.Pricing;
import com.staminal.venue.halls.Dto.UpdateHallRequest;
import com.staminal.venue.halls.Entity.HallMedia;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallMediaRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class HallsServiceTest {

    @Mock
    private HallRepository hallRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HallMediaRepository hallMediaRepository;

    private HallsService hallsService;

    @BeforeEach
    void setUp() {
        hallsService = new HallsService(hallRepository, userRepository, hallMediaRepository);
    }

    @Test
    void publicSearchReturnsApprovedHallsWithGallery() {
        Halls approvedHall = hall(11L, 301L, HallStatus.APPROVED);
        Halls draftHall = hall(12L, 301L, HallStatus.DRAFT);
        HallMedia media = media(approvedHall, "https://cdn.example.com/emerald-cover.jpg");

        when(hallRepository.findByStatus(HallStatus.APPROVED)).thenReturn(List.of(approvedHall));
        when(hallMediaRepository.findByHallId_Id(11L)).thenReturn(List.of(media));

        HallListResponse response = hallsService.searchPublicHalls(
                "emerald",
                "Chennai",
                null,
                "MARRIAGE_HALL",
                500,
                new BigDecimal("150000"),
                "PRICE_ASC",
                0,
                20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).getId()).isEqualTo(11L);
        assertThat(response.content().get(0).getVerified()).isTrue();
        assertThat(response.content().get(0).getGalleryUrls()).containsExactly("https://cdn.example.com/emerald-cover.jpg");
        assertThat(draftHall.getStatus()).isEqualTo(HallStatus.DRAFT);
    }

    @Test
    void ownerCannotReadAnotherOwnersHall() {
        Halls hall = hall(11L, 301L, HallStatus.DRAFT);

        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> hallsService.getHall("11", auth(999L, UserRole.HALL_OWNER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void updateHallUsesRequestedHallId() {
        Halls hall = hall(11L, 301L, HallStatus.PENDING_APPROVAL);
        UpdateHallRequest request = updateRequest();

        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));
        when(hallRepository.save(any(Halls.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HallResponse response = hallsService.updateHall("11", request, auth(301L, UserRole.HALL_OWNER));

        ArgumentCaptor<Halls> hallCaptor = ArgumentCaptor.forClass(Halls.class);
        verify(hallRepository).save(hallCaptor.capture());

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(hallCaptor.getValue().getName()).isEqualTo("Emerald Convention Centre Updated");
        assertThat(hallCaptor.getValue().getStatus()).isEqualTo(HallStatus.DRAFT);
    }

    private Halls hall(Long id, Long ownerId, HallStatus status) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setFullName("Arun Kumar");
        owner.setStatus("ACTIVE");

        Halls hall = new Halls();
        hall.setId(id);
        hall.setOwnerUserId(owner);
        hall.setOwnerName(owner.getFullName());
        hall.setName("Emerald Convention Centre");
        hall.setDescription("Spacious wedding venue with dining and parking.");
        hall.setAddressLine("ECR, Chennai");
        hall.setCity("Chennai");
        hall.setArea("ECR");
        hall.setPincode("600119");
        hall.setCapacityMin(400);
        hall.setCapacityMax(900);
        hall.setHallType("MARRIAGE_HALL");
        hall.setRatings(4.8);
        hall.setAcAvailable(true);
        hall.setCarParking(true);
        hall.setDiningAvailable(true);
        hall.setFullDayAmount(new BigDecimal("125000"));
        hall.setCoverImageUrl("https://cdn.example.com/emerald.jpg");
        hall.setStatus(status);
        hall.setCreatedAt(LocalDateTime.now());
        hall.setUpdatedAt(LocalDateTime.now());
        return hall;
    }

    private HallMedia media(Halls hall, String url) {
        HallMedia media = new HallMedia();
        media.setId(21L);
        media.setHallId(hall);
        media.setMediaType("IMAGE");
        media.setUrl(url);
        media.setIsPrimary(true);
        media.setSortOrder(0);
        return media;
    }

    private UpdateHallRequest updateRequest() {
        UpdateHallRequest request = new UpdateHallRequest();
        request.setName("Emerald Convention Centre Updated");
        request.setDescription("Updated description.");
        request.setAddressLine("ECR, Chennai");
        request.setCity("Chennai");
        request.setArea("ECR");
        request.setPincode("600119");
        request.setCapacity(500);
        request.setCapacityMax(950);
        request.setVenueType("MARRIAGE_HALL");
        request.setAcAvailable(true);
        request.setCarParking(true);
        request.setDiningAvailable(true);
        request.setCoverImageUrl("https://cdn.example.com/emerald-updated.jpg");
        Pricing pricing = new Pricing();
        pricing.setFullDayPrice(new BigDecimal("130000"));
        request.setPricing(pricing);
        return request;
    }

    private UsernamePasswordAuthenticationToken auth(Long userId, UserRole role) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
