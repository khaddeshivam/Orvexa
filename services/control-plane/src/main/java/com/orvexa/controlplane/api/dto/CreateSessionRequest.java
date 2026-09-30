package com.orvexa.controlplane.api.dto;

import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @Size(max = 120, message = "title must not exceed 120 characters")
        String title
) {
}
