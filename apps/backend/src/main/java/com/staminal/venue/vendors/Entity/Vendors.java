package com.staminal.venue.vendors.Entity;

import java.time.Instant;
import com.staminal.venue.enums.VendorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Set;

@Entity
@Table(name = "vendors")
public class Vendors {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private long userId;

    @Column(name = "vendor_name")
    private String vendorName;

    // @Column(name = "category_id")
    // private long categoryId;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    private String description;

    @Column(name = "address_line")
    private String addressLine;

    private String city;

    private String area;

    private String pincode;

    private double latitude;

    private double longitude;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "whatsApp_number")
    private String whatsAppNumber;

    @Enumerated(EnumType.STRING)
    private VendorStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(name = "vendor_category_mapping", joinColumns = @JoinColumn(name = "vendor_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<VendorCategory> categories;

}
