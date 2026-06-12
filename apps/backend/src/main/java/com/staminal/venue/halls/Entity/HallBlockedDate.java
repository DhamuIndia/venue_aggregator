package com.staminal.venue.halls.Entity;

import java.time.Instant;
import java.time.LocalDate;
import com.staminal.venue.enums.SlotType;
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
@Table(name = "hall_blocked_dates")
public class HallBlockedDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name="hall_id")
    private Halls hallId;

    @Column(name = "event_date")
    private LocalDate evetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type")
    private SlotType slotType;

    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

}
