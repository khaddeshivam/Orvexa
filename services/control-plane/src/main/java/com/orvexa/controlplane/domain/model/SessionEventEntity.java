package com.orvexa.controlplane.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "session_events")
public class SessionEventEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "client_event_id", nullable = false)
    private UUID clientEventId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private SessionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 24)
    private EventActorType actorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected SessionEventEntity() {
    }

    public SessionEventEntity(
            UUID id,
            UUID sessionId,
            UUID clientEventId,
            long sequenceNumber,
            SessionEventType eventType,
            EventActorType actorType,
            JsonNode payload
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.clientEventId = clientEventId;
        this.sequenceNumber = sequenceNumber;
        this.eventType = eventType;
        this.actorType = actorType;
        this.payload = payload;
        this.occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getClientEventId() {
        return clientEventId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public SessionEventType getEventType() {
        return eventType;
    }

    public EventActorType getActorType() {
        return actorType;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
