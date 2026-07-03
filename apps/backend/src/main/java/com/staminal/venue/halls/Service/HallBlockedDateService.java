package com.staminal.venue.halls.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.SlotType;
import com.staminal.venue.halls.Dto.BlockedDateResponse;
import com.staminal.venue.halls.Dto.CreateBlockedDateRequest;
import com.staminal.venue.halls.Entity.HallBlockedDate;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallBlockedDateRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@Service
public class HallBlockedDateService {

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private HallBlockedDateRepository hallBlockedDateRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    public BlockedDateResponse create(Long hallId, CreateBlockedDateRequest request, Authentication authentication) {
        Halls hall = findOwnedHall(hallId, authentication);

        Long ownerId = currentUserId(authentication);

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));

        SlotType newSlot = SlotType.valueOf(request.getSlot());

        List<Booking> confirmedBookings = bookingRepository
                .findByHall_IdAndStatus(
                        hallId,
                        BookingStatus.CONFIRMED)
                .stream()
                .filter(booking -> booking.getEventDate().equals(request.getDate()))
                .toList();

        for (Booking booking : confirmedBookings) {

            SlotType bookedSlot = booking.getSlotType();

            if (bookedSlot == newSlot) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This slot is already booked");
            }

            if (bookedSlot == SlotType.FULL_DAY) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Hall is already booked for the full day");
            }

            if (newSlot == SlotType.FULL_DAY) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A booking already exists on this date");
            }
        }

        List<HallBlockedDate> existingBlocks = hallBlockedDateRepository
                .findByHallId_Id(hallId)
                .stream()
                .filter(block -> block.getEventDate().equals(request.getDate()))
                .toList();

        for (HallBlockedDate block : existingBlocks) {

            SlotType existingSlot = block.getSlotType();

            if (existingSlot == newSlot) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This slot is already blocked");
            }
            if (existingSlot == SlotType.FULL_DAY) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Full day is already blocked");
            }
            if (newSlot == SlotType.FULL_DAY) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Morning/Evening slot already blocked");
            }
        }

        HallBlockedDate blockedDate = new HallBlockedDate();

        blockedDate.setHallId(hall);
        blockedDate.setEventDate(request.getDate());
        blockedDate.setSlotType(SlotType.valueOf(request.getSlot()));
        blockedDate.setReason(request.getReason());
        blockedDate.setCreatedAt(LocalDateTime.now());
        HallBlockedDate savedBlockedDate = hallBlockedDateRepository.save(blockedDate);

        auditService.record(
                new AuditCommand(
                        owner.getId(),
                        "HALL_OWNER",
                        AuditAction.AVAILABILITY_BLOCKED,
                        "HALL_BLOCKED_DATE",
                        String.valueOf(savedBlockedDate.getId()),
                        "Owner blocked availability",
                        null,
                        Map.of(
                                "hallId", hall.getId(),
                                "date", savedBlockedDate.getEventDate().toString(),
                                "slot", savedBlockedDate.getSlotType().name(),
                                "reason", savedBlockedDate.getReason()),
                        null));

        return map(savedBlockedDate);
    }

    public List<BlockedDateResponse> getByHall(Long hallId, Authentication authentication) {
        findOwnedHall(hallId, authentication);
        return hallBlockedDateRepository.findByHallId_Id(hallId)
                .stream()
                .map(this::map)
                .toList();
    }

    private BlockedDateResponse map(
            HallBlockedDate blockedDate) {

        BlockedDateResponse response = new BlockedDateResponse();

        response.setId(blockedDate.getId());
        response.setHallId(blockedDate.getHallId().getId());
        response.setDate(blockedDate.getEventDate());
        response.setSlot(blockedDate.getSlotType().name());
        response.setReason(blockedDate.getReason());

        return response;
    }

    public void delete(Long hallId, Long blockId, Authentication authentication) {
        findOwnedHall(hallId, authentication);

        Long ownerId = currentUserId(authentication);

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));

        HallBlockedDate blockedDate = hallBlockedDateRepository.findById(blockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocked date not found"));

        if (blockedDate.getHallId().getId() != hallId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Blocked date does not belong to this hall");
        }
        auditService.record(
                new AuditCommand(
                        owner.getId(),
                        "HALL_OWNER",
                        AuditAction.AVAILABILITY_DELETED,
                        "HALL_BLOCKED_DATE",
                        String.valueOf(blockedDate.getId()),
                        "Owner deleted blocked availability",
                        Map.of(
                                "hallId", blockedDate.getHallId().getId(),
                                "date", blockedDate.getEventDate().toString(),
                                "slot", blockedDate.getSlotType().name(),
                                "reason", blockedDate.getReason()),
                        null,
                        null));

        hallBlockedDateRepository.delete(blockedDate);
    }

    private Halls findOwnedHall(Long hallId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        if (hall.getOwnerUserId() == null || !hall.getOwnerUserId().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall does not belong to this owner");
        }
        return hall;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }
}
