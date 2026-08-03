package com.staminal.venue.audit;

import java.util.Map;

public record AuditCommand(
        Long actorUserId,
        String actorRole,
        AuditAction action,
        String entityType,
        String entityId,
        String summary,
        Map<String, Object> oldValues,
        Map<String, Object> newValues,
        Map<String, Object> metadata) {
}
