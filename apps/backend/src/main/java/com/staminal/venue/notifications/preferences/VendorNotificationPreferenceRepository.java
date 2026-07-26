package com.staminal.venue.notifications.preferences;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorNotificationPreferenceRepository
        extends JpaRepository<VendorNotificationPreference, Long> {

    Optional<VendorNotificationPreference> findByVendor_Id(Long vendorId);
}
