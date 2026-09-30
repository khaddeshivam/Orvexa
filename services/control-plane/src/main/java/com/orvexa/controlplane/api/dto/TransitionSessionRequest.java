package com.orvexa.controlplane.api.dto;

import com.orvexa.controlplane.domain.model.SessionStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionSessionRequest(
        @NotNull(message = "status is required")
        SessionStatus status
) {
}
