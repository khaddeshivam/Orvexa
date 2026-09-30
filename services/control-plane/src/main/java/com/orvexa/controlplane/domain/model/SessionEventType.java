package com.orvexa.controlplane.domain.model;

public enum SessionEventType {
    SESSION_CREATED,
    SESSION_STATUS_CHANGED,
    MESSAGE_CREATED,
    TOOL_EXECUTION,
    AI_RESPONSE,
    ESCALATION_REQUESTED,
    ERROR
}
