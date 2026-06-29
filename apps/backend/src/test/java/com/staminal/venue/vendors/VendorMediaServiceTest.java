package com.staminal.venue.vendors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorServiceType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Dto.CreateVendorMediaRequest;
import com.staminal.venue.vendors.Dto.VendorMediaResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.VendorMedia;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;
import com.staminal.venue.vendors.Service.VendorMediaService;

@ExtendWith(MockitoExtension.class)
class VendorMediaServiceTest {

    @Mock
    private VendorMediaRepository vendorMediaRepository;

    @Mock
    private VendorRepository vendorRepository;

    private VendorMediaService vendorMediaService;

    @BeforeEach
    void setUp() {
        vendorMediaService = new VendorMediaService(vendorMediaRepository, vendorRepository);
    }

    @Test
    void getMyMediaReturnsSortedPortfolioForAuthenticatedVendor() {
        Vendors vendor = vendor(501L, 301L);
        VendorMedia second = media(802L, vendor, "https://cdn.example.com/vendors/saffron/two.jpg", false, 2);
        VendorMedia cover = media(801L, vendor, "https://cdn.example.com/vendors/saffron/cover.jpg", true, 0);

        when(vendorMediaRepository.findByVendor_User_Id(301L)).thenReturn(List.of(second, cover));

        List<VendorMediaResponse> response = vendorMediaService.getMyMedia(authentication());

        assertThat(response).extracting(VendorMediaResponse::getUrl)
                .containsExactly("https://cdn.example.com/vendors/saffron/cover.jpg",
                        "https://cdn.example.com/vendors/saffron/two.jpg");
        assertThat(response.get(0).getIsCover()).isTrue();
    }

    @Test
    void createMyMediaUsesAuthenticatedVendorAndNormalizesCover() {
        Vendors vendor = vendor(501L, 301L);
        VendorMedia oldCover = media(701L, vendor, "https://cdn.example.com/vendors/saffron/old.jpg", true, 0);
        CreateVendorMediaRequest request = createRequest();

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorMediaRepository.findByVendor_Id(501L)).thenReturn(List.of(oldCover));
        when(vendorMediaRepository.save(any(VendorMedia.class))).thenAnswer(invocation -> {
            VendorMedia saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(801L);
            }
            return saved;
        });
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorMediaResponse response = vendorMediaService.createMyMedia(request, authentication());

        ArgumentCaptor<VendorMedia> mediaCaptor = ArgumentCaptor.forClass(VendorMedia.class);
        verify(vendorMediaRepository, org.mockito.Mockito.atLeastOnce()).save(mediaCaptor.capture());

        VendorMedia savedMedia = mediaCaptor.getAllValues().stream()
                .filter(item -> "https://cdn.example.com/vendors/saffron/portfolio-1.jpg".equals(item.getMediaUrl()))
                .findFirst()
                .orElseThrow();

        assertThat(oldCover.getIsPrimary()).isFalse();
        assertThat(savedMedia.getVendor()).isSameAs(vendor);
        assertThat(savedMedia.getStorageKey()).isEqualTo("vendors/saffron/portfolio-1.jpg");
        assertThat(savedMedia.getCaption()).isEqualTo("Reception buffet setup");
        assertThat(savedMedia.getSortOrder()).isEqualTo(1);
        assertThat(savedMedia.getServiceType()).isEqualTo(VendorServiceType.CATERING);
        assertThat(vendor.getCoverImageUrl()).isEqualTo("https://cdn.example.com/vendors/saffron/portfolio-1.jpg");
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/vendors/saffron/portfolio-1.jpg");
    }

    @Test
    void updateMyMediaRejectsMediaOwnedByAnotherVendor() {
        Vendors otherVendor = vendor(777L, 999L);
        VendorMedia media = media(801L, otherVendor, "https://cdn.example.com/vendors/other.jpg", false, 0);
        CreateVendorMediaRequest request = new CreateVendorMediaRequest();
        request.setIsCover(true);

        when(vendorMediaRepository.findByIdAndVendor_User_Id(801L, 301L)).thenReturn(Optional.empty());
        when(vendorMediaRepository.findById(801L)).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> vendorMediaService.updateMyMedia("801", request, authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deleteMyMediaPromotesNextCoverWhenCoverIsRemoved() {
        Vendors vendor = vendor(501L, 301L);
        VendorMedia cover = media(801L, vendor, "https://cdn.example.com/vendors/saffron/cover.jpg", true, 0);
        VendorMedia next = media(802L, vendor, "https://cdn.example.com/vendors/saffron/next.jpg", false, 1);

        when(vendorMediaRepository.findByIdAndVendor_User_Id(801L, 301L)).thenReturn(Optional.of(cover));
        when(vendorMediaRepository.findByVendor_Id(501L)).thenReturn(List.of(next));
        when(vendorMediaRepository.save(any(VendorMedia.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> invocation.getArgument(0));

        vendorMediaService.deleteMyMedia("801", authentication());

        verify(vendorMediaRepository).delete(cover);
        assertThat(next.getIsPrimary()).isTrue();
        assertThat(vendor.getCoverImageUrl()).isEqualTo("https://cdn.example.com/vendors/saffron/next.jpg");
    }

    private CreateVendorMediaRequest createRequest() {
        CreateVendorMediaRequest request = new CreateVendorMediaRequest();
        request.setUrl("https://cdn.example.com/vendors/saffron/portfolio-1.jpg");
        request.setMediaUrl("https://cdn.example.com/vendors/saffron/portfolio-1.jpg");
        request.setStorageKey("vendors/saffron/portfolio-1.jpg");
        request.setFileName("portfolio-1.jpg");
        request.setCaption("Reception buffet setup");
        request.setIsCover(true);
        request.setSortOrder(1);
        request.setMediaType("IMAGE");
        return request;
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }

    private Vendors vendor(Long vendorId, Long userId) {
        User user = new User();
        user.setId(userId);

        Vendors vendor = new Vendors();
        vendor.setId(vendorId);
        vendor.setUser(user);
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setCategories(Set.of(category("Catering")));
        vendor.setServices(List.of("Wedding catering"));
        return vendor;
    }

    private VendorMedia media(Long id, Vendors vendor, String url, boolean cover, int sortOrder) {
        VendorMedia media = new VendorMedia();
        media.setId(id);
        media.setVendor(vendor);
        media.setMediaUrl(url);
        media.setMediaType("IMAGE");
        media.setIsPrimary(cover);
        media.setSortOrder(sortOrder);
        media.setCreatedAt(Instant.parse("2026-06-01T10:00:00Z"));
        media.setServiceType(VendorServiceType.CATERING);
        media.setServiceId(vendor.getId());
        return media;
    }

    private VendorCategory category(String name) {
        VendorCategory category = new VendorCategory();
        category.setId(8L);
        category.setCategoryName(name);
        return category;
    }
}
