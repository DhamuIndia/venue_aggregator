package com.staminal.venue.discovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Candidate;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Page;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Run;
import com.staminal.venue.discovery.VenueDiscoveryResponse.RunDetail;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class VenueDiscoveryStore {
    private static final Set<VenueDiscoveryCandidateStatus> REVIEWABLE = Set.of(
            VenueDiscoveryCandidateStatus.DISCOVERED, VenueDiscoveryCandidateStatus.SHORTLISTED,
            VenueDiscoveryCandidateStatus.REJECTED, VenueDiscoveryCandidateStatus.CLOSED);
    private final VenueDiscoveryRunRepository runs;
    private final VenueDiscoveryCandidateRepository candidates;
    private final VenueDiscoveryRunCandidateRepository observations;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public Long start(VenueDiscoveryRequest.Search request, Actor actor) {
        VenueDiscoveryRun run = new VenueDiscoveryRun();
        run.setSearchMode(VenueDiscoverySearchMode.TEXT);
        run.setCity(request.city().trim());
        run.setArea(request.area() == null || request.area().isBlank() ? null : request.area().trim());
        run.setRequestedPlaceTypes(request.venueType().name());
        run.setCreatedByAdmin(actor.admin());
        run.setStatus(VenueDiscoveryRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        runs.saveAndFlush(run);
        record(actor, AuditAction.VENUE_DISCOVERY_RUN_CREATED, "VENUE_DISCOVERY_RUN", run.getId(),
                Map.of(), Map.of("status", "RUNNING"));
        return run.getId();
    }

    public RunDetail complete(Long id, List<String> placeIds, Actor actor) {
        VenueDiscoveryRun run = requireRun(id);
        if (run.getStatus() != VenueDiscoveryRunStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Search is no longer running");
        }
        List<Candidate> result = new ArrayList<>();
        // Sort the insert/lock order so concurrent overlapping searches cannot deadlock each other.
        for (String placeId : placeIds.stream().distinct().sorted().toList()) {
            int inserted = jdbc.update("""
                    insert into venue_discovery_candidates (source, source_place_id)
                    values ('GOOGLE_PLACES', ?) on conflict (source, source_place_id) do nothing
                    """, placeId);
            // Atomic update avoids overwriting a status that another administrator just reviewed.
            // Acquire the row lock before loading the entity so the response sees the latest review.
            jdbc.update("""
                    update venue_discovery_candidates set source_checked_at = now()
                    where source = 'GOOGLE_PLACES' and source_place_id = ?
                    """, placeId);
            VenueDiscoveryCandidate candidate = candidates.findBySourceAndSourcePlaceId(
                    VenueDiscoverySource.GOOGLE_PLACES, placeId).orElseThrow();
            VenueDiscoveryRunCandidate observation = new VenueDiscoveryRunCandidate();
            observation.setDiscoveryRun(run);
            observation.setCandidate(candidate);
            observations.save(observation);
            if (inserted == 1) {
                record(actor, AuditAction.VENUE_DISCOVERY_CANDIDATE_CREATED,
                        "VENUE_DISCOVERY_CANDIDATE", candidate.getId(), Map.of(), Map.of("status", "DISCOVERED"));
            }
            result.add(candidateResponse(candidate));
        }
        run.setStatus(VenueDiscoveryRunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        runs.save(run);
        return new RunDetail(runResponse(run, result.size()), result);
    }

    public void fail(Long id) {
        VenueDiscoveryRun run = requireRun(id);
        if (run.getStatus() == VenueDiscoveryRunStatus.RUNNING) {
            run.setStatus(VenueDiscoveryRunStatus.FAILED);
            run.setCompletedAt(Instant.now());
            // Never persist an exception, response body, key, or Google display content.
            run.setFailureReason("Search failed. Check configuration or try again within the request limit.");
            runs.save(run);
        }
    }

    @Transactional(readOnly = true)
    public Page<Run> runs(int page, int size) {
        var rows = runs.findAll(PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new Page<>(rows.map(r -> runResponse(r, observations.countByDiscoveryRun_Id(r.getId()))).getContent(),
                page, size, rows.getTotalElements(), rows.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RunDetail run(Long id) {
        VenueDiscoveryRun run = requireRun(id);
        List<Candidate> result = observations.findByDiscoveryRun_IdOrderByObservedAtDesc(id).stream()
                .map(o -> candidateResponse(o.getCandidate())).toList();
        return new RunDetail(runResponse(run, result.size()), result);
    }

    @Transactional(readOnly = true)
    public Page<Candidate> candidates(VenueDiscoveryCandidateStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("discoveredAt"), Sort.Order.desc("id")));
        var rows = status == null ? candidates.findAll(pageable) : candidates.findByStatus(status, pageable);
        return new Page<>(rows.map(this::candidateResponse).getContent(), page, size,
                rows.getTotalElements(), rows.getTotalPages());
    }

    @Transactional(readOnly = true)
    public String placeId(Long id) {
        return requireCandidate(id).getSourcePlaceId();
    }

    public void checked(Long id) {
        jdbc.update("update venue_discovery_candidates set source_checked_at = now() where id = ?", id);
    }

    public Candidate review(Long id, VenueDiscoveryRequest.Review request, Actor actor) {
        VenueDiscoveryCandidate candidate = candidates.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Prospect not found"));
        VenueDiscoveryCandidateStatus old = candidate.getStatus();
        if (!REVIEWABLE.contains(old) || !REVIEWABLE.contains(request.status()) || candidate.getLinkedHall() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This prospect cannot be changed in the discovery review workflow");
        }
        if (old != request.expectedStatus()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prospect changed; refresh before reviewing");
        }
        if (old != request.status()) {
            candidate.setStatus(request.status());
            candidate.setStatusChangedAt(Instant.now());
            candidates.save(candidate);
            record(actor, AuditAction.VENUE_DISCOVERY_CANDIDATE_STATUS_CHANGED,
                    "VENUE_DISCOVERY_CANDIDATE", id, Map.of("status", old.name()),
                    Map.of("status", request.status().name()));
        }
        return candidateResponse(candidate);
    }

    private VenueDiscoveryCandidate requireCandidate(Long id) {
        return candidates.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Prospect not found"));
    }

    private VenueDiscoveryRun requireRun(Long id) {
        return runs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Search run not found"));
    }

    private Candidate candidateResponse(VenueDiscoveryCandidate c) {
        return new Candidate(c.getId(), c.getSourcePlaceId(), c.getStatus().name(), c.getDiscoveredAt(),
                c.getSourceCheckedAt(), c.getStatusChangedAt(), c.getLinkedHall() == null ? null : c.getLinkedHall().getId());
    }

    private Run runResponse(VenueDiscoveryRun r, long count) {
        return new Run(r.getId(), r.getCity(), r.getArea(), r.getRequestedPlaceTypes(), r.getStatus().name(),
                r.getCreatedAt(), r.getStartedAt(), r.getCompletedAt(), r.getFailureReason(), count);
    }

    private void record(Actor actor, AuditAction action, String type, Long id,
            Map<String, Object> oldValues, Map<String, Object> newValues) {
        audit.record(new AuditCommand(actor.userId(), actor.role(), action, type, id.toString(),
                "Venue discovery workflow updated", oldValues, newValues, Map.of()));
    }
}
