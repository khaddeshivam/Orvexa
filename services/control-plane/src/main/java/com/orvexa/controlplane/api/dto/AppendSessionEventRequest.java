package com.orvexa.controlplane.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.orvexa.controlplane.domain.model.EventActorType;
import com.orvexa.controlplane.domain.model.SessionEventType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AppendSessionEventRequest(
        @NotNull(message = "clientEventId is required")
        UUID clientEventId,
        @NotNull(message = "eventType is required")
        SessionEventType eventType,
        @NotNull(message = "actorType is required")
        EventActorType actorType,
        JsonNode payload
) {
}
