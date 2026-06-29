package com.staminal.venue.vendors.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.vendors.Dto.PublicVendorListResponse;
import com.staminal.venue.vendors.Dto.PublicVendorPackageResponse;
import com.staminal.venue.vendors.Dto.PublicVendorResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.VendorMedia;
import com.staminal.venue.vendors.Entity.VendorPackage;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorPackageRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PublicVendorService {

    private static final String DEFAULT_IMAGE_URL = "/images/venues/emerald-convention-centre.jpg";

    private final VendorRepository vendorRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final VendorMediaRepository vendorMediaRepository;

    @Transactional(readOnly = true)
    public PublicVendorListResponse searchPublicVendors(
            String q,
            String city,
            String area,
            String category,
            BigDecimal maxPrice,
            Boolean verified,
            String sort,
            int page,
            int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<Vendors> filtered = vendorRepository.findByStatus(VendorStatus.APPROVED)
                .stream()
                .filter(vendor -> matchesText(vendor, q))
                .filter(vendor -> matchesLocation(vendor, city, area))
                .filter(vendor -> matchesCategory(vendor, category))
                .filter(vendor -> maxPrice == null || startingPrice(vendor) == null
                        || startingPrice(vendor).compareTo(maxPrice) <= 0)
                .filter(vendor -> verified == null || verified)
                .sorted(publicSort(sort))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<PublicVendorResponse> content = filtered.subList(fromIndex, toIndex)
                .stream()
                .map(this::mapToResponse)
                .toList();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new PublicVendorListResponse(content, safePage, safeSize, filtered.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public PublicVendorResponse getPublicVendor(String vendorId) {
        Vendors vendor = findApprovedVendor(vendorId);
        return mapToResponse(vendor);
    }

    private PublicVendorResponse mapToResponse(Vendors vendor) {
        List<VendorMedia> media = vendorMediaRepository.findByVendor_Id(vendor.getId());
        String imageUrl = media.stream()
                .filter(VendorMedia::getIsPrimary)
                .map(VendorMedia::getMediaUrl)
                .filter(this::hasText)
                .findFirst()
                .orElseGet(() -> firstText(vendor.getCoverImageUrl(), DEFAULT_IMAGE_URL));
        List<String> galleryUrls = media.stream()
                .map(VendorMedia::getMediaUrl)
                .filter(this::hasText)
                .distinct()
                .toList();

        return new PublicVendorResponse(
                String.valueOf(vendor.getId()),
                firstText(vendor.getBusinessName(), vendor.getVendorName(), "Vendor"),
                firstText(vendor.getVendorName(), vendor.getBusinessName(), "Vendor owner"),
                frontendCategory(vendor),
                firstText(vendor.getCity(), ""),
                firstText(vendor.getArea(), ""),
                0.0,
                0,
                firstNonNull(vendor.getStartingPrice(), BigDecimal.ZERO),
                imageUrl,
                galleryUrls.isEmpty() ? List.of(imageUrl) : galleryUrls,
                true,
                "Within 24 hours",
                0,
                vendor.getServices() == null ? List.of() : vendor.getServices(),
                firstText(vendor.getDescription(), ""),
                packages(vendor),
                List.of(),
                "APPROVED");
    }

    private List<PublicVendorPackageResponse> packages(Vendors vendor) {
        List<PublicVendorPackageResponse> packageRows = vendorPackageRepository.findByVendor_Id(vendor.getId())
                .stream()
                .map(row -> new PublicVendorPackageResponse(
                        String.valueOf(row.getId()),
                        firstText(row.getPackageName(), "Starting package"),
                        firstText(row.getDescription(), ""),
                        firstNonNull(row.getPrice(), BigDecimal.ZERO),
                        List.of()))
                .toList();

        if (!packageRows.isEmpty()) {
            return packageRows;
        }

        if (!hasText(vendor.getPackageName()) && vendor.getStartingPrice() == null) {
            return List.of();
        }

        return List.of(new PublicVendorPackageResponse(
                "vendor-%d-starting".formatted(vendor.getId()),
                firstText(vendor.getPackageName(), "Starting package"),
                firstText(vendor.getPackageDescription(), ""),
                firstNonNull(vendor.getStartingPrice(), BigDecimal.ZERO),
                vendor.getServices() == null ? List.of() : vendor.getServices()));
    }

    private Vendors findApprovedVendor(String vendorId) {
        Long numericId = tryParseLong(vendorId);

        if (numericId != null) {
            Vendors vendor = vendorRepository.findById(numericId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
            if (vendor.getStatus() != VendorStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found");
            }
            return vendor;
        }

        return vendorRepository.findByStatus(VendorStatus.APPROVED)
                .stream()
                .filter(candidate -> slugify(candidate.getBusinessName()).equals(slugify(vendorId)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
    }

    private Comparator<Vendors> publicSort(String sort) {
        String normalized = sort == null ? "RELEVANCE" : sort.trim().toUpperCase(Locale.ROOT);
        if ("PRICE_ASC".equals(normalized)) {
            return Comparator
                    .comparing((Vendors vendor) -> startingPrice(vendor) == null)
                    .thenComparing(this::startingPrice, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return Comparator
                .comparing((Vendors vendor) -> vendor.getStartingPrice() == null)
                .thenComparing(Vendors::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean matchesText(Vendors vendor, String q) {
        if (!hasText(q)) {
            return true;
        }
        String needle = normalize(q);
        String haystack = normalize(String.join(" ",
                firstText(vendor.getBusinessName(), ""),
                firstText(vendor.getVendorName(), ""),
                firstText(vendor.getDescription(), ""),
                firstText(vendor.getCity(), ""),
                firstText(vendor.getArea(), ""),
                vendor.getServices() == null ? "" : String.join(" ", vendor.getServices()),
                vendor.getCategories() == null ? "" : vendor.getCategories().stream()
                        .map(VendorCategory::getCategoryName)
                        .toList()
                        .toString()));
        return haystack.contains(needle);
    }

    private boolean matchesLocation(Vendors vendor, String city, String area) {
        boolean hasCity = hasText(city);
        boolean hasArea = hasText(area);
        if (!hasCity && !hasArea) {
            return true;
        }

        if (hasCity && hasArea && normalize(city).equals(normalize(area))) {
            return matchesEquals(vendor.getCity(), city) || matchesEquals(vendor.getArea(), area);
        }

        return (!hasCity || matchesEquals(vendor.getCity(), city))
                && (!hasArea || matchesEquals(vendor.getArea(), area));
    }

    private boolean matchesCategory(Vendors vendor, String category) {
        if (!hasText(category)) {
            return true;
        }
        String frontendCategory = normalize(category);
        return normalize(frontendCategory(vendor)).equals(frontendCategory);
    }

    private String frontendCategory(Vendors vendor) {
        Set<VendorCategory> categories = vendor.getCategories() == null ? Set.of() : vendor.getCategories();
        return categories.stream()
                .map(VendorCategory::getCategoryName)
                .map(this::toFrontendCategory)
                .findFirst()
                .orElse("CATERING");
    }

    private String toFrontendCategory(String categoryName) {
        String normalized = normalize(categoryName).replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "CATERING", "FOOD" -> "CATERING";
            case "DECORATION", "BALLOON_DECORATION" -> "DECORATION";
            case "PHOTOGRAPHY" -> "PHOTOGRAPHY";
            case "MAKEUP", "MEHENDI" -> "BRIDAL_MAKEUP";
            case "DJ", "LIVE_MUSIC" -> "MUSIC_AND_DJ";
            case "WEDDING_PLANNER" -> "EVENT_PLANNING";
            default -> "CATERING";
        };
    }

    private BigDecimal startingPrice(Vendors vendor) {
        return vendor.getStartingPrice();
    }

    private boolean matchesEquals(String value, String expected) {
        return !hasText(expected) || normalize(value).contains(normalize(expected));
    }

    private Long tryParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(firstText(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalize(String value) {
        return firstText(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
