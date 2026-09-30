package com.orvexa.controlplane.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum SessionStatus {
    ACTIVE,
    ESCALATED,
    CLOSED;

    public boolean canTransitionTo(SessionStatus target) {
        if (this == target) {
            return true;
        }

        Set<SessionStatus> allowedTargets = switch (this) {
            case ACTIVE -> EnumSet.of(ESCALATED, CLOSED);
            case ESCALATED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(SessionStatus.class);
        };

        return allowedTargets.contains(target);
    }
}
