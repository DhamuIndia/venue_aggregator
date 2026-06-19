package com.staminal.venue.enquiries;

import java.time.Instant;
import java.time.LocalDate;

import com.staminal.venue.enums.EnquiryStatus;
// import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.vendors.Entity.Vendors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "enquiries")
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // @ManyToOne
    // @JoinColumn(name = "hall_id")
    // private Halls hall;

    @ManyToOne
    @JoinColumn(name = "vendor_id")
    private Vendors vendor;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "event_date")
    private LocalDate eventDate;

    private String message;

    @Enumerated(EnumType.STRING)
    private EnquiryStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}
