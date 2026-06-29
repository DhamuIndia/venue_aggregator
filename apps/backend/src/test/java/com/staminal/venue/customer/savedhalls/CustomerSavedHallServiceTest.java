package com.staminal.venue.customer.savedhalls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

import com.staminal.venue.customer.savedhalls.dto.CustomerSavedHallsResponse;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallMediaRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomerSavedHallServiceTest {

    @Mock
    private CustomerSavedHallRepository savedHallRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private HallMediaRepository hallMediaRepository;

    @Mock
    private UserRepository userRepository;

    private CustomerSavedHallService savedHallService;

    @BeforeEach
    void setUp() {
        HallsService hallsService = new HallsService(hallRepository, userRepository, hallMediaRepository);
        savedHallService = new CustomerSavedHallService(savedHallRepository, hallRepository, hallsService, userRepository);
    }

    @Test
    void customerCanListSavedApprovedHalls() {
        User customer = customer();
        Halls hall = approvedHall();
        CustomerSavedHall savedHall = savedHall(customer, hall);

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(savedHallRepository.findByCustomer_IdOrderByCreatedAtDesc(101L)).thenReturn(List.of(savedHall));
        when(hallMediaRepository.findByHallId_Id(11L)).thenReturn(List.of());

        CustomerSavedHallsResponse response = savedHallService.getSavedHalls(auth(101L, UserRole.CUSTOMER));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).getName()).isEqualTo("Emerald Convention Centre");
        assertThat(response.content()).isEqualTo(response.items());
    }

    @Test
    void saveHallIsIdempotentWhenAlreadySaved() {
        User customer = customer();
        Halls hall = approvedHall();
        CustomerSavedHall savedHall = savedHall(customer, hall);

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));
        when(savedHallRepository.findByCustomer_IdAndHall_Id(101L, 11L)).thenReturn(Optional.of(savedHall));
        when(hallMediaRepository.findByHallId_Id(11L)).thenReturn(List.of());

        HallResponse response = savedHallService.saveHall("11", auth(101L, UserRole.CUSTOMER));

        assertThat(response.getId()).isEqualTo(11L);
        verify(savedHallRepository, never()).save(org.mockito.ArgumentMatchers.any(CustomerSavedHall.class));
    }

    @Test
    void cannotSaveDraftHall() {
        User customer = customer();
        Halls hall = approvedHall();
        hall.setStatus(HallStatus.DRAFT);

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> savedHallService.saveHall("11", auth(101L, UserRole.CUSTOMER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void nonCustomerCannotSaveHall() {
        assertThatThrownBy(() -> savedHallService.saveHall("11", auth(301L, UserRole.HALL_OWNER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void deleteIsIdempotent() {
        User customer = customer();
        Halls hall = approvedHall();

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(hallRepository.findById(11L)).thenReturn(Optional.of(hall));
        when(savedHallRepository.findByCustomer_IdAndHall_Id(101L, 11L)).thenReturn(Optional.empty());

        savedHallService.removeHall("11", auth(101L, UserRole.CUSTOMER));

        verify(savedHallRepository, never()).delete(org.mockito.ArgumentMatchers.any(CustomerSavedHall.class));
    }

    private CustomerSavedHall savedHall(User customer, Halls hall) {
        CustomerSavedHall savedHall = new CustomerSavedHall();
        savedHall.setId(501L);
        savedHall.setCustomer(customer);
        savedHall.setHall(hall);
        return savedHall;
    }

    private Halls approvedHall() {
        Halls hall = new Halls();
        hall.setId(11L);
        hall.setName("Emerald Convention Centre");
        hall.setDescription("Spacious wedding venue with dining and parking.");
        hall.setAddressLine("ECR, Chennai");
        hall.setCity("Chennai");
        hall.setArea("ECR");
        hall.setPincode("600119");
        hall.setCapacityMax(900);
        hall.setHallType("MARRIAGE_HALL");
        hall.setRatings(4.8);
        hall.setAcAvailable(true);
        hall.setCarParking(true);
        hall.setDiningAvailable(true);
        hall.setFullDayAmount(new BigDecimal("125000"));
        hall.setCoverImageUrl("https://cdn.example.com/emerald.jpg");
        hall.setStatus(HallStatus.APPROVED);
        hall.setCreatedAt(LocalDateTime.now());
        hall.setUpdatedAt(LocalDateTime.now());
        return hall;
    }

    private User customer() {
        User user = new User();
        user.setId(101L);
        user.setFullName("Priya Raman");
        user.setPhone("9876543210");
        user.setEmail("priya@example.com");
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
