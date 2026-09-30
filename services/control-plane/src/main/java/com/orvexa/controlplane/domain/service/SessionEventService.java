package com.orvexa.controlplane.domain.service;

import com.orvexa.controlplane.api.dto.AppendSessionEventRequest;
import com.orvexa.controlplane.domain.model.SessionEntity;
import com.orvexa.controlplane.domain.model.SessionEventEntity;
import com.orvexa.controlplane.domain.repository.SessionEventRepository;
import com.orvexa.controlplane.exception.IdempotencyConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SessionEventService {

    private final SessionEventRepository eventRepository;

    public SessionEventService(SessionEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public SessionEventEntity append(SessionEntity lockedSession, AppendSessionEventRequest request) {
        return appendInternal(lockedSession, request);
    }

    public SessionEventEntity appendInternal(SessionEntity lockedSession, AppendSessionEventRequest request) {
        return eventRepository.findBySessionIdAndClientEventId(lockedSession.getId(), request.clientEventId())
                .map(existing -> verifyIdempotentReplay(existing, request))
                .orElseGet(() -> {
                    long sequence = lockedSession.nextEventSequence();
                    SessionEventEntity event = new SessionEventEntity(
                            UUID.randomUUID(),
                            lockedSession.getId(),
                            request.clientEventId(),
                            sequence,
                            request.eventType(),
                            request.actorType(),
                            request.payload()
                    );
                    eventRepository.save(event);
                    return event;
                });
    }

    private SessionEventEntity verifyIdempotentReplay(
            SessionEventEntity existing,
            AppendSessionEventRequest request
    ) {
        boolean sameType = existing.getEventType() == request.eventType();
        boolean sameActor = existing.getActorType() == request.actorType();
        boolean samePayload = existing.getPayload() == null
                ? request.payload() == null
                : existing.getPayload().equals(request.payload());

        if (!sameType || !sameActor || !samePayload) {
            throw new IdempotencyConflictException(
                    "clientEventId has already been used with a different event payload"
            );
        }
        return existing;
    }
}
