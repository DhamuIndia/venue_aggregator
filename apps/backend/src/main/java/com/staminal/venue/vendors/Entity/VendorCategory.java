package com.staminal.venue.vendors.Entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Set;

@Entity
@Table(name = "vendor_categories")
public class VendorCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String categoryName;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToMany(mappedBy = "categories")
    private Set<Vendors> vendors;
}
