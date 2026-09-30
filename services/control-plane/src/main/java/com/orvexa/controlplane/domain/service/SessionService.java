package com.orvexa.controlplane.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orvexa.controlplane.api.dto.AppendSessionEventRequest;
import com.orvexa.controlplane.api.dto.CreateSessionRequest;
import com.orvexa.controlplane.domain.model.EventActorType;
import com.orvexa.controlplane.domain.model.SessionEntity;
import com.orvexa.controlplane.domain.model.SessionEventType;
import com.orvexa.controlplane.domain.model.SessionStatus;
import com.orvexa.controlplane.domain.repository.SessionEventRepository;
import com.orvexa.controlplane.domain.repository.SessionRepository;
import com.orvexa.controlplane.exception.InvalidStateTransitionException;
import com.orvexa.controlplane.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionEventRepository eventRepository;
    private final SessionEventService eventService;
    private final ObjectMapper objectMapper;

    public SessionService(
            SessionRepository sessionRepository,
            SessionEventRepository eventRepository,
            SessionEventService eventService,
            ObjectMapper objectMapper
    ) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SessionEntity create(UUID tenantId, UUID actorId, CreateSessionRequest request) {
        SessionEntity session = new SessionEntity(
                UUID.randomUUID(),
                tenantId,
                actorId,
                normalizeTitle(request.title())
        );
        sessionRepository.save(session);

        JsonNode payload = objectMapper.valueToTree(Map.of(
                "title", request.title() == null ? "" : request.title()
        ));

        eventService.appendInternal(
                session,
                new AppendSessionEventRequest(
                        UUID.randomUUID(),
                        SessionEventType.SESSION_CREATED,
                        EventActorType.SYSTEM,
                        payload
                )
        );
        return session;
    }

    @Transactional(readOnly = true)
    public SessionEntity get(UUID tenantId, UUID sessionId) {
        return sessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    @Transactional(readOnly = true)
    public Page<SessionEntity> list(UUID tenantId, SessionStatus status, Pageable pageable) {
        if (status == null) {
            return sessionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        return sessionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable);
    }

    @Transactional
    public SessionEntity transition(UUID tenantId, UUID sessionId, SessionStatus targetStatus) {
        SessionEntity session = sessionRepository.findByIdAndTenantIdForUpdate(sessionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (!session.getStatus().canTransitionTo(targetStatus)) {
            throw new InvalidStateTransitionException(
                    "Cannot transition session " + sessionId + " from "
                            + session.getStatus() + " to " + targetStatus
            );
        }

        if (session.getStatus() == targetStatus) {
            return session;
        }

        SessionStatus previousStatus = session.getStatus();
        session.transitionTo(targetStatus);
        sessionRepository.save(session);

        JsonNode payload = objectMapper.valueToTree(Map.of(
                "from", previousStatus.name(),
                "to", targetStatus.name()
        ));

        eventService.appendInternal(
                session,
                new AppendSessionEventRequest(
                        UUID.randomUUID(),
                        SessionEventType.SESSION_STATUS_CHANGED,
                        EventActorType.SYSTEM,
                        payload
                )
        );

        return session;
    }

    @Transactional(readOnly = true)
    public Page<com.orvexa.controlplane.domain.model.SessionEventEntity> events(
            UUID tenantId,
            UUID sessionId,
            Pageable pageable
    ) {
        get(tenantId, sessionId);
        return eventRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId, pageable);
    }

    @Transactional
    public com.orvexa.controlplane.domain.model.SessionEventEntity appendInternalEvent(
            UUID sessionId,
            AppendSessionEventRequest request
    ) {
        SessionEntity session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        return eventService.append(session, request);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
