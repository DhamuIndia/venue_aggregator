package com.staminal.venue.discovery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueCandidateAssignmentRepository
        extends JpaRepository<VenueCandidateAssignment, Long> {

    Optional<VenueCandidateAssignment> findByCandidate_IdAndUnassignedAtIsNull(Long candidateId);

    List<VenueCandidateAssignment> findByAssignedAdmin_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(
            long assignedAdminId);
}
