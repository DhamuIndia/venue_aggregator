package com.staminal.venue.audit;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditEvent record(AuditCommand command) {
        AuditEvent event = new AuditEvent();
        event.setActorUserId(command.actorUserId());
        event.setActorRole(command.actorRole());
        event.setAction(command.action());
        event.setEntityType(command.entityType());
        event.setEntityId(command.entityId());
        event.setSummary(command.summary());
        event.setOldValues(toJson(command.oldValues()));
        event.setNewValues(toJson(command.newValues()));
        event.setMetadata(toJson(command.metadata()));

        CurrentRequest currentRequest = currentRequest();
        event.setRequestIp(currentRequest.ipAddress());
        event.setUserAgent(currentRequest.userAgent());

        return auditEventRepository.save(event);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit payload cannot be serialized", exception);
        }
    }

    private CurrentRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return new CurrentRequest(null, null);
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        return new CurrentRequest(firstForwardedIp(request), request.getHeader("User-Agent"));
    }

    private String firstForwardedIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record CurrentRequest(String ipAddress, String userAgent) {
    }

    private Map<String, Object> fromJson(String json) {

        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid audit json", ex);
        }
    }

    private AuditEventResponse mapToResponse(AuditEvent event) {

        AuditEventResponse response = new AuditEventResponse();

        response.setId(event.getId());
        response.setActorUserId(event.getActorUserId());
        response.setActorRole(event.getActorRole());
        response.setAction(event.getAction());

        response.setEntityType(event.getEntityType());
        response.setEntityId(event.getEntityId());

        response.setSummary(event.getSummary());

        response.setOldValues(fromJson(event.getOldValues()));
        response.setNewValues(fromJson(event.getNewValues()));
        response.setMetadata(fromJson(event.getMetadata()));

        response.setRequestIp(event.getRequestIp());
        response.setUserAgent(event.getUserAgent());

        response.setCreatedAt(event.getCreatedAt());

        return response;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getAuditEvents() {

        return auditEventRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::mapToResponse)
                .toList();
    }
}
