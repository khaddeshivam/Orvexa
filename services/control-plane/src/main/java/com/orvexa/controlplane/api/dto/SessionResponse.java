package com.orvexa.controlplane.api.dto;

import com.orvexa.controlplane.domain.model.SessionEntity;
import com.orvexa.controlplane.domain.model.SessionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID tenantId,
        UUID createdBy,
        String title,
        SessionStatus status,
        long lastEventSequence,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SessionResponse from(SessionEntity session) {
        return new SessionResponse(
                session.getId(),
                session.getTenantId(),
                session.getCreatedBy(),
                session.getTitle(),
                session.getStatus(),
                session.getLastEventSequence(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
