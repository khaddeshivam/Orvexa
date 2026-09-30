package com.orvexa.controlplane.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.orvexa.controlplane.domain.model.EventActorType;
import com.orvexa.controlplane.domain.model.SessionEventEntity;
import com.orvexa.controlplane.domain.model.SessionEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionEventResponse(
        UUID id,
        UUID sessionId,
        UUID clientEventId,
        long sequenceNumber,
        SessionEventType eventType,
        EventActorType actorType,
        JsonNode payload,
        OffsetDateTime occurredAt
) {
    public static SessionEventResponse from(SessionEventEntity event) {
        return new SessionEventResponse(
                event.getId(),
                event.getSessionId(),
                event.getClientEventId(),
                event.getSequenceNumber(),
                event.getEventType(),
                event.getActorType(),
                event.getPayload(),
                event.getOccurredAt()
        );
    }
}
