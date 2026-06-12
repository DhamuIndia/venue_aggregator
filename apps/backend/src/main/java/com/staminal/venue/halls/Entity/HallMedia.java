package com.staminal.venue.halls.Entity;

import java.time.Instant;
import com.staminal.venue.enums.MediaType;
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
@Table(name = "hall_media")
public class HallMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name="hall_id",nullable = false)
    private Halls halls;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type")
    private MediaType mediaType;

    private String url;

    @Column(name = "public_id")
    private String publicId;

    @Column(name = "is_primary")
    private boolean isPrimary;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at")
    private Instant createdAt;
}
