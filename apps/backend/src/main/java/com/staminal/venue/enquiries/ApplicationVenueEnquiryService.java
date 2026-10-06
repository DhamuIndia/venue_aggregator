package com.staminal.venue.enquiries;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.enquiries.dto.ApplicationVenueEnquiryPage;
import com.staminal.venue.enquiries.dto.EnquiryResponse;
import com.staminal.venue.enquiries.dto.UpdateApplicationVenueEnquiryRequest;
import com.staminal.venue.enums.EnquiryStatus;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ApplicationVenueEnquiryService {
    private static final Set<EnquiryStatus> STATUSES = Set.of(EnquiryStatus.NEW, EnquiryStatus.CONTACTED, EnquiryStatus.CLOSED);
    private final VenueDiscoveryAccess access;
    private final EnquiryRepository enquiries;
    private final EnquiryService responseMapper;
    private final AuditService audit;
    private final ApplicationVenueEnquiryNotifications notifications;

    @Transactional(readOnly = true)
    public ApplicationVenueEnquiryPage list(int page, int size, EnquiryStatus status, Authentication authentication) {
        access.requireAdmin(authentication);
        if (page < 0 || size < 1 || size > 100 || (status != null && !STATUSES.contains(status)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid VenueMart enquiry queue filter");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Enquiry> result = status == null
                ? enquiries.findByRoutingTarget(EnquiryRoutingTarget.VENUEMART, pageable)
                : enquiries.findByRoutingTargetAndStatus(EnquiryRoutingTarget.VENUEMART, status, pageable);
        return new ApplicationVenueEnquiryPage(result.map(responseMapper::toResponse).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public EnquiryResponse update(String enquiryId, UpdateApplicationVenueEnquiryRequest request, Authentication authentication) {
        VenueDiscoveryAccess.Actor actor = access.requireAdmin(authentication);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VenueMart enquiry update is required");
        UpdateApplicationVenueEnquiryRequest validated;
        try { validated = request.validated(); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid VenueMart enquiry update"); }
        Enquiry enquiry = enquiries.findForTeamUpdate(EnquiryIds.parse(enquiryId), EnquiryRoutingTarget.VENUEMART)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VenueMart enquiry not found"));
        long oldVersion = enquiry.getVersion() == null ? 0 : enquiry.getVersion();
        if (oldVersion != validated.expectedVersion())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enquiry changed; reload before updating");
        EnquiryStatus oldStatus = enquiry.getStatus();
        if (!((oldStatus == EnquiryStatus.NEW && validated.status() == EnquiryStatus.CONTACTED)
                || (oldStatus == EnquiryStatus.CONTACTED && validated.status() == EnquiryStatus.CLOSED)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VenueMart enquiries must progress from new to contacted to closed");
        enquiry.setStatus(validated.status());
        enquiry.setTeamResponseMessage(validated.responseMessage());
        enquiry.setLastTeamUpdateReason(validated.reason());
        enquiry.setTeamUpdatedBy(actor.admin().getId());
        enquiry.setRespondedAt(Instant.now());
        Enquiry saved = enquiries.saveAndFlush(enquiry);
        audit.record(new AuditCommand(actor.userId(), actor.role(), AuditAction.APPLICATION_VENUE_ENQUIRY_UPDATED,
                "APPLICATION_VENUE_ENQUIRY", String.valueOf(saved.getId()), "VenueMart team updated an availability request",
                Map.of("status", oldStatus.name(), "version", oldVersion),
                Map.of("status", saved.getStatus().name(), "version", saved.getVersion()),
                Map.of("reasonProvided", true, "reasonLength", validated.reason().length(),
                        "responseLength", validated.responseMessage().length())));
        notifications.updated(saved);
        return responseMapper.toResponse(saved);
    }
}
