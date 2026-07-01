package com.staminal.venue.audit;

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
}
